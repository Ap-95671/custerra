package com.nci.backend.monitoring.service;

import java.time.*;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.nci.backend.monitoring.dto.*;
import com.nci.backend.route.dto.*;
import com.nci.backend.route.service.*;
import com.nci.backend.hazard.service.GeoDistance;

@Component
public class JourneyProgressPolicy {
    private final Clock clock;private final RouteGeometry geometry;private final GeoDistance geo;
    public JourneyProgressPolicy(Clock clock,RouteGeometry geometry,GeoDistance geo){this.clock=clock;this.geometry=geometry;this.geo=geo;}
    public JourneyProgress match(RouteRiskRequest route,JourneyProgress.Update update) {
        var now=clock.instant();var p=update.position();
        if(p==null||p.latitude()==null||p.longitude()==null||!Double.isFinite(p.latitude())||!Double.isFinite(p.longitude())
                ||Math.abs(p.latitude())>90||Math.abs(p.longitude())>180||update.observedAt()==null
                ||update.observedAt().isAfter(now.plusSeconds(60))||update.observedAt().isBefore(now.minusSeconds(86400))
                ||update.accuracyMeters()==null||!Double.isFinite(update.accuracyMeters())||update.accuracyMeters()<0||update.accuracyMeters()>100000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        var match=new RouteSegmentIndex(route.points(),geometry).closest(p.latitude(),p.longitude());
        // At crossings and loops, prefer the earliest plausible segment, not a later nearby pass.
        int segment=match.segmentIndex();
        for(int i=0;i<segment;i++)if(geometry.segmentDistance(route.points().get(i),route.points().get(i+1),p.latitude(),p.longitude())
                <=match.distanceKm()+update.accuracyMeters()/1000+0.01){segment=i;break;}
        double total=0,completed=0;
        for(int i=0;i<route.points().size()-1;i++) {
            var a=route.points().get(i);var b=route.points().get(i+1);double km=geo.kilometers(a.latitude(),a.longitude(),b.latitude(),b.longitude());
            total+=km;if(i<segment)completed+=km;
        }
        String state=update.accuracyMeters()>100?"INACCURATE":match.distanceKm()>0.5?"OFF_ROUTE":"MATCHED";
        return fresh(new JourneyProgress(p,update.observedAt(),update.accuracyMeters(),segment,total==0?0:completed/total,match.distanceKm(),state));
    }
    public JourneyProgress fresh(JourneyProgress p) {
        if(p==null||p.observedAt().isBefore(clock.instant().minusSeconds(86400)))return null;
        if(p.observedAt().isBefore(clock.instant().minusSeconds(300)))return new JourneyProgress(p.position(),p.observedAt(),p.accuracyMeters(),p.segmentIndex(),p.completedFraction(),p.distanceFromRouteKm(),"STALE");
        return p;
    }
    public int startSegment(JourneyProgress p){p=fresh(p);return p!=null&&p.state().equals("MATCHED")?p.segmentIndex():0;}
}
