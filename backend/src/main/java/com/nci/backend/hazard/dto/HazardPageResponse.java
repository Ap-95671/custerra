package com.nci.backend.hazard.dto;

import java.util.List;

public record HazardPageResponse(List<HazardEventResponse> items, int page, int size,
                                 long totalElements, int totalPages) {
    public HazardPageResponse {
        items = List.copyOf(items);
    }
}
