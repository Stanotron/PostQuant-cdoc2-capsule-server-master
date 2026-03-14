package ee.cyber.cdoc2.shared.crypto;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.interfaces.ECKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidParameterSpecException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.custom.sec.SecP256R1Curve;
import org.bouncycastle.math.ec.custom.sec.SecP384R1Curve;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EC keys loading, decoding and encoding. Currently, supports only secp384r1 EC keys.
 */
@SuppressWarnings("squid:S6706")
public final class ECKeys {

    //https://docs.oracle.com/en/java/javase/17/security/oracle-providers.html#GUID-091BF58C-82AB-4C9C-850F-1660824D5254
    public static final String SECP_384_R_1 = "secp384r1";
    public static final String SECP_384_OID = "1.3.132.0.34";

    /**
     * Key length for secp384r1 curve in bytes
     */
    public static final int SECP_384_R_1_LEN_BYTES = 384 / 8;

    // for validating that decoded ECPoints are valid for secp384r1 curve
    private static final ECCurve SECP_384_R_1_CURVE = new SecP384R1Curve();

    public static final String SECP_256_R_1 = "secp256r1";
    public static final String SECP_256_OID = "1.2.840.10045.3.1.7";
    /**
     * Key length for secp384r1 curve in bytes
     */
    public static final int SECP_256_R_1_LEN_BYTES = 256 / 8;
    private static final ECCurve SECP_256_R_1_CURVE = new SecP256R1Curve();

    private static final Logger log = LoggerFactory.getLogger(ECKeys.class);

    private ECKeys() {
    }

    public static KeyPair generateEcKeyPair(String ecCurveName)
        throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
        KeyPairGenerator keyPairGenerator
            = KeyPairGenerator.getInstance(KeyAlgorithm.Algorithm.EC.name());
        keyPairGenerator.initialize(new ECGenParameterSpec(ecCurveName));
        return keyPairGenerator.generateKeyPair();
    }

    /**
     * Encode EcPublicKey in TLS 1.3 format https://datatracker.ietf.org/doc/html/rfc8446#section-4.2.8.2
     * @param curve EC curve that this ecPublicKey uses. Used to get curve key length.
     * @param ecPublicKey EC public key
     * @return ecPublicKey encoded in TLS 1.3 EC pub key format
     */
    public static byte[] encodeEcPubKeyForTls(EllipticCurve curve, ECPublicKey ecPublicKey) {
        int keyLength = curve.getKeyLength();
        return encodeEcPubKeyForTls(ecPublicKey, keyLength);
    }

    /**
     * Encode EcPublicKey in TLS 1.3 format https://datatracker.ietf.org/doc/html/rfc8446#section-4.2.8.2
     * @param ecPublicKey EC public key
     * @return ecPublicKey encoded in TLS 1.3 EC pub key format
     */
    public static byte[] encodeEcPubKeyForTls(ECPublicKey ecPublicKey) throws GeneralSecurityException {
        if (ECPoint.POINT_INFINITY.equals(ecPublicKey.getW())) {
            throw new IllegalArgumentException("Cannot encode infinity ECPoint");
        }
        EllipticCurve curve = EllipticCurve.forOid(ECKeys.getCurveOid(ecPublicKey));
        int keyLength = curve.getKeyLength();
        return encodeEcPubKeyForTls(ecPublicKey, keyLength);
    }

    @SuppressWarnings("checkstyle:LineLength")
    private static byte[] encodeEcPubKeyForTls(ECPublicKey ecPublicKey, int keyLength) {
        byte[] xBytes = toUnsignedByteArray(ecPublicKey.getW().getAffineX(), keyLength);
        byte[] yBytes = toUnsignedByteArray(ecPublicKey.getW().getAffineY(), keyLength);

        //CHECKSTYLE:OFF
        //EC pubKey in TLS 1.3 format
        //https://datatracker.ietf.org/doc/html/rfc8446#section-4.2.8.2
        //https://github.com/bcgit/bc-java/blob/526b5846653100fc521c1a68c02dbe9df3347a29/core/src/main/java/org/bouncycastle/math/ec/ECCurve.java#L410
        //CHECKSTYLE:ON
        byte[] tlsPubKey = new byte[1 + xBytes.length + yBytes.length];
        tlsPubKey[0] = 0x04; //uncompressed

        System.arraycopy(xBytes, 0, tlsPubKey, 1, xBytes.length);
        System.arraycopy(yBytes, 0, tlsPubKey,  1 + xBytes.length, yBytes.length);

        return tlsPubKey;
    }

    static ECPublicKey decodeSecP384R1EcPublicKeyFromTls(ByteBuffer encoded) throws GeneralSecurityException {
        return decodeSecP384R1EcPublicKeyFromTls(
            Arrays.copyOfRange(encoded.array(), encoded.position(), encoded.limit()));
    }

    static ECPublicKey decodeSecP256R1EcPublicKeyFromTls(ByteBuffer encoded) throws GeneralSecurityException {
        return decodeSecP256R1EcPublicKeyFromTls(
            Arrays.copyOfRange(encoded.array(), encoded.position(), encoded.limit()));
    }

    /**
     * Decode EcPublicKey from TLS 1.3 format https://datatracker.ietf.org/doc/html/rfc8446#section-4.2.8.2
     * @param encoded EC public key octets encoded as in TLS 1.3 format. Expects key to be part of secp384r1 curve
     * @return decoded ECPublicKey
     * @throws GeneralSecurityException
     */
    public static ECPublicKey decodeSecP384R1EcPublicKeyFromTls(byte[] encoded) throws GeneralSecurityException {

        ECPublicKey ecPublicKey = validateSizeAndGetKey(encoded, SECP_384_R_1_LEN_BYTES, SECP_384_R_1);

        if (!isValidSecP384R1(ecPublicKey)) {
            throw new InvalidKeyException("Not valid secp384r1 EC public key " + HexFormat.of().formatHex(encoded));
        }
        return ecPublicKey;
    }

    /**
     * Decode EcPublicKey from TLS 1.3 format https://datatracker.ietf.org/doc/html/rfc8446#section-4.2.8.2
     * @param encoded EC public key octets encoded as in TLS 1.3 format. Expects key to be part of secp256r1 curve
     * @return decoded ECPublicKey
     * @throws GeneralSecurityException
     */
    public static ECPublicKey decodeSecP256R1EcPublicKeyFromTls(byte[] encoded) throws GeneralSecurityException {

        ECPublicKey ecPublicKey = validateSizeAndGetKey(encoded, SECP_256_R_1_LEN_BYTES, SECP_256_R_1);

        if (!isValidSecP256R1(ecPublicKey)) {
            throw new InvalidKeyException("Not valid secp256r1 EC public key " + HexFormat.of().formatHex(encoded));
        }
        return ecPublicKey;
    }

    private static ECPublicKey validateSizeAndGetKey(
        byte[] encoded,
        int expectedLength,
        String stdName
    ) throws GeneralSecurityException {
        String encodedHex = HexFormat.of().formatHex(encoded);
        if (encoded.length != 2 * expectedLength + 1) {

            log.error("Invalid pubKey len {}, expected {}, encoded: {}", encoded.length, (2 * expectedLength + 1),
                encodedHex);
            throw new IllegalArgumentException("Incorrect length for uncompressed encoding");
        }

        if (encoded[0] != 0x04) {
            log.error("Illegal EC pub key encoding. Encoded: {}", encodedHex);
            throw new IllegalArgumentException("Invalid encoding");
        }

        BigInteger x = new BigInteger(1, Arrays.copyOfRange(encoded, 1, expectedLength + 1));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(encoded, expectedLength + 1, encoded.length));

        ECPoint pubPoint = new ECPoint(x, y);
        AlgorithmParameters params = AlgorithmParameters.getInstance(KeyAlgorithm.Algorithm.EC.name());
        params.init(new ECGenParameterSpec(stdName));

        ECParameterSpec ecParameters = params.getParameterSpec(ECParameterSpec.class);
        ECPublicKeySpec pubECSpec = new ECPublicKeySpec(pubPoint, ecParameters);

        return (ECPublicKey) KeyFactory
            .getInstance(KeyAlgorithm.Algorithm.EC.name()).generatePublic(pubECSpec);
    }

    private static byte[] toUnsignedByteArray(BigInteger bigInteger, int len) {
        Objects.requireNonNull(bigInteger, "Cannot convert null bigInteger to byte[]");
        //https://stackoverflow.com/questions/4407779/biginteger-to-byte
        byte[] array = bigInteger.toByteArray();
        if ((array[0] == 0) && (array.length == len + 1)) {
            return Arrays.copyOfRange(array, 1, array.length);
        } else if (array.length < len) {
            byte[] padded = new byte[len];
            System.arraycopy(array, 0, padded, len - array.length, array.length);
            return padded;
        } else {
            if ((array.length != len) && (log.isWarnEnabled())) {
                log.warn("Expected EC key to be {} bytes, but was {}. bigInteger: {}",
                    len, array.length, bigInteger.toString(16));
            }
            return array;
        }
    }

    public static String getCurveOid(ECKey key)
        throws NoSuchAlgorithmException, InvalidParameterSpecException, NoSuchProviderException {

        AlgorithmParameters params
            = AlgorithmParameters.getInstance(KeyAlgorithm.Algorithm.EC.name(), "SunEC");
        params.init(key.getParams());

        // JavaDoc NamedParameterSpec::getName() : Returns the standard name that determines the algorithm parameters.
        // and https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html#parameterspec-names
        // lists "secp384r1" as standard name.
        // But in practice SunEC and BC both return "1.3.132.0.34"
        return params.getParameterSpec(ECGenParameterSpec.class).getName();
    }

    public static boolean isEcSecp384r1Curve(ECKey key) throws GeneralSecurityException {
        // https://docs.oracle.com/en/java/javase/17/security/oracle-providers.html
        // Table 4-28 Recommended Curves Provided by the SunEC Provider
        final String[] secp384r1Names = {SECP_384_OID, SECP_384_R_1, "NIST P-384"};
        String oid = getCurveOid(key);
        return Arrays.asList(secp384r1Names).contains(oid);
    }

    public static boolean isEcSecp256r1Curve(ECKey key) throws GeneralSecurityException {
        // https://docs.oracle.com/en/java/javase/17/security/oracle-providers.html
        // Table 4-28 Recommended Curves Provided by the SunEC Provider
        final String[] secp256r1Names = {SECP_256_OID, SECP_256_R_1, "NIST P-256", "X9.62 prime256v1"};
        String oid = getCurveOid(key);
        return Arrays.asList(secp256r1Names).contains(oid);
    }

    public static boolean isECSecp384r1(KeyPair keyPair) throws GeneralSecurityException {

        if (!isEcKeyAlgorithm(keyPair.getPrivate().getAlgorithm(),
            keyPair.getPublic().getAlgorithm())) {
            return false;
        }

        ECPublicKey ecPublicKey = (ECPublicKey)keyPair.getPublic();
        if (keyPair.getPrivate() instanceof ECKey ecKey) {
            return isValidSecP384R1(ecPublicKey) && isEcSecp384r1Curve(ecKey);
        } else {
            return isValidSecP384R1(ecPublicKey)
                && Crypto.isECPKCS11Key(keyPair.getPrivate()); //can't get curve for PKCS11 keys
        }
    }

    public static boolean isECSecp256r1(KeyPair keyPair) throws GeneralSecurityException {
        if (!isEcKeyAlgorithm(keyPair.getPrivate().getAlgorithm(),
            keyPair.getPublic().getAlgorithm())) {
            return false;
        }

        ECPublicKey ecPublicKey = (ECPublicKey)keyPair.getPublic();
        if (keyPair.getPrivate() instanceof ECKey ecKey) {
            return isValidSecP256R1(ecPublicKey) && isEcSecp256r1Curve(ecKey);
        } else {
            return isValidSecP256R1(ecPublicKey)
                && Crypto.isECPKCS11Key(keyPair.getPrivate()); //can't get curve for PKCS11 keys
        }
    }

    public static boolean isValidSecP384R1(ECPublicKey ecPublicKey) throws GeneralSecurityException {
        if (ecPublicKey == null) {
            log.debug("EC pub key is null");
            return false;
        }

        // it is not possible to create other instance of ECPoint.POINT_INFINITY
        if (ECPoint.POINT_INFINITY.equals(ecPublicKey.getW())) {
            log.debug("EC pub key is infinity");
            return false;
        }

        if (!isEcSecp384r1Curve(ecPublicKey)) {
            if (log.isDebugEnabled()) {
                log.debug("EC pub key curve OID {} is not secp384r1", getCurveOid(ecPublicKey));
            }
            return false;
        }

        // https://neilmadden.blog/2017/05/17/so-how-do-you-validate-nist-ecdh-public-keys/
        // Instead of implementing public key validation, rely on BC validation
        // https://github.com/bcgit/bc-java/blob/master/core/src/main/java/org/bouncycastle/math/ec/ECPoint.java
        org.bouncycastle.math.ec.ECPoint ecPoint = SECP_384_R_1_CURVE.createPoint(ecPublicKey.getW().getAffineX(),
            ecPublicKey.getW().getAffineY());

        boolean onCurve = ecPoint.isValid();
        if (!onCurve) {
            log.debug("EC pub key is not on secp384r1 curve");
        }
        return onCurve;
    }

    public static boolean isValidSecP256R1(ECPublicKey ecPublicKey) throws GeneralSecurityException {
        if (ecPublicKey == null) {
            log.debug("EC pub key is null");
            return false;
        }

        // it is not possible to create other instance of ECPoint.POINT_INFINITY
        if (ECPoint.POINT_INFINITY.equals(ecPublicKey.getW())) {
            log.debug("EC pub key is infinity");
            return false;
        }

        if (!isEcSecp256r1Curve(ecPublicKey)) {
            if (log.isDebugEnabled()) {
                log.debug("EC pub key curve OID {} is not secp256r1", getCurveOid(ecPublicKey));
            }
            return false;
        }

        // https://neilmadden.blog/2017/05/17/so-how-do-you-validate-nist-ecdh-public-keys/
        // Instead of implementing public key validation, rely on BC validation
        // https://github.com/bcgit/bc-java/blob/master/core/src/main/java/org/bouncycastle/math/ec/ECPoint.java
        org.bouncycastle.math.ec.ECPoint ecPoint = SECP_256_R_1_CURVE.createPoint(ecPublicKey.getW().getAffineX(),
            ecPublicKey.getW().getAffineY());

        boolean onCurve = ecPoint.isValid();
        if (!onCurve) {
            log.debug("EC pub key is not on secp256r1 curve");
        }
        return onCurve;
    }

    private static boolean isEcKeyAlgorithm(String privateKeyAlgorithm, String publicKeyAlgorithm) {
        if (!KeyAlgorithm.isEcKeysAlgorithm(privateKeyAlgorithm)) {
            log.debug("Not EC key pair. Algorithm is {} (expected EC)", privateKeyAlgorithm);
            return false;
        }
        if (!KeyAlgorithm.isEcKeysAlgorithm(publicKeyAlgorithm)) {
            log.debug("Not EC key pair. Algorithm is {} (expected EC)", publicKeyAlgorithm);
            return false;
        }
        return true;
    }

}