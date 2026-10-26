package com.nci.backend.operations;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import io.micrometer.core.instrument.MeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;
import com.nci.backend.provider.*;
import com.nci.backend.provider.model.*;
import com.nci.backend.weather.dto.WeatherResponse;
@Aspect @Component
public class ProviderTelemetry {
    public record State(String provider,String state,Instant checkedAt) {}
    private final MeterRegistry meters;private final Clock clock;
    private final Map<String,State> states=new ConcurrentHashMap<>();
    public ProviderTelemetry(MeterRegistry meters,Clock clock){this.meters=meters;this.clock=clock;}
    @Around("execution(* com.nci.backend.provider.HazardProvider+.fetch(..)) || execution(* com.nci.backend.history.HistoricalProvider+.fetchHistory(..)) || execution(* com.nci.backend.weather.provider.WeatherProvider+.fetch(..)) || execution(* com.nci.backend.route.provider.GeocodingProvider+.search(..)) || execution(* com.nci.backend.route.provider.RouteProvider+.route(..))")
    public Object provider(ProceedingJoinPoint call) throws Throwable {
        Object target=call.getTarget();String source=target instanceof HazardProvider h?h.source():target instanceof com.nci.backend.weather.provider.WeatherProvider?"OPEN_METEO_WEATHER":target instanceof com.nci.backend.route.provider.GeocodingProvider?"OPEN_METEO_GEOCODING":"OSRM";
        long start=System.nanoTime();String state="UNAVAILABLE";
        try {Object result=call.proceed();state=result instanceof ProviderResult r?switch(r.coverageStatus()) {case COMPLETE->"HEALTHY";case UNAVAILABLE->"UNAVAILABLE";default->"DEGRADED";}:result instanceof WeatherResponse w&&(w.partial()||w.stale())?"DEGRADED":"HEALTHY";return result;}
        finally {record(source,state,System.nanoTime()-start);}
    }
    public void record(String source,String state,long nanos) {
        states.put(source,new State(source,state,clock.instant()));
        meters.timer("custerra.provider.latency","provider",source,"state",state).record(nanos,java.util.concurrent.TimeUnit.NANOSECONDS);
        if(!state.equals("HEALTHY")) meters.counter("custerra.provider.degraded","provider",source,"state",state).increment();
    }
    public List<State> snapshot() {
        return List.of("USGS","FIRMS","GDACS","OPEN_METEO_WEATHER","OPEN_METEO_GEOCODING","OSRM").stream().map(source->{
            State s=states.getOrDefault(source,new State(source,"NOT_CHECKED",null));
            return s.checkedAt()!=null&&s.checkedAt().isBefore(clock.instant().minusSeconds(900))?new State(source,"STALE",s.checkedAt()):s;
        }).toList();
    }
    @Around("execution(* com.nci.backend.monitoring.service.TripMonitoringService.check(..)) || execution(* com.nci.backend.alert.service.NotificationDispatcher.dispatch(..))")
    public Object job(ProceedingJoinPoint call) throws Throwable {
        String job=call.getTarget() instanceof com.nci.backend.alert.service.NotificationDispatcher?"notifications":"journey_check";
        try{return call.proceed();}catch(Throwable failure){meters.counter("custerra.job.failures","job",job).increment();throw failure;}
    }
    @Around("execution(* com.nci.backend.alert.provider.PushGateway+.send(..))")
    public Object delivery(ProceedingJoinPoint call) throws Throwable {
        String outcome="UNCERTAIN";
        try {Object result=call.proceed();if(result instanceof com.nci.backend.alert.provider.PushGateway.Result r)outcome=r.outcome();return result;}
        finally {meters.counter("custerra.notification.attempts","outcome",outcome).increment();}
    }
}
