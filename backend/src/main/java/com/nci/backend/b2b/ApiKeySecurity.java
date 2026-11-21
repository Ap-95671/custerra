package com.nci.backend.b2b;
import java.util.List;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;
import com.nci.backend.auth.SecurityErrors;
@Configuration
public class ApiKeySecurity {
 @Bean @Order(2) SecurityFilterChain business(HttpSecurity http,ApiClientService clients,SecurityErrors errors) throws Exception {
  var filter=new OncePerRequestFilter(){
   @Override protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest req,jakarta.servlet.http.HttpServletResponse res,jakarta.servlet.FilterChain chain)throws java.io.IOException,jakarta.servlet.ServletException {
    String header=req.getHeader("Authorization");String key=header!=null&&header.startsWith("Bearer ")?header.substring(7):null;
    String endpoint=switch(req.getRequestURI()){case "/api/v1/b2b/hazards/nearby"->"nearby";case "/api/v1/b2b/risk/location"->"location";case "/api/v1/b2b/risk/route"->"route";default->"unknown";};
    ApiClientService.Admission admission;
    try{admission=clients.admit(key,endpoint);}catch(ResponseStatusException failure){int status=failure.getStatusCode().value();if(status==429)res.setHeader("Retry-After",Long.toString(((ApiClientService.QuotaExceeded)failure).retrySeconds));errors.write(req,res,status,status==429?"API quota reached. Check account usage and retry after the quota window resets.":status==403?"API key lacks the required operation scope.":"Valid API credentials required.");return;}catch(org.springframework.dao.DataAccessException unavailable){errors.write(req,res,503,"API authorization is temporarily unavailable.");return;}
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(admission.clientId(),null,List.of(new SimpleGrantedAuthority("ROLE_API_CLIENT"))));
    res.setHeader("Cache-Control","no-store");res.setHeader("X-Request-ID",admission.requestId().toString());
    long started=System.nanoTime();int status=500;
    try{chain.doFilter(req,res);status=res.getStatus();}
    finally{try{clients.finish(admission,status,(System.nanoTime()-started)/1000000);}catch(org.springframework.dao.DataAccessException unavailable){org.slf4j.LoggerFactory.getLogger(ApiKeySecurity.class).error("API usage completion unavailable; reservation remains recorded.");}}
   }
  };
  return http.securityMatcher("/api/v1/b2b/**").csrf(c->c.disable()).requestCache(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .authorizeHttpRequests(r->r.anyRequest().hasRole("API_CLIENT"))
   .exceptionHandling(e->e.authenticationEntryPoint((req,res,x)->errors.write(req,res,401,"API credentials required.")))
   .addFilterBefore(filter,UsernamePasswordAuthenticationFilter.class).build();
 }
}
