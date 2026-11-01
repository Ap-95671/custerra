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
        if(!verifiedDatabaseTls(db)||password.isBlank()||!https||!secure.equals("true")||token.length()<32)
            throw new IllegalStateException("Production requires verified database TLS, credentials, HTTPS origins, secure cookies and an operations token of at least 32 characters.");
    }
    private static boolean verifiedDatabaseTls(String value) {
        try {
            if (!value.startsWith("jdbc:postgresql://")) return false;
            var uri = java.net.URI.create(value.substring(5));
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null || uri.getRawQuery() == null) return false;
            var parameters = new java.util.HashMap<String,String>();
            for (String parameter : uri.getRawQuery().split("&")) {
                var pair = parameter.split("=", 2);
                if (pair.length != 2) return false;
                String key = java.net.URLDecoder.decode(pair[0], java.nio.charset.StandardCharsets.UTF_8);
                String setting = java.net.URLDecoder.decode(pair[1], java.nio.charset.StandardCharsets.UTF_8);
                if (parameters.putIfAbsent(key, setting) != null) return false;
            }
            // Custom factories/verifiers can silently defeat sslmode's certificate verification.
            return "verify-full".equals(parameters.get("sslmode"))
                && !parameters.containsKey("sslhostnameverifier")
                && (!parameters.containsKey("sslfactory") || "org.postgresql.ssl.DefaultJavaSSLFactory".equals(parameters.get("sslfactory")));
        } catch (IllegalArgumentException invalid) { return false; }
    }
}
