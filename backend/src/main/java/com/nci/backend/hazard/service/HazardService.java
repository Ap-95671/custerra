package com.nci.backend.hazard.service;

import com.nci.backend.hazard.dto.HazardEventResponse;
import com.nci.backend.hazard.dto.HazardPageResponse;
import com.nci.backend.hazard.entity.HazardEvent;
import com.nci.backend.hazard.mapper.HazardMapper;
import com.nci.backend.hazard.model.HazardType;
import com.nci.backend.hazard.model.SeverityLevel;
import com.nci.backend.hazard.repository.HazardRepository;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class HazardService {
    private final HazardRepository repository;
    private final HazardMapper mapper;

    public HazardService(HazardRepository repository, HazardMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public HazardPageResponse findHazards(HazardType type, SeverityLevel severity, int page, int size) {
        Specification<HazardEvent> filters = (root, query, builder) -> builder.and(
                type == null ? builder.conjunction() : builder.equal(root.get("hazardType"), type),
                severity == null ? builder.conjunction() : builder.equal(root.get("severity"), severity));
        var result = repository.findAll(filters,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt", "id")));
        return new HazardPageResponse(result.getContent().stream().map(mapper::toResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    public HazardEventResponse findById(UUID id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
