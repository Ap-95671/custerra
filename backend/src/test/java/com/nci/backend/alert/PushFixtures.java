package com.nci.backend.alert;
import java.security.*;
import java.util.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.interfaces.*;
public class PushFixtures {
    public record Keys(String publicKey,String privateKey,String auth) {}
    public static Keys keys() {
        try {
            if(Security.getProvider("BC")==null)Security.addProvider(new BouncyCastleProvider());
            var generator=KeyPairGenerator.getInstance("ECDH","BC");generator.initialize(org.bouncycastle.jce.ECNamedCurveTable.getParameterSpec("prime256v1"));var pair=generator.generateKeyPair();
            var encoder=Base64.getUrlEncoder().withoutPadding();var scalar=((ECPrivateKey)pair.getPrivate()).getD().toByteArray();var fixed=new byte[32];
            System.arraycopy(scalar,Math.max(0,scalar.length-32),fixed,Math.max(0,32-scalar.length),Math.min(32,scalar.length));
            byte[] auth=new byte[16];new SecureRandom().nextBytes(auth);
            return new Keys(encoder.encodeToString(((ECPublicKey)pair.getPublic()).getQ().getEncoded(false)),encoder.encodeToString(fixed),encoder.encodeToString(auth));
        }catch(Exception failure){throw new IllegalStateException(failure);}
    }
}
