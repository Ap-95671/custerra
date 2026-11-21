package com.nci.backend.intelligence;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TerrainSlopeTests {
    @Test void modeledRampAndMissingSamples() {
        var json = new ObjectMapper();
        var estimate = TerrainSlope.estimate(json.readTree("[500,725,275,500,500]"));
        assertThat(estimate.degrees()).isCloseTo(45, within(1e-9));
        assertThat(estimate.offsetMeters()).isEqualTo(225);
        assertThat(TerrainSlope.estimate(json.readTree("[500,725,null,500,500]"))).isNull();
    }

    @Test void sampleWrapsLongitudeAndSkipsPolarSlope() {
        var sample = TerrainSlope.sample(0, 179.999, true);
        assertThat(sample.size()).isEqualTo(5);
        assertThat(sample.longitudes().split(",")[3]).startsWith("-179.");
        assertThat(TerrainSlope.sample(82, 20, true).size()).isEqualTo(1);
        assertThat(TerrainSlope.sample(30, 20, false).size()).isEqualTo(1);
    }
}
