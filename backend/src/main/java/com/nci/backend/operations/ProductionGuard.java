package com.nci.backend.operations;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;
@Component @Profile("production")
public class ProductionGuard {
    public ProductionGuard(Environment env) {
        validate(env.getProperty("spring.datasource.url",""),env.getProperty("spring.datasource.password",""),env.getProperty("custerra.cors.allowed-origins",""),env.getProperty("server.servlet.session.cookie.secure","false"),env.getProperty("OPS_TOKEN",""));
    }
    public static void validate(String db,String password,String origins,String secure,String token){
        boolean https=!origins.isBlank()&&java.util.Arrays.stream(origins.split(",")).allMatch(origin->{try{var uri=java.net.URI.create(origin.strip());return "https".equals(uri.getScheme())&&uri.getHost()!=null&&!origin.contains("*")&&uri.getUserInfo()==null;}catch(Exception e){return false;}});
        if(!db.startsWith("jdbc:postgresql:")||!db.contains("sslmode=verify-full")||password.isBlank()||!https||!secure.equals("true")||token.length()<32)
            throw new IllegalStateException("Production requires verified database TLS, credentials, HTTPS origins, secure cookies and an operations token of at least 32 characters.");
    }
}
