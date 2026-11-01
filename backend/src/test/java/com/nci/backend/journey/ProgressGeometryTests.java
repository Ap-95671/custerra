package com.nci.backend.journey;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.nci.backend.hazard.service.GeoDistance;
import com.nci.backend.route.dto.*;
import com.nci.backend.route.service.*;
import com.nci.backend.monitoring.dto.JourneyProgress;
import com.nci.backend.monitoring.service.JourneyProgressPolicy;
import static org.assertj.core.api.Assertions.*;

class ProgressGeometryTests {
    final GeoDistance geo=new GeoDistance();final RouteGeometry geometry=new RouteGeometry(geo);
    final Instant now=Instant.parse("2026-11-01T00:00:00Z");
    final JourneyProgressPolicy policy=new JourneyProgressPolicy(Clock.fixed(now,ZoneOffset.UTC),geometry,geo);
    RouteRiskRequest route(List<RoutePoint> points){return new RouteRiskRequest(points,5,7,0);}
    @Test void indexMatchesExhaustiveDistancesIncludingDatelineAndPolarGeometry() {
        var random=new Random(2026);
        for(double latitude:new double[]{0,45,89}) {
            var points=new ArrayList<RoutePoint>();
            for(int i=0;i<260;i++)points.add(new RoutePoint(latitude+Math.sin(i/10.)*.005,((179+i*.01+180)%360)-180));
            var index=new RouteSegmentIndex(points,geometry);
            for(int q=0;q<100;q++) {
                double lat=latitude+random.nextDouble()*.1-.05,lon=((179+random.nextDouble()*3+180)%360)-180;
                double expected=Double.POSITIVE_INFINITY;
                for(int i=0;i<points.size()-1;i++)expected=Math.min(expected,geometry.segmentDistance(points.get(i),points.get(i+1),lat,lon));
                assertThat(index.closest(lat,lon).distanceKm()).isCloseTo(expected,within(1e-9));
            }
        }
    }
    @Test void matchingRetainsEntireSegmentAndChoosesEarlierPassAtLoopCrossing() {
        var route=route(List.of(new RoutePoint(0.,0.),new RoutePoint(0.,1.),new RoutePoint(1.,1.),new RoutePoint(0.,1.),new RoutePoint(0.,2.)));
        var p=policy.match(route,new JourneyProgress.Update(new RoutePoint(0.,1.),now,10.));
        assertThat(p.segmentIndex()).isZero();assertThat(p.completedFraction()).isZero();
        assertThat(policy.startSegment(p)).isZero();
    }
    @Test void staleInaccurateAndOffRoutePositionsNeverNarrowMonitoring() {
        var route=route(List.of(new RoutePoint(0.,0.),new RoutePoint(0.,1.),new RoutePoint(0.,2.)));
        var recent=policy.match(route,new JourneyProgress.Update(new RoutePoint(0.,1.5),now,10.));
        assertThat(policy.startSegment(recent)).isEqualTo(1);
        assertThat(recent.completedFraction()).isCloseTo(.5,within(.00001));
        var stale=policy.match(route,new JourneyProgress.Update(new RoutePoint(0.,1.5),now.minusSeconds(301),10.));
        var inaccurate=policy.match(route,new JourneyProgress.Update(new RoutePoint(0.,1.5),now,101.));
        var offRoute=policy.match(route,new JourneyProgress.Update(new RoutePoint(1.,1.5),now,10.));
        assertThat(stale.state()).isEqualTo("STALE");assertThat(inaccurate.state()).isEqualTo("INACCURATE");assertThat(offRoute.state()).isEqualTo("OFF_ROUTE");
        for(var p:List.of(stale,inaccurate,offRoute))assertThat(policy.startSegment(p)).isZero();
        assertThat(policy.fresh(new JourneyProgress(recent.position(),now.minusSeconds(86401),10.,1,.5,0,"MATCHED"))).isNull();
    }
    @Test void impossibleCoordinatesFuturePositionsAndNonfiniteAccuracyAreRejected() {
        var route=route(List.of(new RoutePoint(0.,0.),new RoutePoint(0.,1.)));
        for(var p:List.of(new RoutePoint(91.,0.),new RoutePoint(0.,181.),new RoutePoint(Double.NaN,0.)))
            assertThatThrownBy(()->policy.match(route,new JourneyProgress.Update(p,now,10.))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        for(var time:List.of(now.plusSeconds(61),now.minusSeconds(86401)))
            assertThatThrownBy(()->policy.match(route,new JourneyProgress.Update(new RoutePoint(0.,0.),time,10.))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(()->policy.match(route,new JourneyProgress.Update(new RoutePoint(0.,0.),now,Double.NaN))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}
