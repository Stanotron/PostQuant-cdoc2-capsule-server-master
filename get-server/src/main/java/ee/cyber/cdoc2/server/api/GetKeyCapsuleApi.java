package ee.cyber.cdoc2.server.api;

import ee.cyber.cdoc2.server.Constants;
import ee.cyber.cdoc2.server.model.db.KeyCapsuleDb;
import ee.cyber.cdoc2.server.model.db.KeyCapsuleRepository;
import ee.cyber.cdoc2.server.generated.model.Capsule;
import ee.cyber.cdoc2.server.generated.api.KeyCapsulesApi;
import ee.cyber.cdoc2.server.generated.api.KeyCapsulesApiDelegate;

import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.security.auth.x500.X500Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.NativeWebRequest;
import ee.cyber.cdoc2.server.MlDsaTools;


import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;

import java.time.format.DateTimeParseException;
import java.util.Base64;




/**
 * Implements API for getting CDOC2 key capsules {@link KeyCapsulesApi}
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GetKeyCapsuleApi implements KeyCapsulesApiDelegate {

    private final NativeWebRequest nativeWebRequest;
    private final KeyCapsuleRepository capsuleRepository;

    @Override
    public Optional<NativeWebRequest> getRequest() {
        return Optional.of(this.nativeWebRequest);
    }

//    @Override
//    public ResponseEntity<Capsule> getCapsuleByTransactionId(String transactionId) {
//        var clientCertOpt = this.getClientCertFromRequest();
//        if (clientCertOpt.isEmpty()) {
//            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
//        }
//        var clientCert = clientCertOpt.get();
//        PublicKey clientPubKey = clientCert.getPublicKey();
//
//        Optional<KeyCapsuleDb> capsuleDbOpt = this.capsuleRepository.findById(transactionId);
//        if (capsuleDbOpt.isEmpty()) {
//            log.debug("Capsule with transactionId {} not found", transactionId);
//            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
//        }
//
//        var capsule = capsuleDbOpt.get();
//        if (isRecipient(clientPubKey, capsule)) {
//            log.info("Found capsule(transaction={}) for client certificate", transactionId);
//            return ResponseEntity.ok()
//                //return expiry-time as in RFC3339, example  2025-03-18T14:23:45.123Z
//                .header(Constants.X_EXPIRY_TIME_HEADER, DateTimeFormatter.ISO_INSTANT.format(capsule.getExpiryTime()))
//                .header(Constants.X_EXPIRY_TIME_ADJUSTED, String.valueOf(capsule.getExpiryTimeAdjusted()))
//                .body(toDto(capsule));
//        } else {
//            log.info("Client certificate does not match capsule(transactionId={}) recipient", transactionId);
//            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
//        }
//    }

    @Override
    public ResponseEntity<Capsule> getCapsuleByTransactionId(
            String transactionId,
            String xRecipientId,
            String xTimestamp,
            String xSignature
    ) {                                                                             // pq change

        String recipientIdHeader = xRecipientId;
        String timestampHeader = xTimestamp;
        String signatureHeader = xSignature;

        if (recipientIdHeader == null || timestampHeader == null || signatureHeader == null) {
            log.info("Missing required PQ auth headers for transactionId={}", transactionId);
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        Optional<KeyCapsuleDb> capsuleDbOpt = this.capsuleRepository.findById(transactionId);
        if (capsuleDbOpt.isEmpty()) {
            log.debug("Capsule with transactionId {} not found", transactionId);
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        var capsule = capsuleDbOpt.get();

        if (!isRecipientIdMatch(recipientIdHeader, capsule)) {
            log.info("RecipientId header does not match capsule(transactionId={}) recipient", transactionId);
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        if (!isTimestampFresh(timestampHeader)) {
            log.info("Timestamp is stale or invalid for capsule(transactionId={})", transactionId);
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        if (!isSignatureValid(transactionId, recipientIdHeader, timestampHeader, signatureHeader, capsule)) {
            log.info("Signature validation failed for capsule(transactionId={})", transactionId);
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        log.info("Found capsule(transaction={}) for PQ-authenticated request", transactionId);
        return ResponseEntity.ok()
                .header(Constants.X_EXPIRY_TIME_HEADER, DateTimeFormatter.ISO_INSTANT.format(capsule.getExpiryTime()))
                .header(Constants.X_EXPIRY_TIME_ADJUSTED, String.valueOf(capsule.getExpiryTimeAdjusted()))
                .body(toDto(capsule));
    }

    @Override
    public ResponseEntity<Void> createCapsule(Capsule capsule, LocalDateTime xExpiryTime) {
        log.error("createCapsule() operation not supported on key capsule get server");
        return new ResponseEntity<>(HttpStatus.METHOD_NOT_ALLOWED);
    }



    private static boolean isRecipientIdMatch(String recipientIdHeader, KeyCapsuleDb capsule) {
        try {
            byte[] requestRecipientId = Base64.getDecoder().decode(recipientIdHeader);
            return java.util.Arrays.equals(requestRecipientId, capsule.getRecipient());
        } catch (IllegalArgumentException e) {
            log.error("Failed to decode X-Recipient-Id header", e);
            return false;
        }
    }

    private static boolean isTimestampFresh(String timestampHeader) {
        try {
            Instant requestTime = OffsetDateTime.parse(timestampHeader).toInstant();
            Instant now = Instant.now();
            long ageSeconds = Math.abs(Duration.between(requestTime, now).getSeconds());
            return ageSeconds <= 300;
        } catch (DateTimeParseException e) {
            log.error("Failed to parse X-Timestamp header", e);
            return false;
        }
    }

    private static boolean isSignatureValid(
            String transactionId,
            String recipientIdHeader,
            String timestampHeader,
            String signatureHeader,
            KeyCapsuleDb capsule
    ) {
        try {
            if (capsule.getRecipientMldsaPublicKey() == null || capsule.getRecipientMldsaPublicKey().length == 0) {
                log.error("Capsule transactionId={} does not contain recipient ML-DSA public key", transactionId);
                return false;
            }

            String signedMessage =
                    transactionId + "|" + recipientIdHeader + "|" + timestampHeader;

            byte[] signature = Base64.getDecoder().decode(signatureHeader);

            boolean verified = MlDsaTools.verifyMlDsaSignature(
                    capsule.getRecipientMldsaPublicKey(),
                    signedMessage,
                    signature
            );

            log.info(
                    "PQ signature verification transactionId={} verified={} signatureLen={} publicKeyLen={}",
                    transactionId,
                    verified,
                    signature.length,
                    capsule.getRecipientMldsaPublicKey().length
            );

            return verified;

        } catch (IllegalArgumentException e) {
            log.error("Failed to decode X-Signature header", e);
            return false;
        } catch (GeneralSecurityException e) {
            log.error("Failed to verify ML-DSA signature for transactionId={}", transactionId, e);
            return false;
        }
    }


//    private static boolean isRecipient(PublicKey publicKey, KeyCapsuleDb capsule) {             // pq change
//        try {
//            if (KeyAlgorithm.isEcKeysAlgorithm(publicKey.getAlgorithm())
//                    && publicKey instanceof ECPublicKey ecPublicKey) {
//
//                KeyCapsuleDb.CapsuleType type = capsule.getCapsuleType();
//
//                boolean ecAuthCapsule =
//                        type == KeyCapsuleDb.CapsuleType.SECP384R1
//                                || type == KeyCapsuleDb.CapsuleType.SECP256R1
//                                || type == KeyCapsuleDb.CapsuleType.MLKEM768;
//
//                if (ecAuthCapsule) {
//                    return Arrays.equals(
//                            capsule.getRecipient(),
//                            ECKeys.encodeEcPubKeyForTls(ecPublicKey)
//                    );
//                }
//            }
//
//            if (capsule.getCapsuleType() == KeyCapsuleDb.CapsuleType.RSA
//                    && KeyAlgorithm.isRsaKeysAlgorithm(publicKey.getAlgorithm())) {
//                return Arrays.equals(capsule.getRecipient(), RsaUtils.encodeRsaPubKey((RSAPublicKey) publicKey));
//            }
//        } catch (GeneralSecurityException exc) {
//            log.error("Error occurred while verifying recipient", exc);
//        }
//        return false;
//    }

    private static Capsule toDto(KeyCapsuleDb db) {
        var dto = new Capsule();
        dto.setRecipientId(db.getRecipient());
        dto.setRecipientMldsaPublicKey(db.getRecipientMldsaPublicKey());
        dto.setEphemeralKeyMaterial(db.getPayload());

        switch (db.getCapsuleType()) {
            case SECP384R1:
                dto.setCapsuleType(Capsule.CapsuleTypeEnum.ECC_SECP384R1);
                break;
            case SECP256R1:
                dto.setCapsuleType(Capsule.CapsuleTypeEnum.ECC_SECP256R1);
                break;
            case RSA:
                dto.setCapsuleType(Capsule.CapsuleTypeEnum.RSA);
                break;
            case MLKEM768:                                                                      // pq change
                dto.setCapsuleType(Capsule.CapsuleTypeEnum.MLKEM768);
                break;
            default:
                throw new IllegalArgumentException("Unsupported capsule type: " + db.getCapsuleType());
        }
        return dto;
    }

//    private Optional<X509Certificate> getClientCertFromRequest() {
//        HttpServletRequest req = this.nativeWebRequest.getNativeRequest(HttpServletRequest.class);
//        X509Certificate[] certs = (req != null)
//                ? (X509Certificate[]) req.getAttribute("jakarta.servlet.request.X509Certificate")
//                : new X509Certificate[0];
//        if (null == certs) {
//            String errorMsg = "Failed to get client certificate from http request";
//            log.error(errorMsg);
//            throw new IllegalArgumentException(errorMsg);
//        }
//
//        if (certs.length > 0) {
//            var clientCert = certs[0];
//            log.info("Got client certificate(subject='{}')", getCertSubjectNameWithoutCN(clientCert));
//            return Optional.of(clientCert);
//        } else {
//            log.info("No client certificate in http request");
//            return Optional.empty();
//        }
//    }

    public static String getCertSubjectNameWithoutCN(X509Certificate certificate) {
        return Optional.ofNullable(certificate.getSubjectX500Principal())
            .map(X500Principal::getName)
            // Remove the Common name from logs for privacy, it can contain name and id code
            .map(GetKeyCapsuleApi::removeCN)
            .orElse("");
    }

    private static String removeCN(String distinguishedName) {
        try {
            LdapName ldapName = new LdapName(distinguishedName);
            List<Rdn> rdns = ldapName.getRdns();

            List<Rdn> filteredRdns = rdns.stream()
                .filter(rdn -> !rdn.getType().equalsIgnoreCase("CN"))
                .collect(Collectors.toList());

            LdapName result = new LdapName(filteredRdns);
            return result.toString();
        } catch (InvalidNameException e) {
            // If parsing fails, return empty string
            return "";
        }
    }

}
