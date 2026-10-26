package com.nci.backend.history;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
@Repository
public class HistoryRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final com.nci.backend.auth.CurrentAccount current;
    public HistoryRepository(JdbcTemplate jdbc, ObjectMapper json, com.nci.backend.auth.CurrentAccount current) { this.jdbc = jdbc; this.json = json; this.current=current; }
    public void save(HistorySnapshot snapshot) {
        jdbc.update("insert into history_snapshot(id, fetched_at, payload, owner_id) values (?, ?, ?, ?)",
                snapshot.id(), java.sql.Timestamp.from(snapshot.fetchedAt()), json.writeValueAsString(snapshot), current.id());
    }
    public Optional<HistorySnapshot> find(UUID id) {
        return jdbc.query("select payload from history_snapshot where id = ? and owner_id = ?",
                (rs, row) -> json.readValue(rs.getString(1), HistorySnapshot.class), id, current.id()).stream().findFirst();
    }
}
