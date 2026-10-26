package com.nci.backend.operations;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import com.nci.backend.auth.SecurityErrors;
@Configuration
public class OperationsSecurity {
    @Bean @Order(1) SecurityFilterChain operations(HttpSecurity http,SecurityErrors errors,@Value("${OPS_TOKEN:}") String token) throws Exception {
        var filter=new OncePerRequestFilter(){
            @Override protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest req,jakarta.servlet.http.HttpServletResponse res,jakarta.servlet.FilterChain chain) throws java.io.IOException,jakarta.servlet.ServletException {
                String supplied=req.getHeader("Authorization");
                if(token.length()>=32 && supplied!=null && MessageDigest.isEqual(("Bearer "+token).getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)))
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("operations",null,List.of(new SimpleGrantedAuthority("ROLE_OPERATIONS"))));
                chain.doFilter(req,res);
            }
        };
        return http.securityMatcher("/actuator/**","/api/internal/**").csrf(c->c.disable()).requestCache(c->c.disable())
            .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(r->r.requestMatchers("/actuator/health","/actuator/health/liveness","/actuator/health/readiness").permitAll().anyRequest().hasRole("OPERATIONS"))
            .exceptionHandling(e->e.authenticationEntryPoint((req,res,x)->errors.write(req,res,401,"Operations credentials required.")).accessDeniedHandler((req,res,x)->errors.write(req,res,403,"Access denied.")))
            .addFilterBefore(filter,UsernamePasswordAuthenticationFilter.class).build();
    }
}
