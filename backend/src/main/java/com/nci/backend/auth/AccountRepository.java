package com.nci.backend.auth;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class AccountRepository {
    private final JdbcTemplate jdbc;
    public AccountRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public record Account(UUID id,String username,String passwordHash) {
        @Override public String toString() { return "Account[id="+id+"]"; }
    }
    public Optional<Account> find(String username) {
        return jdbc.query("select id,username,password_hash from user_account where username=?",
                (rs,row)->new Account(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3)),username.toLowerCase(Locale.ROOT)).stream().findFirst();
    }
    public UUID create(String username,String hash) {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into user_account(id,username,password_hash) values (?,?,?)",id,username.toLowerCase(Locale.ROOT),hash);
        return id;
    }
}
