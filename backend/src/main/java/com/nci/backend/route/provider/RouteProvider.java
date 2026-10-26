package com.nci.backend.route.provider;

import java.time.Instant;
import java.util.List;
import com.nci.backend.route.dto.RoutePoint;
import com.nci.backend.provider.model.ProviderMetadata;

public interface RouteProvider {
    RoutePlan route(RoutePoint origin, RoutePoint destination, TravelMode mode);
    enum TravelMode { DRIVING }
    /** Full provider geometry stays encoded at API boundaries; decoded points are transient assessment inputs. */
    record RoutePlan(String geometry, int precision, List<RoutePoint> points, double distanceKm,
                     double durationSeconds, ProviderMetadata source, Instant retrievedAt) {
        public RoutePlan { points = List.copyOf(points); }
    }
}
