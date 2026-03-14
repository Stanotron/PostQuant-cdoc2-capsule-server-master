package ee.cyber.cdoc2.shared.crypto;

import java.security.NoSuchAlgorithmException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Elliptic curve enums mapped to known elliptic curve names and oid's
 */
public enum EllipticCurve {

    UNKNOWN(null, null),
    SECP384R1(ECKeys.SECP_384_R_1, ECKeys.SECP_384_OID),
    SECP256R1(ECKeys.SECP_256_R_1, ECKeys.SECP_256_OID);

    private static final Logger log = LoggerFactory.getLogger(EllipticCurve.class);

    private final String name;
    private final String oid;

    EllipticCurve(String name, String oid) {
        this.name = name;
        this.oid = oid;
    }

    public String getName() {
        return name;
    }

    /**
     * Key length in bytes. For secp384r1, its 384/8=48
     */
    public int getKeyLength() {
        return switch (this) {
            case SECP384R1 -> ECKeys.SECP_384_R_1_LEN_BYTES;
            case SECP256R1 -> ECKeys.SECP_256_R_1_LEN_BYTES;
            default -> throw new IllegalStateException("getKeyLength not implemented for " + this);
        };
    }

    public static EllipticCurve forOid(String oid) throws NoSuchAlgorithmException {
        return switch (oid) {
            case ECKeys.SECP_384_OID -> SECP384R1;
            case ECKeys.SECP_256_OID -> SECP256R1;
            default -> throw new NoSuchAlgorithmException("Unknown EC curve oid " + oid);
        };
    }

}
