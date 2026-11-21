package com.nci.backend.intelligence;
import java.time.*;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.nci.backend.history.HistoryQuery;
import com.nci.backend.history.HistoryService;
import com.nci.backend.provider.ProviderException;
import com.nci.backend.provider.model.ProviderMetadata;
import com.nci.backend.hazard.service.NearbyHazardService;
import com.nci.backend.hazard.dto.NearbyHazardResponse;
import com.nci.backend.hazard.model.HazardType;
import com.nci.backend.provider.ProviderHttp;
import tools.jackson.databind.ObjectMapper;
@RestController @RequestMapping("/api/v1/intelligence")
public class ComparisonController {
    public record Destination(@NotBlank @Size(max=80)String name,@NotNull @DecimalMin("-90")@DecimalMax("90")Double latitude,@NotNull @DecimalMin("-180")@DecimalMax("180")Double longitude){}
    public record HistoricalWindow(@NotNull Instant from, @NotNull Instant to,
                                   @NotNull @DecimalMin("0") @DecimalMax("10") Double minMagnitude) {
        HistoryQuery at(Destination destination, double radiusKm) {
            return new HistoryQuery(destination.latitude(), destination.longitude(), radiusKm, from, to, minMagnitude);
        }
    }
    public record Compare(@NotNull @Size(min=2,max=3)List<@NotNull @Valid Destination> destinations,@NotNull @Min(1)@Max(1000)Double radiusKm,@NotNull @Min(1)@Max(30)Integer days,@Valid HistoricalWindow history){}
    public record HistoricalEvidence(String coverageStatus, Integer eventCount, Double annualFrequency,
                                     Instant fetchedAt, ProviderMetadata source) {}
    public record Compared(Destination destination, NearbyHazardResponse earthquakes, IntelligenceService.Response intelligence,
                           String overallRisk, @JsonInclude(JsonInclude.Include.NON_NULL) HistoricalEvidence history) {}
    public record Comparison(List<Compared> destinations, Instant collectedAt, List<String> limitations,
                             @JsonInclude(JsonInclude.Include.NON_NULL) HistoricalWindow historicalWindow) {}
    private final NearbyHazardService nearby;private final IntelligenceService intelligence;private final HistoryService history;
    private final ObjectMapper json;private final Clock clock;
    private final RestClient terrainClient = ProviderHttp.client(3000, 10000);
    private Instant nextElevation=Instant.MIN;private final LinkedHashMap<String,Elevation> elevationCache=new LinkedHashMap<>();
    public ComparisonController(NearbyHazardService nearby,IntelligenceService intelligence,HistoryService history,ObjectMapper json,Clock clock){this.nearby=nearby;this.intelligence=intelligence;this.history=history;this.json=json;this.clock=clock;}
    @PostMapping("/compare")public Comparison compare(@Valid @RequestBody Compare request){
        var destinations = request.destinations();
        var historyQueries = request.history() == null ? Collections.<HistoryQuery>emptyList()
                : destinations.stream().map(destination -> request.history().at(destination, request.radiusKm())).toList();
        historyQueries.forEach(query -> query.validate(clock.instant()));
        var sources=intelligence.snapshots();var tasks=new ArrayList<Callable<Compared>>();
        for (int i = 0; i < destinations.size(); i++) {
            var destination = destinations.get(i);
            var historical = historyQueries.isEmpty() ? null : historyQueries.get(i);
            tasks.add(() -> new Compared(destination,
                    nearby.nearby(destination.latitude(), destination.longitude(), request.radiusKm(), request.days(), HazardType.EARTHQUAKE, "USGS"),
                    intelligence.nearby(sources, destination.latitude(), destination.longitude(), request.radiusKm(), request.days()),
                    "UNKNOWN", historical == null ? null : historicalEvidence(historical)));
        }
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
            var results=new ArrayList<Compared>();for(var future:executor.invokeAll(tasks,30,TimeUnit.SECONDS))results.add(future.get());
            var limitations = new ArrayList<>(List.of("Same filters, separately timestamped source queries; no universal safety ranking or prediction.",
                    "Counts describe retrieved evidence, not complete incident inventories. Unknown coverage cannot be compared as zero risk."));
            if (!historyQueries.isEmpty()) limitations.add("Historical USGS catalog coverage varies by region, era and magnitude. Truncated counts are lower bounds. Catalog frequency is historical context.");
            return new Comparison(results, clock.instant(), limitations, request.history());
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new com.nci.backend.provider.ProviderException(true);}catch(ExecutionException|CancellationException e){throw new com.nci.backend.provider.ProviderException(false);}
    }
    private HistoricalEvidence historicalEvidence(HistoryQuery query) {
        try {
            var snapshot = history.preview(query);
            return new HistoricalEvidence(snapshot.truncated() ? "TRUNCATED" : "RETRIEVED",
                    snapshot.eventCount(), snapshot.annualFrequency(), snapshot.fetchedAt(), snapshot.source());
        } catch (ProviderException unavailable) {
            return new HistoricalEvidence("UNAVAILABLE", null, null, null, null);
        }
    }
    public record Elevation(double latitude, double longitude, double elevationMeters, Instant retrievedAt, String source,
                            String sourceUrl, List<String> limitations, @JsonInclude(JsonInclude.Include.NON_NULL) TerrainSlope.Estimate slope) {}

    @GetMapping("/elevation")
    public synchronized Elevation elevation(@RequestParam @DecimalMin("-90") @DecimalMax("90") double latitude,
                                            @RequestParam @DecimalMin("-180") @DecimalMax("180") double longitude,
                                            @RequestParam(defaultValue = "false") boolean includeSlope) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) throw new IllegalArgumentException();
        var key = latitude + "," + longitude + "," + includeSlope;
        var now = clock.instant();
        var cached = elevationCache.get(key);
        if (cached != null && cached.retrievedAt().isAfter(now.minusSeconds(86400))) return cached;
        if (now.isBefore(nextElevation)) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        nextElevation = now.plusSeconds(1);

        var sample = TerrainSlope.sample(latitude, longitude, includeSlope);
        var uri = URI.create("https://api.open-meteo.com/v1/elevation?latitude=" + sample.latitudes() + "&longitude=" + sample.longitudes());
        var root = json.readTree(ProviderHttp.get(terrainClient, uri, "application/json", 65536));
        var values = root.path("elevation");
        if (!values.isArray() || values.size() != sample.size()) throw new ProviderException(false);
        var elevation = TerrainSlope.validElevation(values.get(0));
        if (elevation == null) throw new ProviderException(false);
        var slope = sample.size() == 5 ? TerrainSlope.estimate(values) : null;
        var limitations = new ArrayList<>(List.of("Modeled terrain context. Local surveying and hazard-specific susceptibility require different evidence.",
                "Copernicus GLO-90 has 90 m nominal resolution. Hosted non-commercial service terms apply."));
        if (includeSlope) limitations.add(slope == null ? "Slope unavailable at this sample or high latitude."
                : "Slope uses four modeled points 225 m from the center. It describes broad local relief, with no parcel-scale or landslide interpretation.");
        var result = new Elevation(latitude, longitude, elevation, clock.instant(), "Open-Meteo / Copernicus DEM",
                "https://open-meteo.com/en/docs/elevation-api", limitations, slope);
        elevationCache.remove(key);
        elevationCache.put(key, result);
        if (elevationCache.size() > 200) elevationCache.pollFirstEntry();
        return result;
    }
}
