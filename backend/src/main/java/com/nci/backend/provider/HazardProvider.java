package com.nci.backend.provider;

import com.nci.backend.provider.model.HazardQuery;
import com.nci.backend.provider.model.ProviderResult;

public interface HazardProvider {
    String source();
    com.nci.backend.provider.model.ProviderMetadata metadata();
    default java.util.Set<com.nci.backend.hazard.model.HazardType> supportedHazards() {
        return java.util.Set.of(com.nci.backend.hazard.model.HazardType.EARTHQUAKE);
    }
    ProviderResult fetch(HazardQuery query);
}
