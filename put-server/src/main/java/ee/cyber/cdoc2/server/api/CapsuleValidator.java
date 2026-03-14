package ee.cyber.cdoc2.server.api;

import ee.cyber.cdoc2.shared.crypto.ECKeys;
import ee.cyber.cdoc2.shared.crypto.RsaUtils;
import java.io.IOException;


import lombok.extern.slf4j.Slf4j;


import java.security.GeneralSecurityException;
import java.security.interfaces.ECPublicKey;

import ee.cyber.cdoc2.server.generated.model.Capsule;

import static ee.cyber.cdoc2.server.generated.model.Capsule.CapsuleTypeEnum.ECC_SECP384R1;

/**
 * Utility class for validating capsules.
 */
@Slf4j
public final class CapsuleValidator {

    private CapsuleValidator() {
        // utility class
    }

    static boolean isValid(Capsule capsule) {
        return switch (capsule.getCapsuleType()) {
            case ECC_SECP384R1, ECC_SECP256R1 -> validateEcCapsule(capsule);
            case RSA -> validateRSACapsule(capsule);
            case MLKEM768 -> validateMlKemCapsule(capsule);                                   // pq change
            default ->
                throw new IllegalArgumentException("Unexpected capsule type: " + capsule.getCapsuleType());
        };
    }

    private static boolean validateMlKemCapsule(Capsule capsule) {                             // pq change
        return capsule.getRecipientId() != null
                && capsule.getRecipientId().length > 0
                && capsule.getEphemeralKeyMaterial() != null
                && capsule.getEphemeralKeyMaterial().length > 0;
    }

    private static boolean validateEcCapsule(Capsule capsule) {

        try {

            var keyLength = switch (capsule.getCapsuleType()) {
                case ECC_SECP384R1 -> ECKeys.SECP_384_R_1_LEN_BYTES;
                case ECC_SECP256R1 -> ECKeys.SECP_256_R_1_LEN_BYTES;
                default -> throw new IllegalArgumentException("Must be a elliptic curve capsule");
            };
            int tlsEncodedKeyLen = 2 * keyLength + 1;

            if (capsule.getRecipientId() == null || capsule.getEphemeralKeyMaterial() == null) {
                log.error("Recipient id or ephemeral key was null");
                return false;
            }
            if (capsule.getRecipientId().length != tlsEncodedKeyLen
                    || capsule.getEphemeralKeyMaterial().length != tlsEncodedKeyLen) {
                log.error("Invalid {} curve key length", capsule.getCapsuleType());
                return false;
            }

            if (capsule.getCapsuleType() == ECC_SECP384R1) {
                ECPublicKey recipientPubKey = ECKeys.decodeSecP384R1EcPublicKeyFromTls(capsule.getRecipientId());
                ECPublicKey senderPubKey = ECKeys.decodeSecP384R1EcPublicKeyFromTls(capsule.getEphemeralKeyMaterial());

                return (ECKeys.isValidSecP384R1(recipientPubKey) && ECKeys.isValidSecP384R1(senderPubKey));
            }
            ECPublicKey recipientPubKey = ECKeys.decodeSecP256R1EcPublicKeyFromTls(capsule.getRecipientId());
            ECPublicKey senderPubKey = ECKeys.decodeSecP256R1EcPublicKeyFromTls(capsule.getEphemeralKeyMaterial());

            return (ECKeys.isValidSecP256R1(recipientPubKey) && ECKeys.isValidSecP256R1(senderPubKey));

        } catch (GeneralSecurityException gse) {
            log.error("Invalid {} EC key", capsule.getCapsuleType(), gse);
        }
        return false;
    }

    private static boolean validateRSACapsule(Capsule capsule) {

        try {
            RsaUtils.decodeRsaPubKey(capsule.getRecipientId());
            return true;
        } catch (GeneralSecurityException | IOException exc) {
            log.error("Failed to parse capsule recipient's RSA public key", exc);
            return false;
        }
    }
}
