package com.nci.backend.intelligence;
import java.time.*;
import java.net.URI;
import java.util.*;
import com.nci.backend.provider.*;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** On-demand global feed snapshots, cached for >=30s; no per-location upstream requests or polling. */
@Component
public class IntelligenceFeed {
    public record Snapshot(String source,Instant retrievedAt,String coverageStatus,List<IntelligenceEvent> items,List<String> limitations) {public Snapshot{items=List.copyOf(items);limitations=List.copyOf(limitations);}}
    private record Cached(Snapshot value,Instant expires) {}
    private final ObjectMapper json;private final Clock clock;private final SpatialGeometry spatial;private final RestClient client;
    private final boolean nwsEnabled,eonetEnabled;private final Map<String,Cached> cache=new HashMap<>();private final Map<String,Instant> blocked=new HashMap<>();
    @Autowired public IntelligenceFeed(ObjectMapper json,Clock clock,SpatialGeometry spatial,@Value("${NWS_ENABLED:true}")boolean nws,@Value("${EONET_ENABLED:true}")boolean eonet){this(json,clock,spatial,ProviderHttp.client(3000,10000),nws,eonet);}
    public IntelligenceFeed(ObjectMapper json,Clock clock,SpatialGeometry spatial,RestClient client,boolean nws,boolean eonet){this.json=json;this.clock=clock;this.spatial=spatial;this.client=client;this.nwsEnabled=nws;this.eonetEnabled=eonet;}
    public synchronized Snapshot fetch(String source) {
        if(!Set.of("NWS","EONET").contains(source))throw new IllegalArgumentException("Unsupported intelligence source");
        if(source.equals("NWS")?!nwsEnabled:!eonetEnabled)return new Snapshot(source,null,"UNAVAILABLE",List.of(),List.of("SOURCE_DISABLED"));
        var now=clock.instant();var previous=cache.get(source);if(previous!=null&&previous.expires().isAfter(now))return previous.value();
        if(blocked.getOrDefault(source,Instant.MIN).isAfter(now))throw new ProviderException(false);
        blocked.put(source,now.plusSeconds(60));
        var uri=URI.create(source.equals("NWS")?"https://api.weather.gov/alerts/active?status=actual&area=CA":"https://eonet.gsfc.nasa.gov/api/v3/events?status=all&days=30&limit=200");
        var root=json.readTree(ProviderHttp.get(client,uri,"application/geo+json, application/json",2_000_000));
        var result=source.equals("NWS")?parseNws(root,now):parseEonet(root,now);
        cache.put(source,new Cached(result,now.plusSeconds(60)));blocked.remove(source);return result;
    }
    public Snapshot parseNws(JsonNode root,Instant now){
        var features=root.get("features");if(features==null||!features.isArray()||features.size()>500)throw new ProviderException(false);
        var events=new LinkedHashMap<String,IntelligenceEvent>();int rejected=0;
        for(var feature:features){try{
            var p=feature.get("properties");if(!"Actual".equals(text(p,"status",40)))continue;
            String message=text(p,"messageType",40);if(!Set.of("Alert","Update","Cancel").contains(message))continue;
            String id=required(p,"id",500),title=required(p,"event",200),url=safeUrl(text(p,"web",2000));if(url==null)url=safeUrl(text(p,"@id",2000));if(url==null)url=safeUrl(id);if(url==null || !(URI.create(url).getHost().equals("weather.gov")||URI.create(url).getHost().endsWith(".weather.gov")))throw new IllegalArgumentException();
            var shape=shape(feature.get("geometry"));var issued=instant(p,"sent",true);var expires=instant(p,"expires",false);var effective=instant(p,"effective",false);var end=instant(p,"ends",false);
            if(expires!=null&&expires.isBefore(issued)||end!=null&&end.isBefore(issued))throw new IllegalArgumentException();
            var refs=new ArrayList<String>();var references=p.get("references");if(references!=null&&references.isArray())for(var ref:references){String value=text(ref,"identifier",500);if(!value.isBlank()&&refs.size()<50)refs.add(value);}
            var event=new IntelligenceEvent(id,"NWS",hazard(title),title.toLowerCase(Locale.ROOT).contains("warning")?"OFFICIAL_WARNING":title.toLowerCase(Locale.ROOT).contains("watch")?"OFFICIAL_WATCH":"OFFICIAL_ADVISORY",text(p,"headline",500).isBlank()?title:text(p,"headline",500),text(p,"description",12000),text(p,"severity",40),text(p,"urgency",40),text(p,"certainty",40),text(p,"language",40),text(p,"senderName",200),message,"CURRENT",issued,effective,expires,issued,now,text(p,"areaDesc",4000),url,shape,refs,List.of(new IntelligenceEvent.SourceLink("NWS",url)),List.of(),Map.of("event",title,"instruction",text(p,"instruction",8000),"response",text(p,"response",80),"geometryStatus",shape==null?"AREA_TEXT_ONLY":"SOURCE_GEOMETRY","ends",end==null?"":end.toString()));
            events.merge(id,event,(a,b)->b.issuedAt().isAfter(a.issuedAt())?b:a);
        }catch(RuntimeException invalid){rejected++;}}
        return new Snapshot("NWS",now,"PARTIAL",new ArrayList<>(events.values()),List.of("NWS California active alerts only; other U.S. states and countries are not covered by this adapter.","Bounded California snapshot; no absent-warning or cancellation inference. Zone-only products can have no polygon.","REJECTED_ITEMS: "+rejected,"PAGE_LIMIT_OR_PAGINATION: "+(features.size()==500||root.hasNonNull("pagination"))));
    }
    public Snapshot parseEonet(JsonNode root,Instant now){
        var values=root.get("events");if(values==null||!values.isArray()||values.size()>200)throw new ProviderException(false);
        var items=new ArrayList<IntelligenceEvent>();int rejected=0;
        for(var value:values){try{
            String id=required(value,"id",200),title=required(value,"title",500);String url=safeUrl(text(value,"link",2000));if(url==null || !URI.create(url).getHost().equals("eonet.gsfc.nasa.gov"))throw new IllegalArgumentException();
            var categories=value.get("categories");String category=categories!=null&&categories.isArray()&&!categories.isEmpty()?text(categories.get(0),"id",80):"";String hazard=switch(category){case"wildfires"->"WILDFIRE";case"volcanoes"->"VOLCANIC_ERUPTION";case"floods"->"FLOOD";case"landslides"->"LANDSLIDE";case"severeStorms"->"SEVERE_WEATHER";case"earthquakes"->"EARTHQUAKE";default->null;};if(hazard==null)continue;
            var geometries=value.get("geometry");if(geometries==null||!geometries.isArray()||geometries.isEmpty()||geometries.size()>500)throw new IllegalArgumentException();
            JsonNode latest=null;Instant last=null,first=null;var track=new ArrayList<IntelligenceEvent.Position>();
            for(var g:geometries){var at=instant(g,"date",true);if(at.isAfter(now.plusSeconds(3600)))throw new IllegalArgumentException();if(first==null||at.isBefore(first))first=at;if(last==null||at.isAfter(last)){latest=g;last=at;}if("Point".equals(text(g,"type",40))){var shape=shape(g);var c=shape.coordinates();track.add(new IntelligenceEvent.Position(at,c.get(1).asDouble(),c.get(0).asDouble()));}}
            var links=new ArrayList<IntelligenceEvent.SourceLink>();var rawSources=value.get("sources");if(rawSources!=null&&rawSources.isArray())for(var link:rawSources){String href=safeUrl(text(link,"url",2000));if(href!=null&&links.size()<30)links.add(new IntelligenceEvent.SourceLink(text(link,"id",120),href));}
            var closed=instant(value,"closed",false);var geometry=shape(latest);
            items.add(new IntelligenceEvent(id,"EONET",hazard,"CURATED_EVENT",title,text(value,"description",8000),"UNKNOWN","UNKNOWN","UNKNOWN","Not supplied","NASA EONET curated metadata","Report",closed==null?"REPORTED_OPEN":"REPORTED_CLOSED",first,first,closed,last,now,"Approximate source-reported location or extent",url,geometry,List.of(),links,track.stream().sorted(Comparator.comparing(IntelligenceEvent.Position::at)).toList(),Map.of("category",category,"geometryStatus",geometry==null?"UNAVAILABLE":"APPROXIMATE_SOURCE_GEOMETRY","interpretation","Visualization/general information only. Extent and open/closed labels are not official warnings or confirmed impact.")));
        }catch(RuntimeException invalid){rejected++;}}
        return new Snapshot("EONET",now,"PARTIAL",items,List.of("Curated approximate metadata, not official spatial/temporal extent.","Last 30 days, one page, up to 200 events; no completeness claim.","Original sources retain their own rights and interpretations.","REJECTED_ITEMS: "+rejected));
    }
    private IntelligenceEvent.Shape shape(JsonNode node){if(node==null||node.isNull())return null;var result=new IntelligenceEvent.Shape(required(node,"type",40),node.get("coordinates"));spatial.read(result);return result;}
    private static String hazard(String name){String s=name.toLowerCase(Locale.ROOT);return s.contains("tsunami")?"TSUNAMI":s.contains("tornado")?"TORNADO":s.contains("hurricane")?"HURRICANE":s.contains("typhoon")?"TYPHOON":s.contains("cyclone")?"CYCLONE":s.contains("flood")?"FLOOD":s.contains("volcan")?"VOLCANIC_ERUPTION":s.contains("landslide")||s.contains("debris flow")?"LANDSLIDE":"SEVERE_WEATHER";}
    static String text(JsonNode node,String key,int max){if(node==null)return "";var value=node.get(key);if(value==null||value.isNull())return "";if(!value.isString()||value.asText().length()>max)throw new IllegalArgumentException();return value.asText();}
    static String required(JsonNode node,String key,int max){String value=text(node,key,max);if(value.isBlank())throw new IllegalArgumentException();return value;}
    static Instant instant(JsonNode node,String key,boolean required){String value=text(node,key,100);if(value.isBlank()){if(required)throw new IllegalArgumentException();return null;}return Instant.parse(value);}
    static String safeUrl(String value){try{var u=URI.create(value);return "https".equals(u.getScheme())&&u.getHost()!=null&&u.getUserInfo()==null?u.toString():null;}catch(RuntimeException invalid){return null;}}
}
