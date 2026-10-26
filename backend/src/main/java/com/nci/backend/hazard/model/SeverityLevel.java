package com.nci.backend.hazard.model;

/** Event severity is not a location risk score. Unknown must not imply low severity. */
public enum SeverityLevel {
    UNKNOWN, INFO, LOW, MODERATE, HIGH, CRITICAL
}
