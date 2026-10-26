package com.nci.backend.alert.dto;
import java.util.*;
import jakarta.validation.constraints.*;
import com.nci.backend.hazard.model.*;
public record NotificationPreference(boolean enabled,boolean pushEnabled,@NotNull @Size(max=11) Set<@NotNull HazardType> hazardTypes,
        @NotNull SeverityLevel minimumSeverity,boolean includeUnknown) {
    public NotificationPreference { hazardTypes=hazardTypes==null?Set.of():Set.copyOf(hazardTypes); }
    public static NotificationPreference defaults() { return new NotificationPreference(true,false,EnumSet.allOf(HazardType.class),SeverityLevel.UNKNOWN,true); }
    public boolean accepts(HazardType hazard,SeverityLevel severity) {
        return enabled && hazardTypes.contains(hazard) && (severity==SeverityLevel.UNKNOWN ? includeUnknown : severity.ordinal()>=minimumSeverity.ordinal());
    }
}
