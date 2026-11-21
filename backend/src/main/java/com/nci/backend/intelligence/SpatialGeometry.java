package com.nci.backend.intelligence;

import java.util.*;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.operation.valid.IsValidOp;
import org.locationtech.jts.operation.distance.DistanceOp;
import tools.jackson.databind.JsonNode;
import com.nci.backend.route.dto.RoutePoint;
import com.nci.backend.hazard.service.GeoDistance;
import org.springframework.stereotype.Component;

/** Bounded, longitude-unwrapped planar topology; lengths use spherical surface distance. Not impact modeling. */
@Component
public class SpatialGeometry {
    private final GeometryFactory factory=new GeometryFactory();
    private final GeoDistance distance=new GeoDistance();
    public Geometry read(IntelligenceEvent.Shape shape) {
        if(shape==null)return null;
        int[] count={0};var n=shape.coordinates();
        Geometry result=switch(shape.type()) {
            case "Point" -> factory.createPoint(coordinate(n,count));
            case "LineString" -> factory.createLineString(line(n,count,false));
            case "Polygon" -> polygon(n,count);
            case "MultiPolygon" -> { if(!n.isArray() || n.size()>200)throw new IllegalArgumentException("Geometry bound");var polygons=new Polygon[n.size()];for(int i=0;i<n.size();i++)polygons[i]=polygon(n.get(i),count);yield factory.createMultiPolygon(polygons); }
            default -> throw new IllegalArgumentException("Unsupported geometry");
        };
        if(result.isEmpty() || !new IsValidOp(result).isValid())throw new IllegalArgumentException("Invalid geometry topology");
        return result;
    }
    private Coordinate coordinate(JsonNode n,int[] count) {
        if(n==null || !n.isArray() || n.size()<2 || n.size()>3 || !n.get(0).isNumber() || !n.get(1).isNumber() || ++count[0]>20000)throw new IllegalArgumentException("Invalid coordinates");
        double lon=n.get(0).asDouble(),lat=n.get(1).asDouble();
        if(!Double.isFinite(lon)||!Double.isFinite(lat)||Math.abs(lon)>180||Math.abs(lat)>90)throw new IllegalArgumentException("Invalid coordinates");
        return new Coordinate(lon,lat);
    }
    private Coordinate[] line(JsonNode n,int[] count,boolean ring) {
        if(n==null||!n.isArray()||n.size()<(ring?4:2)||n.size()>20000)throw new IllegalArgumentException("Invalid line");
        var points=new Coordinate[n.size()];double previous=0;
        for(int i=0;i<n.size();i++){points[i]=coordinate(n.get(i),count);if(i>0){while(points[i].x-previous>180)points[i].x-=360;while(points[i].x-previous< -180)points[i].x+=360;}previous=points[i].x;}
        if(ring && !points[0].equals2D(points[points.length-1]))throw new IllegalArgumentException("Unclosed ring");
        double min=Arrays.stream(points).mapToDouble(c->c.x).min().orElseThrow(),max=Arrays.stream(points).mapToDouble(c->c.x).max().orElseThrow();
        if(max-min>180)throw new IllegalArgumentException("Geometry spanning more than a hemisphere is not supported");
        return points;
    }
    private Polygon polygon(JsonNode n,int[] count) {
        if(n==null||!n.isArray()||n.isEmpty()||n.size()>200)throw new IllegalArgumentException("Invalid polygon");
        var shell=factory.createLinearRing(line(n.get(0),count,true));var holes=new LinearRing[n.size()-1];
        for(int i=1;i<n.size();i++){var points=line(n.get(i),count,true);double shift=360*Math.rint((shell.getEnvelopeInternal().centre().x-points[0].x)/360);for(var point:points)point.x+=shift;holes[i-1]=factory.createLinearRing(points);}
        return factory.createPolygon(shell,holes);
    }
    private Geometry aligned(Geometry geometry,double longitude) {
        double shift=360*Math.rint((longitude-geometry.getEnvelopeInternal().centre().x)/360);
        return shift==0?geometry:AffineTransformation.translationInstance(shift,0).transform(geometry);
    }
    public record LocationMatch(Boolean intersects,Double distanceKm,String method) {}
    public LocationMatch location(IntelligenceEvent.Shape shape,double lat,double lon) {
        if(shape==null)return new LocationMatch(null,null,"NO_SOURCE_GEOMETRY");
        var geometry=aligned(read(shape),lon);
        if(Math.abs(lat)>85 || Arrays.stream(geometry.getCoordinates()).anyMatch(c->Math.abs(c.y)>85))return new LocationMatch(null,null,"POLAR_GEOMETRY_NOT_ASSESSED");
        var point=factory.createPoint(new Coordinate(lon,lat));boolean intersects=geometry.intersects(point);
        var near=DistanceOp.nearestPoints(point,geometry);double km=intersects?0:distance.kilometers(near[0].y,wrap(near[0].x),near[1].y,wrap(near[1].x));
        return new LocationMatch(intersects,km,"PLANAR_TOPOLOGY_SPHERICAL_DISTANCE");
    }
    public record RouteMatch(Boolean intersects,Double lengthWithinKm,List<Integer> segmentIndices,String method) {}
    public RouteMatch route(IntelligenceEvent.Shape shape,List<RoutePoint> points) {
        if(shape==null)return new RouteMatch(null,null,List.of(),"NO_SOURCE_GEOMETRY");
        var geometry=read(shape);
        if(points.size()<2 || points.size()>20000)throw new IllegalArgumentException("Route bound");
        if(points.stream().anyMatch(p->Math.abs(p.latitude())>85)||Arrays.stream(geometry.getCoordinates()).anyMatch(c->Math.abs(c.y)>85))return new RouteMatch(null,null,List.of(),"POLAR_GEOMETRY_NOT_ASSESSED");
        var indices=new ArrayList<Integer>();double length=0;int work=0;
        for(int i=0;i<points.size()-1;i++){
            var a=points.get(i);var b=points.get(i+1);double end=b.longitude();while(end-a.longitude()>180)end-=360;while(end-a.longitude()< -180)end+=360;
            int parts=Math.max(1,(int)Math.ceil(distance.kilometers(a.latitude(),a.longitude(),b.latitude(),b.longitude())/2));
            if((work+=parts)>30000)throw new IllegalArgumentException("Spatial work bound");
            boolean matched=false;
            for(int part=0;part<parts;part++){
                double f=(double)part/parts,g=(double)(part+1)/parts;
                var start=new Coordinate(a.longitude()+(end-a.longitude())*f,a.latitude()+(b.latitude()-a.latitude())*f);
                var finish=new Coordinate(a.longitude()+(end-a.longitude())*g,a.latitude()+(b.latitude()-a.latitude())*g);
                var segment=factory.createLineString(new Coordinate[]{start,finish});var area=aligned(geometry,(start.x+finish.x)/2);
                if(!area.getEnvelopeInternal().intersects(segment.getEnvelopeInternal()) || !area.intersects(segment))continue;
                matched=true;
                if(area.getDimension()==2)length+=length(segment.intersection(area));
            }
            if(matched)indices.add(i);
        }
        return new RouteMatch(!indices.isEmpty(),geometry.getDimension()==2?length:null,indices,"DENSIFIED_PLANAR_INTERSECTION_SPHERICAL_LENGTH");
    }
    private double length(Geometry geometry) {
        if(geometry instanceof LineString line){double total=0;var points=line.getCoordinates();for(int i=1;i<points.length;i++)total+=distance.kilometers(points[i-1].y,wrap(points[i-1].x),points[i].y,wrap(points[i].x));return total;}
        if(geometry instanceof GeometryCollection){double total=0;for(int i=0;i<geometry.getNumGeometries();i++)total+=length(geometry.getGeometryN(i));return total;}return 0;
    }
    private double wrap(double lon){return ((lon+180)%360+360)%360-180;}
}
