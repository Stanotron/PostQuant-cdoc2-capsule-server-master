package ee.cyber.cdoc2.server;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import ee.cyber.cdoc2.crypto.ECKeys;
import ee.cyber.cdoc2.crypto.EllipticCurve;
import ee.cyber.cdoc2.crypto.PemTools;
import ee.cyber.cdoc2.crypto.RsaUtils;
import ee.cyber.cdoc2.server.api.CreateKeyCapsuleApi;
import ee.cyber.cdoc2.server.generated.model.Capsule;
import ee.cyber.cdoc2.server.config.KeyCapsuleConfigProperties;
import ee.cyber.cdoc2.server.model.db.KeyCapsuleDb;

import static ee.cyber.cdoc2.server.Utils.getCapsuleExpirationTime;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
class CreateKeyCapsuleApiTest extends KeyCapsuleIntegrationTest {

    @Autowired
    private CreateKeyCapsuleApi api;

    @Autowired
    private KeyCapsuleConfigProperties configProperties;

    @BeforeEach
    public void setup() {
        this.capsuleRepository.deleteAll();
    }


    @Test
    void shouldCreateCapsuleWithDefaultExpiryTime() throws Exception {
        var rsaCapsule = getRsaCapsule();

        assertCapsuleCreateRequestIsSuccessful(rsaCapsule, null);

        KeyCapsuleDb savedCapsule = assertCapsuleCreatedInDb(rsaCapsule);
        Instant expiryTime = savedCapsule.getExpiryTime();
        assertNotNull(expiryTime);

        Instant expiryDate = expiryTime.truncatedTo(ChronoUnit.DAYS);

        Instant expectedInstant
            = getCapsuleExpirationTime(configProperties.defaultExpirationDuration()).toInstant();
        Instant expectedExpiryDate = expectedInstant.truncatedTo(ChronoUnit.DAYS);

        assertEquals(expectedExpiryDate, expiryDate);
    }

    @Test
    void shouldCreateCapsuleWithRequestExpiryTime() throws Exception {
        var ecCapsule = getEcCapsule();
        LocalDateTime requestExpiryTime = LocalDateTime.now().minusMonths(1);

        assertCapsuleCreateRequestIsSuccessful(ecCapsule, requestExpiryTime);

        KeyCapsuleDb savedCapsule = assertCapsuleCreatedInDb(ecCapsule);

        Instant responseExpiryTime = savedCapsule
            .getExpiryTime()
            .truncatedTo(ChronoUnit.MINUTES);

        Instant expectedExpiryDate = requestExpiryTime.toInstant(ZoneOffset.UTC)
            .truncatedTo(ChronoUnit.MINUTES);

        assertEquals(expectedExpiryDate, responseExpiryTime);
    }

    @Test
    void shouldCreateCapsuleWithMaxExpiryTimeWhenToLongTimeIsRequested() throws Exception {
        var capsule = getEcCapsule();

        LocalDateTime expiryTime = LocalDateTime.now().plusYears(10);

        api.createCapsule(capsule, expiryTime);

        assertMaxExpiryTimeIsAssignedToCapsule(capsule);
    }

    @Test
    void shouldFailToCreateRsaCapsuleWithOverloadedExpiryTime() throws Exception {
        var rsaCapsule = getRsaCapsule();

        LocalDateTime expiryTime = LocalDateTime.now().plusYears(10);

        api.createCapsule(rsaCapsule, expiryTime);

        assertMaxExpiryTimeIsAssignedToCapsule(rsaCapsule);
    }

    private Capsule getRsaCapsule() throws Exception {
        var recipientCert = PemTools.loadCertificate(
            new ByteArrayInputStream(
                Files.readAllBytes(TestData.getKeysDirectory().resolve("rsa/client-rsa-2048-cert.pem")
                    .toAbsolutePath())
            )
        );

        return new Capsule()
            .capsuleType(Capsule.CapsuleTypeEnum.RSA)
            .ephemeralKeyMaterial(UUID.randomUUID().toString().getBytes())
            .recipientId(RsaUtils.encodeRsaPubKey((RSAPublicKey) recipientCert.getPublicKey()));
    }

    private Capsule getEcCapsule() throws Exception {
        var capsule = new Capsule()
            .capsuleType(Capsule.CapsuleTypeEnum.ECC_SECP384R1);

        // Recipient public key TLS encoded and base64 encoded from client-certificate.pem
        File[] certs = {TestData.getKeysDirectory().resolve("ca_certs/client-certificate.pem").toFile()};
        ECPublicKey recipientKey = ECKeys.loadCertKeys(certs).get(0);
        EllipticCurve curve = EllipticCurve.forPubKey(recipientKey);
        capsule.recipientId(ECKeys.encodeEcPubKeyForTls(curve, recipientKey));

        // Sender public key
        KeyPair senderKeyPair = curve.generateEcKeyPair();
        ECPublicKey senderPubKey = (ECPublicKey) senderKeyPair.getPublic();
        capsule.ephemeralKeyMaterial(ECKeys.encodeEcPubKeyForTls(senderPubKey));

        return capsule;
    }

    private void assertCapsuleCreateRequestIsSuccessful(
        Capsule rsaCapsule, LocalDateTime requestExpiryTime
    ) {
        ResponseEntity<Void> response
            = api.createCapsule(rsaCapsule, requestExpiryTime);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertTrue(response.getHeaders().containsKey(Constants.X_EXPIRY_TIME_HEADER));

        log.debug("POST expiry-time {}", response.getHeaders().get(Constants.X_EXPIRY_TIME_HEADER));
        //no exception means that x-expiry-time is formatted correctly
        DateTimeFormatter.ISO_INSTANT.parse(response.getHeaders().get(Constants.X_EXPIRY_TIME_HEADER).get(0));
    }

    private KeyCapsuleDb assertCapsuleCreatedInDb(Capsule capsule) {
        Optional<KeyCapsuleDb> retrievedOpt
            = this.capsuleRepository.findByRecipient(capsule.getRecipientId());
        assertTrue(retrievedOpt.isPresent());
        return retrievedOpt.get();
    }

    private void assertMaxExpiryTimeIsAssignedToCapsule(Capsule capsule) {
        KeyCapsuleDb savedCapsule = assertCapsuleCreatedInDb(capsule);
        Instant savedExpiryTime = savedCapsule.getExpiryTime();
        assertNotNull(savedExpiryTime);

        Instant savedExpiryDate = savedExpiryTime.truncatedTo(ChronoUnit.DAYS);

        Instant expectedInstant
            = getCapsuleExpirationTime(configProperties.maxExpirationDuration()).toInstant();
        Instant expectedExpiryDate = expectedInstant.truncatedTo(ChronoUnit.DAYS);

        assertEquals(expectedExpiryDate, savedExpiryDate);
    }
}
