package com.nci.backend.operations;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @Transactional
@EnabledIfEnvironmentVariable(named="CUSTERRA_DATABASE_TESTS",matches="true")
class WorkerStatusPersistenceTests {
    @Autowired JdbcTemplate jdbc;
    @DynamicPropertySource static void db(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->System.getenv("TEST_DB_URL"));p.add("spring.datasource.username",()->System.getenv("TEST_DB_USERNAME"));p.add("spring.datasource.password",()->System.getenv("TEST_DB_PASSWORD"));}
    @Test void executionCheckpointSurvivesWorkerReconstructionAndEarlierRunCannotOverwriteIt() {
        var now=Instant.parse("2026-11-01T00:00:00Z");
        var first=new WorkerExecutionStatus(jdbc,Clock.fixed(now,ZoneOffset.UTC));var start=first.start("journeys");
        var restarted=new WorkerExecutionStatus(jdbc,Clock.fixed(now.plusSeconds(60),ZoneOffset.UTC));
        assertThat(restarted.snapshot()).anyMatch(row->row.get("outcome").equals("RUNNING"));
        var second=restarted.start("journeys");first.finish("journeys",start,"COMPLETED");
        assertThat(jdbc.queryForObject("select outcome from worker_execution_status where job='journeys'",String.class)).isEqualTo("RUNNING");
        restarted.finish("journeys",second,"DEGRADED");
        assertThat(first.snapshot()).anyMatch(row->row.get("outcome").equals("DEGRADED"));
    }
}
