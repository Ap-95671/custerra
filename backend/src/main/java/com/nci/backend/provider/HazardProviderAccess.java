package com.nci.backend.provider;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;
import com.nci.backend.provider.model.*;

/** Request-triggered only. No background polling, stale fallback, or automatic retry storm. */
@Component
public class HazardProviderAccess {
    private static class State {
        final Semaphore slots;
        int failures;Instant retryAt=Instant.MIN;
        State(int permits){slots=new Semaphore(permits);}
    }
    private record Key(String source,HazardQuery query) {}
    private record Cached(ProviderResult result,Instant expiresAt) {}
    private final Map<String,State> states=new ConcurrentHashMap<>();
    private final LinkedHashMap<Key,Cached> cache=new LinkedHashMap<>();
    private final Clock clock;
    public HazardProviderAccess(Clock clock){this.clock=clock;}
    private synchronized ProviderResult cached(Key key) {
        cache.values().removeIf(value->!value.expiresAt().isAfter(clock.instant()));
        var cached=cache.get(key);
        if(cached==null)return null;
        var result=cached.result();var notes=new ArrayList<>(result.limitations());
        notes.add("CACHED_SNAPSHOT: retrieved "+result.retrievedAt()+"; no new upstream retrieval occurred.");
        return new ProviderResult(result.events(),result.truncated(),result.coverageStatus(),notes,result.retrievedAt());
    }
    private synchronized void remember(Key key,ProviderResult result) {
        cache.values().removeIf(value->!value.expiresAt().isAfter(clock.instant()));
        if(result.events().size()>2000)return;
        while(!cache.isEmpty() && (cache.size()>=32 || cache.values().stream().mapToInt(c->c.result().events().size()).sum()+result.events().size()>5000))
            cache.remove(cache.keySet().iterator().next());
        cache.put(key,new Cached(result,clock.instant().plusSeconds(key.source().equals("GDACS")?60:15)));
    }
    public ProviderResult fetch(HazardProvider provider,HazardQuery query) {
        // Only GDACS returns a global feed independent of query. Other keys keep exact window/geometry.
        var key=new Key(provider.source(),provider.source().equals("GDACS")?null:query);
        var state=states.computeIfAbsent(provider.source(),source->new State(source.equals("USGS")?2:1));
        synchronized(state){if(state.retryAt.isAfter(clock.instant()))return unavailable("PROVIDER_BACKOFF: retry after "+state.retryAt);}
        var cached=cached(key);if(cached!=null)return cached;
        if(!state.slots.tryAcquire())return unavailable("PROVIDER_BUSY: concurrency budget reached; retry later.");
        try {
            cached=cached(key);if(cached!=null)return cached;
            var result=provider.fetch(query);
            if(result.coverageStatus()==CoverageStatus.UNAVAILABLE)return result;
            result=new ProviderResult(result.events(),result.truncated(),result.coverageStatus(),result.limitations(),clock.instant());
            synchronized(state){state.failures=0;state.retryAt=Instant.MIN;}
            remember(key,result);return result;
        } catch(RuntimeException failure) {
            synchronized(state){state.failures=Math.min(5,state.failures+1);state.retryAt=clock.instant().plusSeconds(Math.min(60,5L<<(state.failures-1)));}
            throw failure;
        } finally {state.slots.release();}
    }
    private ProviderResult unavailable(String note){return new ProviderResult(List.of(),false,CoverageStatus.UNAVAILABLE,List.of(note),null);}
}
