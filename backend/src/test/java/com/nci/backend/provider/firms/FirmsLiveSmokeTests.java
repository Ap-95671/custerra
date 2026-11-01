package com.nci.backend.provider.firms;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import com.nci.backend.provider.model.*;
import static org.assertj.core.api.Assertions.*;

/** One bounded request only when the operator explicitly opts in and supplies an authorized key. */
@EnabledIfEnvironmentVariable(named="CUSTERRA_FIRMS_LIVE_AUTHORIZED",matches="true")
class FirmsLiveSmokeTests {
    @Test void authorizedAreaRequestParsesRealObservationsOrExplicitEmptyResult() {
        String key=System.getenv("FIRMS_MAP_KEY");assertThat(key!=null && !key.isBlank()).as("An authorized MAP_KEY must be supplied privately").isTrue();
        var now=Instant.now();var provider=new FirmsProvider(new FirmsProperties(key,3000,10000),new FirmsMapper(),Clock.systemUTC());
        var result=provider.fetch(new HazardQuery(0,0,1,now.minusSeconds(3600),now));
        assertThat(result.coverageStatus()).isIn(CoverageStatus.COMPLETE,CoverageStatus.PARTIAL,CoverageStatus.TRUNCATED);
        assertThat(result.events()).allSatisfy(e->{assertThat(e.source()).isEqualTo("FIRMS");assertThat(e.attributes()).containsEntry("instrument","VIIRS");assertThat(e.retrievedAt()).isNotNull();assertThat(e.startedAt()).isBeforeOrEqualTo(now);});
        System.out.println("FIRMS bounded live parse: observations="+result.events().size()+", coverage="+result.coverageStatus()+". Empty results do not verify nonempty live row parsing.");
    }
}
