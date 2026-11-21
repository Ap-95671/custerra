package com.nci.backend.intelligence;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
@Service
public class SavedLocationService {
    public record Change(String source,String eventId,String type){}
    public record Check(UUID id,UUID locationId,Instant checkedAt,IntelligenceService.Response evidence,List<Change> changes){}
    private final SavedLocationRepository repository;private final IntelligenceService intelligence;private final Clock clock;
    public SavedLocationService(SavedLocationRepository repository,IntelligenceService intelligence,Clock clock){this.repository=repository;this.intelligence=intelligence;this.clock=clock;}
    public Check check(UUID owner,UUID id){var location=repository.get(owner,id);var response=intelligence.nearby(location.latitude(),location.longitude(),location.radiusKm(),location.days());return repository.record(owner,id,response,clock.instant());}
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString="${SAVED_LOCATION_INTERVAL_MS:600000}")
    public void monitor(){if(!Boolean.parseBoolean(System.getenv().getOrDefault("SAVED_LOCATION_MONITORING_ENABLED","false")))return;for(var due:repository.due(clock.instant().minusSeconds(600)))try{check(due.owner(),due.id());}catch(RuntimeException ignored){/* Failure cannot erase evidence or expose locations/secrets. Next bounded cycle may retry. */}}
}
