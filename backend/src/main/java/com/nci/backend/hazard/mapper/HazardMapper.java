package com.nci.backend.hazard.mapper;

import com.nci.backend.hazard.dto.HazardEventResponse;
import com.nci.backend.hazard.entity.HazardEvent;
import org.springframework.stereotype.Component;

@Component
public class HazardMapper {
    public HazardEventResponse toResponse(HazardEvent event) {
        return new HazardEventResponse(event.getId(), event.getExternalId(), event.getHazardType(),
                event.getTitle(), event.getDescription(), event.getSeverity(), event.getInformationType(),
                event.getLatitude(), event.getLongitude(), event.getAffectedRadiusKm(),
                event.getStartedAt(), event.getEndedAt(), event.getSource(), event.getSourceUrl(),
                event.getObservedAt(), event.getConfidence());
    }
}
