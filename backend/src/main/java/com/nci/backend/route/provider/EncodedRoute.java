package com.nci.backend.route.provider;

import java.util.*;
import com.nci.backend.route.dto.RoutePoint;

/** Bounded polyline6 decoder. Preserves the provider's path without lossy simplification. */
public final class EncodedRoute {
    private EncodedRoute() {}
    public static List<RoutePoint> decode(String encoded) {
        if(encoded==null || encoded.isEmpty() || encoded.length()>200_000) throw new IllegalArgumentException("Invalid encoded route");
        var points=new ArrayList<RoutePoint>(); long lat=0,lon=0; int index=0;
        while(index<encoded.length()) {
            long[] a=number(encoded,index); index=(int)a[1]; lat+=a[0];
            long[] b=number(encoded,index); index=(int)b[1]; lon+=b[0];
            if(Math.abs(lat)>90_000_000 || Math.abs(lon)>180_000_000 || points.size()>=20_000)
                throw new IllegalArgumentException("Route geometry exceeds bounds");
            points.add(new RoutePoint(lat/1_000_000.0,lon/1_000_000.0));
        }
        if(points.size()<2) throw new IllegalArgumentException("Insufficient geometry");
        return List.copyOf(points);
    }
    private static long[] number(String encoded,int index) {
        long value=0; int shift=0;
        while(true) {
            if(index>=encoded.length() || shift>30) throw new IllegalArgumentException("Invalid polyline");
            int digit=encoded.charAt(index++)-63;
            if(digit<0 || digit>63) throw new IllegalArgumentException("Invalid polyline character");
            value|=(long)(digit&31)<<shift;
            if(digit<32) return new long[]{(value&1)==0 ? value>>1 : ~(value>>1),index};
            shift+=5;
        }
    }
}
