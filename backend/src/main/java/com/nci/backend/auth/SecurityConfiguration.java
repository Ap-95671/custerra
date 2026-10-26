package com.nci.backend.auth;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
@Configuration
public class SecurityConfiguration {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean UserDetailsService users(AccountRepository accounts) {
        return username -> { var a=accounts.find(username).orElseThrow(()->new UsernameNotFoundException("Invalid credentials"));
            return User.withUsername(a.username()).password(a.passwordHash()).roles("USER").build(); };
    }
    @Bean SecurityFilterChain security(HttpSecurity http,SecurityErrors errors,Clock clock,com.nci.backend.alert.repository.PushSubscriptionRepository subscriptions,AccountRepository accounts) throws Exception {
        http.cors(Customizer.withDefaults()).csrf(Customizer.withDefaults())
            .requestCache(cache->cache.disable())
            .authorizeHttpRequests(r->r
                .requestMatchers("/api/v1/developer/**","/api/v1/auth/me","/api/v1/journeys","/api/v1/journeys/**","/api/v1/history/**","/api/v1/notifications","/api/v1/notifications/**").authenticated()
                .anyRequest().permitAll())
            .formLogin(form->form.loginProcessingUrl("/api/v1/auth/login")
                .successHandler((req,res,a)->{
                    var session=req.getSession(false);
                    // A fresh sign-in may switch accounts while reusing the browser session.
                    if(session!=null && session.getAttribute(com.nci.backend.alert.repository.PushSubscriptionRepository.SESSION_ATTRIBUTE) instanceof java.util.UUID id) {
                        subscriptions.expire(id);session.removeAttribute(com.nci.backend.alert.repository.PushSubscriptionRepository.SESSION_ATTRIBUTE);
                    }
                    res.setStatus(204);res.setHeader("Cache-Control","no-store");
                })
                .failureHandler((req,res,e)->errors.write(req,res,401,"Invalid username or password.")))
            .logout(logout->logout.addLogoutHandler((req,res,authentication)->{
                var session=req.getSession(false);
                if(authentication!=null && session!=null && session.getAttribute(com.nci.backend.alert.repository.PushSubscriptionRepository.SESSION_ATTRIBUTE) instanceof java.util.UUID id)
                    accounts.find(authentication.getName()).ifPresent(account->subscriptions.remove(account.id(),id));
            }).logoutUrl("/api/v1/auth/logout").deleteCookies("JSESSIONID")
                .logoutSuccessHandler((req,res,a)->res.setStatus(204)))
            .exceptionHandling(ex->ex.authenticationEntryPoint((req,res,e)->errors.write(req,res,401,"Sign in to access your saved data."))
                .accessDeniedHandler((req,res,e)->errors.write(req,res,403,"Request denied. Refresh your session before retrying.")))
            .addFilterBefore(new AuthenticationRateLimit(clock,errors),UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
