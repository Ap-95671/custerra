package com.nci.backend.operations;
import java.time.Clock;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class WorkerExecutionStatus {
    private final JdbcTemplate jdbc;private final Clock clock;
    public WorkerExecutionStatus(JdbcTemplate jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;}
    public java.time.Instant start(String job) {
        var now=clock.instant();
        jdbc.update("insert into worker_execution_status(job,last_started_at,outcome) values (?,?,'RUNNING') on conflict(job) do update set last_started_at=excluded.last_started_at,outcome='RUNNING'",job,java.sql.Timestamp.from(now));return now;
    }
    public void finish(String job,java.time.Instant started,String outcome) {
        // A slower earlier replica cannot overwrite the state of a more recent execution.
        jdbc.update("update worker_execution_status set last_completed_at=?,outcome=? where job=? and last_started_at=?",java.sql.Timestamp.from(clock.instant()),outcome,job,java.sql.Timestamp.from(started));
    }
    public List<Map<String,Object>> snapshot(){return jdbc.queryForList("select job,last_started_at,last_completed_at,outcome from worker_execution_status order by job");}
}
