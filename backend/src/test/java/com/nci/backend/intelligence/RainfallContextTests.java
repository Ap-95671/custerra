package com.nci.backend.intelligence;

import com.nci.backend.provider.ProviderException;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RainfallContextTests {
    private static final LocalDate START = LocalDate.parse("2026-11-03");
    private static final Instant FETCHED = Instant.parse("2026-11-18T12:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void attributedUtcWindowDistinguishesZeroFromMissing() {
        var full = read("mm", "2026-11-09", "[3,0.5,0,0,1,0,0]");
        assertThat(full.totalRainMm()).isEqualTo(4.5);
        assertThat(full.incomplete()).isFalse();
        assertThat(full.days().get(2).rainMm()).isZero();
        assertThat(full.retrievedAt()).isEqualTo(FETCHED);
        assertThat(full.gridLatitude()).isEqualTo(37.75);
        assertThat(full.source()).contains("ERA5");

        var partial = read("mm", "2026-11-09", "[3,0.5,null,0,1,0,0]");
        assertThat(partial.incomplete()).isTrue();
        assertThat(partial.totalRainMm()).isNull();
        assertThat(partial.days().get(2).rainMm()).isNull();
    }

    @Test void invalidUnitsDatesAndMeasurementsFailClosed() {
        assertThatThrownBy(() -> read("inch", "2026-11-09", "[0,0,0,0,0,0,0]")).isInstanceOf(ProviderException.class);
        assertThatThrownBy(() -> read("mm", "2026-11-08", "[0,0,0,0,0,0,0]")).isInstanceOf(ProviderException.class);
        assertThatThrownBy(() -> read("mm", "2026-11-09", "[0,0,0,0,0,-1,0]")).isInstanceOf(ProviderException.class);
        assertThatThrownBy(() -> read("mm", "2026-11-09", "[0,0,0,0,0,0]")).isInstanceOf(ProviderException.class);
    }

    private RainfallContextController.Rainfall read(String unit, String end, String values) {
        String payload = """
                {"latitude":37.75,"longitude":-122.25,"daily_units":{"rain_sum":"%s"},"daily":{
                 "time":["2026-11-03","2026-11-04","2026-11-05","2026-11-06","2026-11-07","2026-11-08","%s"],
                 "rain_sum":%s}}
                """.formatted(unit, end, values);
        return RainfallContextController.parse(mapper.readTree(payload), START, FETCHED);
    }
}
