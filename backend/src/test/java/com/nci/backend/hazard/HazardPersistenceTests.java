package com.nci.backend.hazard;

import com.nci.backend.hazard.model.*;
import com.nci.backend.hazard.repository.HazardRepository;
import com.nci.backend.hazard.service.HazardService;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "CUSTERRA_DATABASE_TESTS", matches = "true")
class HazardPersistenceTests {
    @Autowired HazardRepository repository;
    @Autowired HazardService service;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;

    @DynamicPropertySource
    static void testDatabase(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> required("TEST_DB_URL"));
        properties.add("spring.datasource.username", () -> required("TEST_DB_USERNAME"));
        properties.add("spring.datasource.password", () -> required("TEST_DB_PASSWORD"));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must identify a dedicated test database");
        }
        return value;
    }

    @Test
    void migrationValidatesAndDoesNotRunTwice() {
        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where version = '1' and success",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void eventRoundTripsThroughPostgresAndDtoWithoutLosingProvenance() {
        var event = repository.saveAndFlush(HazardFixtures.event("round-trip", HazardType.WILDFIRE,
                SeverityLevel.UNKNOWN, HazardFixtures.START));
        entityManager.clear();
        var stored = repository.findById(event.getId()).orElseThrow();
        assertThat(stored).usingRecursiveComparison().isEqualTo(event);
        var response = service.findById(event.getId());
        assertThat(response.id()).isEqualTo(event.getId());
        assertThat(response.hazardType()).isEqualTo(HazardType.WILDFIRE);
        assertThat(response.confidence()).isEqualTo(ConfidenceLevel.UNKNOWN);
        assertThat(response.affectedRadiusKm()).isNull();
        assertThat(response.sourceUrl()).isEqualTo("https://example.test/events/round-trip");
    }

    @Test
    void advisoryMigrationPreservesExistingValuesAndAcceptsNewClassification() {
        var event = repository.saveAndFlush(HazardFixtures.event("advisory-migration", HazardType.FLOOD,
                SeverityLevel.MODERATE, HazardFixtures.START));
        jdbc.update("update hazard_event set information_type = 'ADVISORY' where id = ?", event.getId());
        entityManager.clear();
        assertThat(service.findById(event.getId()).informationType()).isEqualTo(InformationType.ADVISORY);
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where version = '3' and success", Integer.class)).isEqualTo(1);
    }

    @Test
    void filtersCombineAndPagesUseStableNewestFirstOrder() {
        repository.saveAndFlush(HazardFixtures.event("old", HazardType.EARTHQUAKE, SeverityLevel.HIGH,
                HazardFixtures.START));
        var newer = repository.saveAndFlush(HazardFixtures.event("new", HazardType.EARTHQUAKE, SeverityLevel.HIGH,
                HazardFixtures.START.plusSeconds(120)));
        repository.saveAndFlush(HazardFixtures.event("flood", HazardType.FLOOD, SeverityLevel.HIGH,
                HazardFixtures.START.plusSeconds(60)));
        repository.saveAndFlush(HazardFixtures.event("low", HazardType.EARTHQUAKE, SeverityLevel.LOW,
                HazardFixtures.START.plusSeconds(180)));
        var first = service.findHazards(HazardType.EARTHQUAKE, SeverityLevel.HIGH, 0, 1);
        assertThat(first.totalElements()).isEqualTo(2);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.items()).extracting("id").containsExactly(newer.getId());
        assertThat(service.findHazards(HazardType.EARTHQUAKE, SeverityLevel.HIGH, 1, 1).items())
                .extracting("externalId").containsExactly("old");
        assertThat(service.findHazards(null, SeverityLevel.HIGH, 0, 20).totalElements()).isEqualTo(3);
        assertThat(service.findHazards(HazardType.FLOOD, null, 0, 20).totalElements()).isEqualTo(1);
        assertThat(service.findHazards(null, null, 0, 20).totalElements()).isEqualTo(4);
        assertThat(service.findHazards(HazardType.TSUNAMI, null, 0, 20).items()).isEmpty();
    }

    @Test
    void equalTimestampsDoNotDuplicateEventsAcrossPages() {
        var first = repository.saveAndFlush(HazardFixtures.event("tie-1", HazardType.FLOOD,
                SeverityLevel.UNKNOWN, HazardFixtures.START));
        var second = repository.saveAndFlush(HazardFixtures.event("tie-2", HazardType.FLOOD,
                SeverityLevel.UNKNOWN, HazardFixtures.START));
        var page0 = service.findHazards(null, null, 0, 1).items().getFirst().id();
        var page1 = service.findHazards(null, null, 1, 1).items().getFirst().id();
        assertThat(java.util.List.of(page0, page1)).containsExactlyInAnyOrder(first.getId(), second.getId());
        assertThat(service.findHazards(null, null, 0, 1).items().getFirst().id()).isEqualTo(page0);
    }

    @Test
    void duplicateProviderIdentityIsRejectedByDatabase() {
        repository.saveAndFlush(HazardFixtures.event("duplicate", HazardType.EARTHQUAKE,
                SeverityLevel.UNKNOWN, HazardFixtures.START));
        assertThatThrownBy(() -> repository.saveAndFlush(HazardFixtures.event("duplicate", HazardType.FLOOD,
                SeverityLevel.HIGH, HazardFixtures.START)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsInvalidCoordinatesEvenWithoutBeanValidation() {
        var event = repository.saveAndFlush(HazardFixtures.event("db-check", HazardType.EARTHQUAKE,
                SeverityLevel.UNKNOWN, HazardFixtures.START));
        assertThatThrownBy(() -> jdbc.update("update hazard_event set latitude = 91 where id = ?", event.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
