package com.nci.backend.route.provider;

import java.time.Instant;
import java.util.List;
import com.nci.backend.provider.model.ProviderMetadata;
import com.nci.backend.route.dto.RoutePoint;

public interface GeocodingProvider {
    Places search(String query);
    record Place(String id, String name, String region, String country, RoutePoint location) {}
    record Places(List<Place> candidates, ProviderMetadata source, Instant retrievedAt) {
        public Places { candidates = List.copyOf(candidates); }
    }
}
