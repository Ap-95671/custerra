package com.nci.backend.intelligence;

import java.time.Instant;
import java.util.*;
import tools.jackson.databind.JsonNode;

/** Source-issued geometry and classifications stay separate from experimental risk. */
public record IntelligenceEvent(String id,String source,String hazardType,String informationType,String title,
    String description,String severity,String urgency,String certainty,String language,String authority,
    String messageType,String status,Instant issuedAt,Instant effectiveAt,Instant expiresAt,Instant updatedAt,
    Instant retrievedAt,String areaDescription,String sourceUrl,Shape geometry,List<String> references,
    List<SourceLink> sourceLinks,List<Position> track,Map<String,String> attributes) {
    public IntelligenceEvent { references=List.copyOf(references);sourceLinks=List.copyOf(sourceLinks);track=List.copyOf(track);attributes=Map.copyOf(attributes); }
    public record Shape(String type,JsonNode coordinates) {}
    public record SourceLink(String id,String url) {}
    public record Position(Instant at,double latitude,double longitude) {}
    public String stateAt(Instant now) {
        if("EONET".equals(source))return status;
        if("Cancel".equalsIgnoreCase(messageType))return "CANCELLED";
        if(expiresAt!=null && !expiresAt.isAfter(now))return "EXPIRED";
        if(effectiveAt!=null && effectiveAt.isAfter(now))return "UPCOMING";
        return status;
    }
}
