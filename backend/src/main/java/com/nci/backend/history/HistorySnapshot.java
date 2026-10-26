package com.nci.backend.history;
import java.time.Instant;
import java.util.*;
import com.nci.backend.provider.model.*;
public record HistorySnapshot(UUID id, String modelVersion, HistoryQuery query, Instant fetchedAt,
        int eventCount, double exposureYears, Double annualFrequency, boolean truncated,
        List<NormalizedHazardEvent> events, ProviderMetadata source, String explanation) {
    public HistorySnapshot { events = List.copyOf(events); }
}
