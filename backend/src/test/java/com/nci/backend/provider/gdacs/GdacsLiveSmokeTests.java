package com.nci.backend.provider.gdacs;

import java.nio.file.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

/** Optional parse smoke against an independently downloaded, unmodified public feed. */
@EnabledIfEnvironmentVariable(named="CUSTERRA_GDACS_SMOKE_FILE", matches=".+")
class GdacsLiveSmokeTests {
    @Test void parsesCurrentDownloadedFeed() throws Exception {
        var provider = new GdacsProvider(new GdacsProperties(true,3000,10000),new GdacsMapper(),Clock.systemUTC());
        var result = provider.parse(Files.readAllBytes(Path.of(System.getenv("CUSTERRA_GDACS_SMOKE_FILE"))),Instant.now());
        assertThat(result.events()).isNotEmpty();
        assertThat(result.events()).allSatisfy(e -> {
            assertThat(e.source()).isEqualTo("GDACS");
            assertThat(e.sourceUrl()).startsWith("https://");
            assertThat(e.retrievedAt()).isNotNull();
        });
        System.out.println("GDACS live feed: normalized="+result.events().size()+", coverage="+result.coverageStatus()+", limitations="+result.limitations());
    }
}
