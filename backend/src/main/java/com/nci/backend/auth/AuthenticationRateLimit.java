package com.nci.backend.auth;
import java.io.IOException;
import java.time.Clock;
import java.util.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
/** Bounded per-process throttle; uses transport peer address, never untrusted forwarding headers. */
public class AuthenticationRateLimit extends OncePerRequestFilter {
    private record Window(long expires,int attempts) {}
    private final Map<String,Window> windows=new HashMap<>();
    private final Clock clock;
    private final SecurityErrors errors;
    public AuthenticationRateLimit(Clock clock,SecurityErrors errors) { this.clock=clock;this.errors=errors; }
    private synchronized boolean allowed(String address) {
        long now=clock.millis(); windows.values().removeIf(w->w.expires()<=now);
        var old=windows.get(address);
        if(old==null && windows.size()>=10000) return false;
        if(old!=null && old.attempts()>=20) return false;
        windows.put(address,new Window(old==null?now+600000:old.expires(),old==null?1:old.attempts()+1)); return true;
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String path=request.getRequestURI().substring(request.getContextPath().length());
        boolean limited=request.getMethod().equals("POST") && Set.of("/api/v1/auth/login","/api/v1/auth/register").contains(path);
        if(limited && !allowed(request.getRemoteAddr())) { response.setHeader("Retry-After","600"); errors.write(request,response,429,"Too many authentication attempts. Try again later."); return; }
        // Avoid encoder-specific overlong-password behavior in form login.
        if(path.equals("/api/v1/auth/login") && request.getMethod().equals("POST")) {
            var password=request.getParameter("password");
            if(password==null || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72) { errors.write(request,response,401,"Invalid username or password."); return; }
        }
        chain.doFilter(request,response);
    }
}
