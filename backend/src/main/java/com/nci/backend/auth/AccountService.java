package com.nci.backend.auth;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Service
public class AccountService {
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    public AccountService(AccountRepository accounts,PasswordEncoder passwords) { this.accounts=accounts;this.passwords=passwords; }
    public UUID register(String username,String password) {
        if(password.getBytes(StandardCharsets.UTF_8).length>72) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        try { return accounts.create(username,passwords.encode(password)); }
        catch(DuplicateKeyException duplicate) { throw new ResponseStatusException(HttpStatus.CONFLICT); }
    }
}
