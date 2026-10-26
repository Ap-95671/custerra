package com.nci.backend.provider.gdacs;

import com.nci.backend.hazard.model.*;
import com.nci.backend.provider.*;
import com.nci.backend.provider.model.*;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GdacsProviderTests {
    static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");
    static final String ITEM = """
        <item><title>Test-only flood advisory</title><link>https://www.gdacs.org/report.aspx?eventtype=FL&amp;eventid=123</link>
        <gdacs:eventtype>FL</gdacs:eventtype><gdacs:eventid>123</gdacs:eventid><gdacs:episodeid>2</gdacs:episodeid>
        <gdacs:alertlevel>Orange</gdacs:alertlevel><gdacs:alertscore>2</gdacs:alertscore>
        <gdacs:fromdate>Thu, 08 Oct 2026 00:00:00 GMT</gdacs:fromdate><gdacs:todate>Fri, 09 Oct 2026 00:00:00 GMT</gdacs:todate>
        <gdacs:datemodified>Fri, 09 Oct 2026 01:00:00 GMT</gdacs:datemodified>
        <gdacs:country>Test region</gdacs:country><gdacs:bbox>73 81 28 36</gdacs:bbox>
        <geo:Point><geo:lat>32.24</geo:lat><geo:long>77.19</geo:long></geo:Point></item>
        """;
    static String feed(String items) { return "<rss xmlns:gdacs=\"http://www.gdacs.org\" xmlns:geo=\"http://www.w3.org/2003/01/geo/wgs84_pos#\"><channel><pubDate>Fri, 09 Oct 2026 11:59:00 GMT</pubDate>"+items+"</channel></rss>"; }
    final Clock clock = Clock.fixed(NOW,ZoneOffset.UTC);
    final HazardQuery query = new HazardQuery(32.24,77.19,100,NOW.minus(Duration.ofDays(7)),NOW);
    MockRestServiceServer server; GdacsProvider provider;
    @BeforeEach void setup() {
        var builder = RestClient.builder(); server = MockRestServiceServer.bindTo(builder).build();
        provider = new GdacsProvider(new GdacsProperties(true,1000,1000),new GdacsMapper(),clock,builder.build());
    }
    @Test void normalizesAdvisoryAndPreservesSourceClassificationAndFreshness() {
        server.expect(anything()).andRespond(withSuccess(feed(ITEM),MediaType.APPLICATION_XML));
        var result = provider.fetch(query); var e = result.events().getFirst();
        assertThat(e.externalId()).isEqualTo("FL:123"); assertThat(e.hazardType()).isEqualTo(HazardType.FLOOD);
        assertThat(e.informationType()).isEqualTo(InformationType.ADVISORY); assertThat(e.severity()).isEqualTo(SeverityLevel.MODERATE);
        assertThat(e.confidence()).isEqualTo(ConfidenceLevel.UNKNOWN);
        assertThat(e.attributes()).containsEntry("alertlevel","Orange").containsEntry("bbox","73 81 28 36").containsEntry("episodeid","2");
        assertThat(e.affectedRadiusKm()).isNull(); assertThat(e.retrievedAt()).isEqualTo(NOW);
        assertThat(e.sourceUpdatedAt()).isEqualTo(Instant.parse("2026-10-09T01:00:00Z"));
        assertThat(result.coverageStatus()).isEqualTo(CoverageStatus.PARTIAL);
    }
    @Test void supportsSixFamiliesAndKeepsLatestRevisionRegardlessOfFeedOrder() {
        var items = new StringBuilder();
        for (String type: List.of("EQ","TC","FL","VO","TS","WF")) items.append(ITEM.replace(">FL<",">"+type+"<"));
        items.append(ITEM.replace("01:00:00", "02:00:00").replace("Orange","Red")); items.append(ITEM);
        server.expect(anything()).andRespond(withSuccess(feed(items.toString()),MediaType.APPLICATION_XML));
        var events = provider.fetch(query).events(); assertThat(events).hasSize(6);
        assertThat(events.stream().filter(e->e.externalId().equals("FL:123")).findFirst().orElseThrow().severity()).isEqualTo(SeverityLevel.HIGH);
    }
    @Test void malformedRowsAndUnsupportedTypesRemainVisibleAsIncomplete() {
        server.expect(anything()).andRespond(withSuccess(feed(ITEM+ITEM.replace("32.24","NaN")+ITEM.replace(">FL<",">DR<")),MediaType.APPLICATION_XML));
        var result = provider.fetch(query); assertThat(result.events()).hasSize(1);
        assertThat(result.limitations()).contains("REJECTED_OR_UNSUPPORTED_ITEMS: 2");
    }
    @Test void emptyFeedIsPartialAndDisabledSourceIsUnavailable() {
        server.expect(anything()).andRespond(withSuccess(feed(""),MediaType.APPLICATION_XML));
        var empty = provider.fetch(query); assertThat(empty.events()).isEmpty(); assertThat(empty.coverageStatus()).isEqualTo(CoverageStatus.PARTIAL);
        assertThat(new GdacsProvider(new GdacsProperties(false,1000,1000),new GdacsMapper(),clock).fetch(query).coverageStatus()).isEqualTo(CoverageStatus.UNAVAILABLE);
    }
    @Test void unsafeXmlMalformedJsonAndOversizeAreRejected() {
        for(String body: List.of("{}","<rss>","<html/>","<!DOCTYPE rss [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]>"+feed(ITEM),"x".repeat(4_000_001))) {
            server.reset(); server.expect(anything()).andRespond(withSuccess(body,MediaType.APPLICATION_XML));
            assertThatThrownBy(()->provider.fetch(query)).isInstanceOf(ProviderException.class);
        }
    }
    @Test void handlesHttpErrorsAndTimeoutWithoutUpstreamDetails() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("private"));
        server.expect(anything()).andRespond(withException(new SocketTimeoutException("private")));
        assertThatThrownBy(()->provider.fetch(query)).isInstanceOfSatisfying(ProviderException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(502));
        assertThatThrownBy(()->provider.fetch(query)).isInstanceOfSatisfying(ProviderException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(504));
    }
    @Test void truncationAndStaleFeedAreExplicit() {
        var result = provider.parse(feed(String.join("",Collections.nCopies(1001,ITEM))).replace("11:59:00","00:00:00").getBytes(StandardCharsets.UTF_8),NOW);
        assertThat(result.truncated()).isTrue(); assertThat(result.limitations()).anyMatch(s->s.startsWith("STALE_FEED"));
    }
    @Test void evidenceLinkCannotBeUntrustedOrPromotedToWarning() {
        server.expect(anything()).andRespond(withSuccess(feed(ITEM.replace("https://www.gdacs.org/report", "https://untrusted.test/report")),MediaType.APPLICATION_XML));
        assertThat(provider.fetch(query).events()).isEmpty();
    }
}
