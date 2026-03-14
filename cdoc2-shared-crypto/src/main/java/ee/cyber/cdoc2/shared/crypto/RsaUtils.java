package ee.cyber.cdoc2.shared.crypto;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERSequence;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Objects;

public final class RsaUtils {
    private RsaUtils() { }

    /**
     * Encode RSA public key as in RFC8017 RSA Public Key Syntax (A.1.1) https://www.rfc-editor.org/rfc/rfc8017#page-54
     * <pre>
     *           RSAPublicKey ::= SEQUENCE {
     *              modulus           INTEGER,  -- n
     *              publicExponent    INTEGER   -- e
     *          }
     * </pre>
     * See RsaTest.java for examples
     * @return rsaPublicKey encoded as ASN1 RSAPublicKey
     */
    public static byte[] encodeRsaPubKey(RSAPublicKey rsaPublicKey) {
        Objects.requireNonNull(rsaPublicKey);

        ASN1EncodableVector v = new ASN1EncodableVector(2);
        v.add(new ASN1Integer(rsaPublicKey.getModulus()));
        v.add(new ASN1Integer(rsaPublicKey.getPublicExponent()));
        DERSequence derSequence = new DERSequence(v);
        try {
            return derSequence.getEncoded();
        } catch (IOException io) {
            // getEncoded uses internally ByteArrayOutputStream that shouldn't throw IOException
            throw new IllegalStateException("Failed to encode rsaPublicKey", io);
        }
    }

    /**
     * Decode RSA public key from byte stream as defined in
     * RFC8017 RSA Public Key Syntax (A.1.1) https://www.rfc-editor.org/rfc/rfc8017#page-54
     * <pre>
     *           RSAPublicKey ::= SEQUENCE {
     *              modulus           INTEGER,  -- n
     *              publicExponent    INTEGER   -- e
     *          }
     * </pre>
     * @param asn1Data asn1 sequence containing RSAPublicKey structure
     * @return decoded RSA public key
     * @throws IOException if decoding fails
     * @throws GeneralSecurityException if converting ASN1 data to RSA public key fails
     * see RsaTest.java for examples
     */
    public static RSAPublicKey decodeRsaPubKey(byte[] asn1Data) throws IOException, GeneralSecurityException {

        ASN1Primitive p = ASN1Primitive.fromByteArray(asn1Data);
        ASN1Sequence asn1Sequence = ASN1Sequence.getInstance(p);

        if (asn1Sequence.size() != 2) {
            throw new IOException("Bad sequence size: " + asn1Sequence.size());
        }

        ASN1Integer mod = ASN1Integer.getInstance(asn1Sequence.getObjectAt(0));
        ASN1Integer exp = ASN1Integer.getInstance(asn1Sequence.getObjectAt(1));

        RSAPublicKeySpec spec = new RSAPublicKeySpec(mod.getPositiveValue(), exp.getPositiveValue());
        KeyFactory keyFactory = KeyFactory.getInstance(KeyAlgorithm.Algorithm.RSA.name());
        return (RSAPublicKey) keyFactory.generatePublic(spec);
    }

}
