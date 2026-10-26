package com.nci.backend.provider.gdacs;

import com.nci.backend.provider.*;
import com.nci.backend.provider.model.*;
import com.nci.backend.hazard.model.HazardType;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.*;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Element;
import org.xml.sax.helpers.DefaultHandler;

@Component
public class GdacsProvider implements HazardProvider {
    private final GdacsProperties properties;
    private final GdacsMapper mapper;
    private final Clock clock;
    private final RestClient client;
    @org.springframework.beans.factory.annotation.Autowired
    public GdacsProvider(GdacsProperties properties, GdacsMapper mapper, Clock clock) {
        this(properties, mapper, clock, ProviderHttp.client(properties.connectTimeoutMs(), properties.readTimeoutMs()));
    }
    GdacsProvider(GdacsProperties properties, GdacsMapper mapper, Clock clock, RestClient client) {
        this.properties = properties; this.mapper = mapper; this.clock = clock; this.client = client;
    }
    @Override public String source() { return "GDACS"; }
    @Override public Set<HazardType> supportedHazards() { return Set.copyOf(GdacsMapper.TYPES.values()); }
    @Override public ProviderMetadata metadata() {
        return new ProviderMetadata(source(), "Global Disaster Awareness and Coordination System, GDACS",
                "https://www.gdacs.org/feed_reference.aspx", "https://www.gdacs.org/About/termofuse.aspx",
                "On-demand recent-event RSS feed; source publishes approximately every six minutes.");
    }
    @Override public ProviderResult fetch(HazardQuery query) {
        if (!properties.enabled()) return new ProviderResult(List.of(), false, CoverageStatus.UNAVAILABLE, List.of("PROVIDER_DISABLED"));
        var bytes = ProviderHttp.get(client, URI.create("https://www.gdacs.org/xml/rss.xml"), "application/rss+xml, application/xml", 4_000_000);
        return parse(bytes, clock.instant());
    }
    ProviderResult parse(byte[] bytes, Instant retrievedAt) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder(); builder.setErrorHandler(new DefaultHandler());
            var doc = builder.parse(new ByteArrayInputStream(bytes));
            if (!"rss".equals(doc.getDocumentElement().getTagName()) || doc.getElementsByTagName("channel").getLength() != 1)
                throw new IllegalArgumentException("Unexpected feed schema");
            var items = doc.getElementsByTagName("item");
            var events = new LinkedHashMap<String, NormalizedHazardEvent>();
            int rejected = 0;
            for (int i = 0; i < Math.min(items.getLength(), 1000); i++) {
                try {
                    var event = mapper.map((Element) items.item(i), retrievedAt);
                    events.merge(event.externalId(), event, (old, next) -> next.sourceUpdatedAt() != null
                            && (old.sourceUpdatedAt() == null || next.sourceUpdatedAt().isAfter(old.sourceUpdatedAt())) ? next : old);
                } catch (IllegalArgumentException | DateTimeException e) { rejected++; }
            }
            var notes = new ArrayList<String>(List.of("RECENT_FEED_ONLY: This is not a complete catalog for the requested time window.",
                    "MAJOR_REPORTED_EVENTS_ONLY: advisories describe potential humanitarian impact, not local emergency orders.",
                    "POINT_PROXIMITY_ONLY: Source map extents do not establish affected-area intersection."));
            if (rejected > 0) notes.add("REJECTED_OR_UNSUPPORTED_ITEMS: " + rejected);
            var published = GdacsMapper.text((Element) doc.getElementsByTagName("channel").item(0), null, "pubDate");
            if (!published.isBlank() && GdacsMapper.date(published).isBefore(retrievedAt.minus(Duration.ofHours(2))))
                notes.add("STALE_FEED: publication timestamp is more than two hours old.");
            boolean truncated = items.getLength() > 1000;
            if (truncated) notes.add("FEED_LIMIT: only the first 1000 source records were examined.");
            return new ProviderResult(List.copyOf(events.values()), truncated, CoverageStatus.PARTIAL, notes);
        } catch (Exception e) { throw new ProviderException(false); }
    }
}
