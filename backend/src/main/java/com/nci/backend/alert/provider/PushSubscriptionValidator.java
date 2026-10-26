package com.nci.backend.alert.provider;
import java.net.URI;
import java.util.*;
public final class PushSubscriptionValidator {
    private PushSubscriptionValidator() {}
    public static void validateEndpoint(String value) {
        var uri=URI.create(value);String host=uri.getHost();
        if(value.length()>2048 || !"https".equals(uri.getScheme()) || uri.getRawUserInfo()!=null || uri.getPort()!=-1 || uri.getRawFragment()!=null
                || host==null || !(Set.of("fcm.googleapis.com","updates.push.services.mozilla.com","web.push.apple.com").contains(host)
                    || host.endsWith(".push.apple.com"))) throw new IllegalArgumentException("Unsupported push endpoint");
    }
    public static void validateKeys(String p256dh,String auth) {
        try {
            if(Base64.getUrlDecoder().decode(p256dh).length!=65 || Base64.getUrlDecoder().decode(auth).length!=16) throw new IllegalArgumentException();
            nl.martijndwars.webpush.Utils.loadPublicKey(p256dh);
        } catch(Exception invalid) { throw new IllegalArgumentException("Invalid push subscription keys"); }
    }
}
