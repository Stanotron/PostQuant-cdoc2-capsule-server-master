package ee.cyber.cdoc2.server;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

public final class MlDsaTools {

    private static final String BC = "BC";

    private MlDsaTools() {
    }

    static {
        if (Security.getProvider(BC) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public static PublicKey decodeMlDsaPublicKey(byte[] encodedPublicKey)
            throws GeneralSecurityException {

        KeyFactory keyFactory = KeyFactory.getInstance("MLDSA", BC);
        return keyFactory.generatePublic(new X509EncodedKeySpec(encodedPublicKey));
    }

    public static boolean verifyMlDsaSignature(
            byte[] encodedPublicKey,
            String signedMessage,
            byte[] signatureBytes
    ) throws GeneralSecurityException {

        PublicKey publicKey = decodeMlDsaPublicKey(encodedPublicKey);

        Signature verifier = Signature.getInstance("MLDSA", BC);
        verifier.initVerify(publicKey);
        verifier.update(signedMessage.getBytes(StandardCharsets.UTF_8));

        return verifier.verify(signatureBytes);
    }
}

