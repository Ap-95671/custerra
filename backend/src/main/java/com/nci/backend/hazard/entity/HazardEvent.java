package com.nci.backend.hazard.entity;

import com.nci.backend.hazard.model.*;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "hazard_event", uniqueConstraints =
        @UniqueConstraint(name = "uk_hazard_source_external", columnNames = {"source", "external_id"}))
public class HazardEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank @Size(max = 200)
    @Column(name = "external_id", nullable = false, length = 200)
    private String externalId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "hazard_type", nullable = false, length = 40)
    private HazardType hazardType;

    @NotBlank @Size(max = 500)
    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Size(max = 10000)
    @Column(name = "description", length = 10000)
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private SeverityLevel severity;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "information_type", nullable = false, length = 40)
    private InformationType informationType;

    @DecimalMin("-90") @DecimalMax("90")
    @Column(name = "latitude", nullable = false)
    private double latitude;

    @DecimalMin("-180") @DecimalMax("180")
    @Column(name = "longitude", nullable = false)
    private double longitude;

    @DecimalMin("0") @DecimalMax("20040")
    @Column(name = "affected_radius_km")
    private Double affectedRadiusKm;

    @NotNull
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @NotBlank @Size(max = 100)
    @Column(name = "source", nullable = false, length = 100)
    private String source;

    @NotBlank @Size(max = 2000)
    @Pattern(regexp = "https?://[^\\s]+", message = "must be an HTTP or HTTPS source URL")
    @Column(name = "source_url", nullable = false, length = 2000)
    private String sourceUrl;

    @NotNull
    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "confidence", nullable = false, length = 20)
    private ConfidenceLevel confidence;

    protected HazardEvent() {}

    public HazardEvent(String externalId,
            HazardType hazardType,
            String title,
            String description,
            SeverityLevel severity,
            InformationType informationType,
            double latitude,
            double longitude,
            Double affectedRadiusKm,
            Instant startedAt,
            Instant endedAt,
            String source,
            String sourceUrl,
            Instant observedAt,
            ConfidenceLevel confidence) {
        this.externalId = externalId;
        this.hazardType = hazardType;
        this.title = title;
        this.description = description;
        this.severity = severity;
        this.informationType = informationType;
        this.latitude = latitude;
        this.longitude = longitude;
        this.affectedRadiusKm = affectedRadiusKm;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.source = source;
        this.sourceUrl = sourceUrl;
        this.observedAt = observedAt;
        this.confidence = confidence;
    }

    @AssertTrue(message = "endedAt must not precede startedAt")
    public boolean isTimeRangeValid() {
        return endedAt == null || startedAt == null || !endedAt.isBefore(startedAt);
    }

    public UUID getId() { return id; }
    public String getExternalId() { return externalId; }
    public HazardType getHazardType() { return hazardType; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public SeverityLevel getSeverity() { return severity; }
    public InformationType getInformationType() { return informationType; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public Double getAffectedRadiusKm() { return affectedRadiusKm; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public String getSource() { return source; }
    public String getSourceUrl() { return sourceUrl; }
    public Instant getObservedAt() { return observedAt; }
    public ConfidenceLevel getConfidence() { return confidence; }
}
