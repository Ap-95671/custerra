package com.nci.backend.operations;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.filter.OncePerRequestFilter;
import com.nci.backend.auth.SecurityErrors;

public class PublicRateLimit extends OncePerRequestFilter {
    private record Window(long minute,int count) {}
    private final Map<String,Window> peers=new HashMap<>();private final Clock clock;private final SecurityErrors errors;private final int limit;
    public PublicRateLimit(Clock clock,SecurityErrors errors,@Value("${PUBLIC_REQUESTS_PER_MINUTE:120}") int limit){if(limit<1||limit>10000)throw new IllegalArgumentException("Invalid public request limit");this.clock=clock;this.errors=errors;this.limit=limit;}
    public synchronized boolean acquire(String peer){
        long minute=clock.instant().getEpochSecond()/60;peers.entrySet().removeIf(e->e.getValue().minute()!=minute);
        var old=peers.get(peer);if(old==null&&peers.size()>=10000)return false;
        int count=old==null?0:old.count();if(count>=limit)return false;
        peers.put(peer,new Window(minute,count+1));return true;
    }
    @Override protected boolean shouldNotFilter(jakarta.servlet.http.HttpServletRequest req){String p=req.getRequestURI();return req.getMethod().equals("OPTIONS")||!(p.startsWith("/api/v1/b2b/")||p.startsWith("/api/v1/developer/")||p.startsWith("/api/v1/hazards")||p.startsWith("/api/v1/risk/")||p.startsWith("/api/v1/weather/")||p.startsWith("/api/v1/geocoding/")||p.startsWith("/api/v1/routes/"));}
    @Override protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest req,jakarta.servlet.http.HttpServletResponse res,jakarta.servlet.FilterChain chain) throws java.io.IOException,jakarta.servlet.ServletException {
        if(!acquire(req.getRemoteAddr())){res.setHeader("Retry-After","60");errors.write(req,res,429,"Request limit reached. Retry later.");return;}chain.doFilter(req,res);
    }
}
