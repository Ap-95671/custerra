package com.nci.backend.intelligence;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="CUSTERRA_INTELLIGENCE_LIVE_AUTHORIZED",matches="true")
class IntelligenceLiveTests {
    void check(String source){var feed=new IntelligenceFeed(new ObjectMapper(),Clock.systemUTC(),new SpatialGeometry(),true,true);var result=feed.fetch(source);assertThat(result.coverageStatus()).isEqualTo("PARTIAL");System.out.println("Bounded live intelligence source="+source+" parsed="+result.items().size()+" retrieved="+result.retrievedAt()+" limits="+result.limitations());}
    @Test void boundedNws(){check("NWS");}
    @Test void boundedEonet(){check("EONET");}
}
