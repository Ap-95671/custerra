package com.nci.backend.intelligence;

import java.time.*;
import java.util.*;
import com.nci.backend.provider.model.*;
import com.nci.backend.provider.ProviderException;
import com.nci.backend.route.dto.RoutePoint;
import com.nci.backend.route.provider.EncodedRoute;
import com.nci.backend.hazard.service.GeoDistance;
import org.springframework.stereotype.Service;

@Service
public class IntelligenceService {
    private final IntelligenceFeed feed;private final SpatialGeometry spatial;private final Clock clock;
    public IntelligenceService(IntelligenceFeed feed,SpatialGeometry spatial,Clock clock){this.feed=feed;this.spatial=spatial;this.clock=clock;}
    public record Item(IntelligenceEvent event,String state,SpatialGeometry.LocationMatch spatial) {}
    public record Response(Instant retrievedAt,String coverageStatus,List<Item> items,List<IntelligenceFeed.Snapshot> sources,List<Correlation> correlations,List<String> limitations) {}
    public record Correlation(List<String> sourceIdentities,String basis) {}
    public record Explanation(String text,List<IntelligenceEvent.SourceLink> sources,String mode,List<String> limitations) {}
    public List<IntelligenceFeed.Snapshot> snapshots(){
        var list=new ArrayList<IntelligenceFeed.Snapshot>();for(String source:List.of("NWS","EONET"))try{list.add(feed.fetch(source));}catch(RuntimeException e){list.add(new IntelligenceFeed.Snapshot(source,null,"UNAVAILABLE",List.of(),List.of(e instanceof ProviderException p&&p.getStatusCode().value()==504?"PROVIDER_TIMEOUT":"PROVIDER_UNAVAILABLE_OR_BACKOFF")));}return list;
    }
    public Response nearby(double lat,double lon,double radius,int days){new HazardQuery(lat,lon,radius,clock.instant().minus(Duration.ofDays(days)),clock.instant());if(days<1||days>30)throw new IllegalArgumentException("Lookback bound");return nearby(snapshots(),lat,lon,radius,days);}
    Response nearby(List<IntelligenceFeed.Snapshot> sources,double lat,double lon,double radius,int days){
        var now=clock.instant();var items=new ArrayList<Item>();
        for(var source:sources)for(var event:source.items()){
            if(event.source().equals("EONET")&&event.updatedAt().isBefore(now.minus(Duration.ofDays(days))))continue;
            var match=spatial.location(event.geometry(),lat,lon);
            // Area-text-only national alerts cannot be localized without inventing zone geometry.
            if(match.distanceKm()!=null&&match.distanceKm()<=radius)items.add(new Item(event,event.stateAt(now),match));
        }
        items.sort(Comparator.comparingDouble(i->i.spatial().distanceKm()));
        return new Response(now,coverage(sources),items,statusOnly(sources),correlate(items.stream().map(Item::event).toList()),List.of("NWS coverage is California only and feed responses are bounded. Zone-only warnings cannot be localized here; consult the source.","EONET extents are approximate, not official impact boundaries.","Geometry intersection is not physical impact or a safety determination. Overall risk remains UNKNOWN."));
    }
    public Response timeline(){var sources=snapshots();var now=clock.instant();var items=sources.stream().flatMap(s->s.items().stream()).sorted(Comparator.comparing(IntelligenceEvent::updatedAt).reversed()).limit(700).map(e->new Item(e,e.stateAt(now),null)).toList();return new Response(now,coverage(sources),items,statusOnly(sources),correlate(items.stream().map(Item::event).toList()),List.of("Bounded available-feed timeline, not a complete global disaster archive.","Observation/issue, update and retrieval times have different meanings. No historical animation is live monitoring."));}
    public record RouteItem(IntelligenceEvent event,String state,SpatialGeometry.RouteMatch spatial) {}
    public record RouteExposure(Instant assessedAt,String overallRisk,String coverageStatus,List<RouteItem> items,List<IntelligenceFeed.Snapshot> sources,boolean truncated,List<String> limitations) {}
    public RouteExposure route(List<RoutePoint> points){
        if(points==null||points.size()<2||points.size()>20000)throw new IllegalArgumentException("Route points bound");double km=0;var geo=new GeoDistance();
        for(int i=0;i<points.size();i++){var p=points.get(i);if(p==null||p.latitude()==null||p.longitude()==null||!Double.isFinite(p.latitude())||!Double.isFinite(p.longitude())||Math.abs(p.latitude())>90||Math.abs(p.longitude())>180)throw new IllegalArgumentException("Invalid route coordinate");if(i>0){var prev=points.get(i-1);km+=geo.kilometers(prev.latitude(),prev.longitude(),p.latitude(),p.longitude());}}
        if(km<.001||km>900)throw new IllegalArgumentException("Route length bound");
        var sources=snapshots();var items=new ArrayList<RouteItem>();int assessed=0;long work=0;boolean truncated=false;
        long routeParts=0;for(int i=1;i<points.size();i++)routeParts+=Math.max(1,(int)Math.ceil(geo.kilometers(points.get(i-1).latitude(),points.get(i-1).longitude(),points.get(i).latitude(),points.get(i).longitude())/2));
        double latMin=points.stream().mapToDouble(RoutePoint::latitude).min().orElseThrow(),latMax=points.stream().mapToDouble(RoutePoint::latitude).max().orElseThrow();
        candidates: for(var source:sources)for(var event:source.items()){
            String state=event.stateAt(clock.instant());if(!Set.of("CURRENT","UPCOMING","REPORTED_OPEN").contains(state)||event.geometry()==null)continue;
            var geometry=spatial.read(event.geometry());var envelope=geometry.getEnvelopeInternal();if(envelope.getMaxY()<latMin||envelope.getMinY()>latMax)continue;
            // Latitude rejection is deliberately conservative; longitude/date-line handling belongs to the geometry engine.
            if(++assessed>60){truncated=true;break candidates;}
            long cost=routeParts*geometry.getNumPoints();
            if(work+cost>8_000_000){truncated=true;items.add(new RouteItem(event,state,new SpatialGeometry.RouteMatch(null,null,List.of(),"SPATIAL_WORK_LIMIT_NOT_ASSESSED")));continue;}
            work+=cost;
            var match=spatial.route(event.geometry(),points);if(match.intersects()==null || match.intersects())items.add(new RouteItem(event,state,match));
        }
        return new RouteExposure(clock.instant(),"UNKNOWN",coverage(sources),items,statusOnly(sources),truncated,List.of("At most 60 latitude-overlapping source geometries and 8 million geometry/route-coordinate work units are assessed; truncation is explicit.","Route vertices are retained. Planar segments are densified to <=2km; spherical intersection lengths are approximations, not road closures or actual impact.","Polar/unsupported geometry remains unassessed; source points and tracks have no invented affected-area length."));
    }
    public List<RoutePoint> points(String encoded,List<RoutePoint> points){if((encoded==null)==(points==null))throw new IllegalArgumentException("Supply geometry or points, not both");return encoded==null?points:EncodedRoute.decode(encoded);}
    private List<IntelligenceFeed.Snapshot> statusOnly(List<IntelligenceFeed.Snapshot> sources){return sources.stream().map(s->new IntelligenceFeed.Snapshot(s.source(),s.retrievedAt(),s.coverageStatus(),List.of(),s.limitations())).toList();}
    private String coverage(List<IntelligenceFeed.Snapshot> sources){return sources.stream().allMatch(s->s.coverageStatus().equals("UNAVAILABLE"))?"UNAVAILABLE":"PARTIAL";}
    public static Explanation explain(IntelligenceEvent event,String state){return new Explanation(event.authority()+" reports: "+event.title()+". Information type: "+event.informationType()+"; source classification: "+event.severity()+". Issued/observed "+event.issuedAt()+", source update "+event.updatedAt()+", retrieved "+event.retrievedAt()+". State at assessment: "+state+". "+(event.geometry()==null?"No machine-readable source geometry is available.":"Displayed geometry comes from the source and does not establish physical impact.")+" Overall risk is UNKNOWN.",event.sourceLinks(),"DETERMINISTIC_SOURCE_SUMMARY",List.of("No LLM inference or evacuation advice. Consult the unaltered official source for instructions.","This explanation is application text, not an official advisory."));}
    public static List<Correlation> correlate(List<IntelligenceEvent> items){
        var byId=new HashMap<String,IntelligenceEvent>();for(var e:items)byId.put(e.source()+":"+e.id(),e);var groups=new ArrayList<Correlation>();
        for(var event:items)for(String ref:event.references()){var previous=byId.get(event.source()+":"+ref);if(previous!=null&&!previous.id().equals(event.id())&&previous.authority().equals(event.authority()))groups.add(new Correlation(List.of(previous.source()+":"+previous.id(),event.source()+":"+event.id()),"EXPLICIT_SOURCE_UPDATE_REFERENCE"));}
        return groups.stream().distinct().toList();
    }
}
