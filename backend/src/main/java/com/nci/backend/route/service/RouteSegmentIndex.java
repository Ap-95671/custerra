package com.nci.backend.route.service;

import java.util.*;
import com.nci.backend.route.dto.RoutePoint;
import com.nci.backend.hazard.service.GeoDistance;

/** Conservative spherical block bounds; every original segment is retained and indexed. */
public final class RouteSegmentIndex {
    public record Match(int segmentIndex,double distanceKm) {}
    private record Block(int from,int to,RoutePoint center,double radius) {}
    private final List<RoutePoint> points;
    private final List<Block> blocks=new ArrayList<>();
    private final RouteGeometry geometry;
    private final GeoDistance geo=new GeoDistance();
    public RouteSegmentIndex(List<RoutePoint> points,RouteGeometry geometry) {
        this.points=points;this.geometry=geometry;
        for(int first=0;first<points.size()-1;first+=64) {
            int last=Math.min(first+64,points.size()-1);double radius=0;
            for(int i=first;i<last;i++)radius+=distance(points.get(i),points.get(i+1));
            // Path length bounds every point along all spherical segments in the block.
            blocks.add(new Block(first,last,points.get(first),radius+1e-7));
        }
    }
    private double distance(RoutePoint a,RoutePoint b){return geo.kilometers(a.latitude(),a.longitude(),b.latitude(),b.longitude());}
    public Match closest(double latitude,double longitude) {
        double best=Double.POSITIVE_INFINITY;int segment=0;
        for(var block:blocks) {
            if(geo.kilometers(block.center.latitude(),block.center.longitude(),latitude,longitude)-block.radius>best)continue;
            for(int i=block.from;i<block.to;i++) {
                double distance=geometry.segmentDistance(points.get(i),points.get(i+1),latitude,longitude);
                if(distance<best){best=distance;segment=i;}
            }
        }
        return new Match(segment,best);
    }
}
