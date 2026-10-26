package com.nci.backend.alert.dto;
import java.time.Instant;
import java.util.*;
import com.nci.backend.monitoring.dto.JourneyNotice;
public record AlertResponse(UUID id,String journeyName,JourneyNotice notice,Instant readAt,List<Delivery> deliveries) {
    public record Delivery(String channel,String status,int attempts,String lastError) {}
}
