package com.nci.backend.provider.firms;

import com.nci.backend.hazard.model.*;
import com.nci.backend.provider.*;
import com.nci.backend.provider.model.*;
import java.net.SocketTimeoutException;
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

class FirmsProviderTests {
    static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");
    static final String HEADER = "latitude,longitude,bright_ti4,scan,track,acq_date,acq_time,satellite,instrument,confidence,version,bright_ti5,frp,daynight\r\n";
    static final String ROW = "32.24,77.19,330.2,0.4,0.4,2026-10-09,0631,N,VIIRS,n,2.0NRT,290.2,12.4,D\r\n";
    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    final HazardQuery query = new HazardQuery(32.24,77.19,100,NOW.minus(Duration.ofDays(7)),NOW);
    MockRestServiceServer server;
    FirmsProvider provider;
    @BeforeEach void setup() {
        var builder = RestClient.builder(); server = MockRestServiceServer.bindTo(builder).build();
        provider = new FirmsProvider(new FirmsProperties("test-only-key",1000,1000),new FirmsMapper(),clock,builder.build());
    }
    @Test void preservesObservationMetadataAndReportsShorterCoverage() {
        server.expect(request -> assertThat(request.getURI().getPath()).contains("/VIIRS_SNPP_NRT/", "/5/2026-10-05"))
                .andRespond(withSuccess(HEADER+ROW,MediaType.TEXT_PLAIN));
        var result = provider.fetch(query); var e = result.events().getFirst();
        assertThat(e.title()).isEqualTo("Satellite-detected thermal anomaly");
        assertThat(e.hazardType()).isEqualTo(HazardType.WILDFIRE);
        assertThat(e.informationType()).isEqualTo(InformationType.OBSERVED_EVENT);
        assertThat(e.severity()).isEqualTo(SeverityLevel.UNKNOWN);
        assertThat(e.confidence()).isEqualTo(ConfidenceLevel.MEDIUM);
        assertThat(e.attributes()).containsEntry("confidence","n").containsEntry("satellite","N").containsEntry("daynight","D");
        assertThat(e.measurements()).containsEntry("bright_ti4",330.2).containsEntry("frp",12.4);
        assertThat(e.observedAt()).isEqualTo(Instant.parse("2026-10-09T06:31:00Z"));
        assertThat(e.retrievedAt()).isEqualTo(NOW); assertThat(e.sourceUpdatedAt()).isNull();
        assertThat(e.sourceUrl()).doesNotContain("test-only-key");
        assertThat(result.coverageStatus()).isEqualTo(CoverageStatus.PARTIAL);
        server.verify();
    }
    @Test void absentKeyIsUnavailableWithoutNetwork() {
        var p = new FirmsProvider(new FirmsProperties("",1000,1000),new FirmsMapper(),clock);
        assertThat(p.fetch(query).coverageStatus()).isEqualTo(CoverageStatus.UNAVAILABLE);
        assertThat(new FirmsProperties("secret",1000,1000).toString()).doesNotContain("secret");
    }
    @Test void validEmptyCsvIsDistinctFromMalformedAndRejectedRowsArePartial() {
        server.expect(anything()).andRespond(withSuccess(HEADER,MediaType.TEXT_PLAIN));
        server.expect(anything()).andRespond(withSuccess(HEADER+ROW+ROW.replace("32.24", "91"),MediaType.TEXT_PLAIN));
        var shortQuery = new HazardQuery(32.24,77.19,100,NOW.minus(Duration.ofDays(1)),NOW);
        assertThat(provider.fetch(shortQuery).coverageStatus()).isEqualTo(CoverageStatus.COMPLETE);
        var partial = provider.fetch(shortQuery);
        assertThat(partial.events()).hasSize(1);
        assertThat(partial.coverageStatus()).isEqualTo(CoverageStatus.PARTIAL);
        assertThat(partial.limitations()).contains("REJECTED_ROWS: 1");
    }
    @Test void stableIdentityDeduplicatesRevisedMeasurementButNotOtherOverpasses() {
        server.expect(anything()).andRespond(withSuccess(HEADER+ROW+ROW.replace("12.4", "13.4")+ROW.replace("0631","0731"),MediaType.TEXT_PLAIN));
        var events = provider.fetch(query).events();
        assertThat(events).hasSize(2);
        assertThat(events.get(1).measurements()).containsEntry("frp",13.4);
    }
    @Test void rejectsMalformedCsvHttpErrorsAndTimeoutsWithoutLeakingKey() {
        for (String body : List.of("Invalid MAP_KEY", "{}", HEADER+'"'+ROW, "x".repeat(2_000_001))) {
            server.reset(); server.expect(anything()).andRespond(withSuccess(body,MediaType.TEXT_PLAIN));
            assertThatThrownBy(() -> provider.fetch(query)).isInstanceOf(ProviderException.class).hasMessageNotContaining("test-only-key");
        }
        server.reset(); server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(anything()).andRespond(withException(new SocketTimeoutException("test-only-key")));
        assertThatThrownBy(() -> provider.fetch(query)).isInstanceOfSatisfying(ProviderException.class,e -> assertThat(e.getStatusCode().value()).isEqualTo(502));
        assertThatThrownBy(() -> provider.fetch(query)).isInstanceOfSatisfying(ProviderException.class,e -> assertThat(e.getStatusCode().value()).isEqualTo(504));
    }
    @Test void absentConfidenceDoesNotBecomeCertaintyOrDiscardObservation() {
        server.expect(anything()).andRespond(withSuccess(HEADER+ROW.replace(",n,", ",,"), MediaType.TEXT_PLAIN));
        assertThat(provider.fetch(query).events().getFirst().confidence()).isEqualTo(ConfidenceLevel.UNKNOWN);
    }
    @Test void csvHandlesQuotedFieldsAndEscapes() {
        var row = FirmsCsv.rows(HEADER+ROW.replace("2.0NRT", "\"version, \"\"quoted\"\"\"" )).getFirst();
        assertThat(row.get("version")).isEqualTo("version, \"quoted\"");
    }
    @Test void datelineAndPoleBoundingBoxesDoNotOmitCircle() {
        assertThat(FirmsProvider.boxes(new HazardQuery(0,179.9,100,query.from(),NOW))).hasSize(2);
        assertThat(FirmsProvider.boxes(new HazardQuery(0,-179.9,100,query.from(),NOW))).hasSize(2);
        assertThat(FirmsProvider.boxes(new HazardQuery(89.9,0,100,query.from(),NOW)).getFirst()).startsWith("-180.0,").contains(",180.0,90.0");
    }
    @Test void boundedResponseTruncatesAfterExactSpatialFiltering() {
        var body = new StringBuilder(HEADER);
        for(int i=0;i<501;i++) body.append(ROW.replace("32.24",Double.toString(32.24+i*0.00001)));
        body.append(ROW.replace("77.19","0"));
        server.expect(anything()).andRespond(withSuccess(body.toString(),MediaType.TEXT_PLAIN));
        var result = provider.fetch(query);
        assertThat(result.events()).hasSize(500); assertThat(result.truncated()).isTrue();
    }
}
