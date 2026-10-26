package com.nci.backend.hazard;

import com.nci.backend.hazard.entity.HazardEvent;
import com.nci.backend.hazard.mapper.HazardMapper;
import com.nci.backend.hazard.model.*;
import com.nci.backend.hazard.repository.HazardRepository;
import com.nci.backend.hazard.service.HazardService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HazardServiceTests {
    private final HazardRepository repository = mock(HazardRepository.class);
    private final HazardService service = new HazardService(repository, new HazardMapper());

    @Test
    void mapsAllEvidenceFieldsWithoutInventingUnknownValues() {
        var event = HazardFixtures.event("test-1", HazardType.FLOOD, SeverityLevel.UNKNOWN, HazardFixtures.START);
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(event));
        var result = service.findById(id);
        assertThat(result.externalId()).isEqualTo("test-1");
        assertThat(result.title()).isEqualTo("Synthetic test fixture");
        assertThat(result.description()).isEqualTo("Test-only event");
        assertThat(result.latitude()).isEqualTo(32.24);
        assertThat(result.longitude()).isEqualTo(77.19);
        assertThat(result.startedAt()).isEqualTo(HazardFixtures.START);
        assertThat(result.sourceUrl()).isEqualTo("https://example.test/events/test-1");
        assertThat(result.confidence()).isEqualTo(ConfidenceLevel.UNKNOWN);
        assertThat(result.hazardType()).isEqualTo(HazardType.FLOOD);
        assertThat(result.severity()).isEqualTo(SeverityLevel.UNKNOWN);
        assertThat(result.affectedRadiusKm()).isNull();
        assertThat(result.endedAt()).isNull();
        assertThat(result.informationType()).isEqualTo(InformationType.OBSERVED_EVENT);
        assertThat(result.source()).isEqualTo("test-source");
        assertThat(result.observedAt()).isEqualTo(HazardFixtures.START.plusSeconds(60));
    }

    @Test
    void missingEventIsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(id)).isInstanceOfSatisfying(
                ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void usesBoundedPaginationStableSortAndExplicitMetadata() {
        var pageRequest = PageRequest.of(2, 5, Sort.by(Sort.Direction.DESC, "startedAt", "id"));
        when(repository.findAll(org.mockito.ArgumentMatchers.<Specification<HazardEvent>>any(), eq(pageRequest)))
                .thenReturn(new PageImpl<>(List.of(), pageRequest, 10));
        var result = service.findHazards(HazardType.EARTHQUAKE, SeverityLevel.HIGH, 2, 5);
        assertThat(result.items()).isEmpty();
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(5);
        assertThat(result.totalElements()).isEqualTo(10);
        assertThat(result.totalPages()).isEqualTo(2);
        verify(repository).findAll(org.mockito.ArgumentMatchers.<Specification<HazardEvent>>any(), eq(pageRequest));
    }
}
