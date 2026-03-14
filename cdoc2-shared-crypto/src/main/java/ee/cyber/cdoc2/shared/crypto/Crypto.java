package ee.cyber.cdoc2.shared.crypto;

import java.security.DrbgParameters;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Crypto {

    private static final Logger log = LoggerFactory.getLogger(Crypto.class);

    private static SecureRandom secureRandomInstance = null;

    private Crypto() { }

    public static synchronized SecureRandom getSecureRandom() throws NoSuchAlgorithmException {
        if (secureRandomInstance == null) {
            secureRandomInstance = createSecureRandom();
        }

        return secureRandomInstance;
    }

    private static SecureRandom createSecureRandom() throws NoSuchAlgorithmException {
        log.debug("Initializing SecureRandom");
        SecureRandom sRnd = SecureRandom.getInstance("DRBG", DrbgParameters.instantiation(256, DrbgParameters.Capability.PR_AND_RESEED, "CDOC2".getBytes()));
        log.info("Initialized SecureRandom.");
        return sRnd;
    }

    /**
     * If key is EC PKCS11 key (unextractable hardware key), that should only be used by the provider associated with
     * that token
     * @param key checked
     * @return true if key is EC key from PKCS11 or other hardware provider. Note that !isECPKCS11Key doesn't mean that
     *      the key is EC software key as key might be for some other algorithm
     */
    @SuppressWarnings("checkstyle:LineLength")
    public static boolean isECPKCS11Key(PrivateKey key) {
        // might be manufacturer specif, this true for Manufacturer ID: AS Sertifitseerimiskeskus
        // accessed through opensc-pkcs11
        // .toString(): "SunPKCS11-OpenSC EC private key, 384 bitstoken object, sensitive, unextractable)"
        // .getClass(): sun.security.pkcs11.P11Key$P11PrivateKey

        // https://docs.oracle.com/en/java/javase/17/security/pkcs11-reference-guide1.html#GUID-508B5E3B-BF39-4E02-A1BD-523352D3AA12
        // Software Key objects (or any Key object that has access to the actual key material) should implement
        // the interfaces in the java.security.interfaces and javax.crypto.interfaces packages (such as DSAPrivateKey).
        //
        // Key objects representing unextractable token keys should only implement the relevant generic interfaces in
        // the java.security and javax.crypto packages (PrivateKey, PublicKey, or SecretKey). Identification of
        // the algorithm of a key should be performed using the Key.getAlgorithm() method.
        // Note that a Key object for an unextractable token key can only be used by the provider associated with that
        // token.

        // algorithm is EC, but doesn't implement java.security.interfaces.ECKey
        return (KeyAlgorithm.isEcKeysAlgorithm(key.getAlgorithm())
            && !(key instanceof java.security.interfaces.ECKey));
    }

}
