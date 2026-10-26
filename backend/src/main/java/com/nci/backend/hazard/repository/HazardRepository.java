package com.nci.backend.hazard.repository;

import com.nci.backend.hazard.entity.HazardEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface HazardRepository extends JpaRepository<HazardEvent, UUID>,
        JpaSpecificationExecutor<HazardEvent> {}
