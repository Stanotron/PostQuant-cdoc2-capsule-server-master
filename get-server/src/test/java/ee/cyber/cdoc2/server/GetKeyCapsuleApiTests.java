package ee.cyber.cdoc2.server;

import ee.cyber.cdoc2.UserErrorCode;
import ee.cyber.cdoc2.client.Cdoc2KeyCapsuleApiClient;
import ee.cyber.cdoc2.client.EcCapsuleClientImpl;
import ee.cyber.cdoc2.client.KeyCapsuleClientImpl;
import ee.cyber.cdoc2.client.RsaCapsuleClientImpl;
import ee.cyber.cdoc2.config.KeyCapsuleClientConfiguration;
import ee.cyber.cdoc2.exceptions.CDocUserException;
import ee.cyber.cdoc2.server.api.GetKeyCapsuleApi;
import ee.cyber.cdoc2.server.generated.model.Capsule;
import ee.cyber.cdoc2.crypto.Crypto;
import ee.cyber.cdoc2.crypto.ECKeys;
import ee.cyber.cdoc2.crypto.EllipticCurve;
import ee.cyber.cdoc2.crypto.PemTools;
import ee.cyber.cdoc2.crypto.Pkcs11DeviceConfiguration;
import ee.cyber.cdoc2.crypto.Pkcs11Tools;
import ee.cyber.cdoc2.crypto.RsaUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collections;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

import javax.naming.ldap.LdapName;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;

import static org.junit.jupiter.api.Assertions.*;


@Slf4j
class GetKeyCapsuleApiTests extends KeyCapsuleIntegrationTest {

    // read hardware PKCS11 device conf from a properties file
    private Pkcs11DeviceConfiguration pkcs11Conf = Pkcs11DeviceConfiguration.load();

    // rest client with client auth using keystore rsa/client-rsa-2048.p12
    @Qualifier("trustAllWithClientAuth")
    @Autowired
    private RestClient restClient;

    @Test
    void testPKCS12Client() throws Exception {
        Cdoc2KeyCapsuleApiClient client = createClientWithPkcs12();

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

        String id = this.saveCapsule(capsule, EXPIRY_TIME).getTransactionId();

        assertNotNull(id);

        var serverCapsule = client.getCapsule(id);

        assertTrue(serverCapsule.isPresent());

        // comparing client.model.Capsule and server.model.Capsule.
        //Since these are different classes, can't do simple equals, although json is the same
        assertArrayEquals(capsule.getRecipientId(), serverCapsule.get().getRecipientId());
        assertArrayEquals(capsule.getEphemeralKeyMaterial(), serverCapsule.get().getEphemeralKeyMaterial());
        assertEquals(String.valueOf(capsule.getCapsuleType()), String.valueOf(serverCapsule.get().getCapsuleType()));
    }

    @Test
    void testKeyServerPropertiesClientPKCS12() throws Exception {
        var client = createPkcs12ServerClient(baseUrl);

        X509Certificate cert = (X509Certificate) client.getClientCertificate();

        assertNotNull(cert);

        //recipientPubKey must match with pub key in mutual TLS
        ECPublicKey recipientPubKey = (ECPublicKey) cert.getPublicKey();

        ECPublicKey senderPubKey = (ECPublicKey) EllipticCurve.SECP384R1.generateEcKeyPair().getPublic();

        log.debug("Sender pub key: {}",
            Base64.getEncoder().encodeToString(
                ECKeys.encodeEcPubKeyForTls(EllipticCurve.SECP384R1, senderPubKey)
            )
        );
        assertNotNull(client.getServerIdentifier());

        var curve = EllipticCurve.forPubKey(recipientPubKey);

        var capsule = new Capsule()
            .ephemeralKeyMaterial(ECKeys.encodeEcPubKeyForTls(senderPubKey))
            .recipientId(ECKeys.encodeEcPubKeyForTls(curve, recipientPubKey))
            .capsuleType(Capsule.CapsuleTypeEnum.ECC_SECP384R1);

        String transactionID = this.saveCapsule(capsule, EXPIRY_TIME).getTransactionId();

        assertNotNull(transactionID);

        Optional<ECPublicKey> serverSenderKey = new EcCapsuleClientImpl(client).getSenderKey(transactionID);
        assertTrue(serverSenderKey.isPresent());
        assertEquals(senderPubKey, serverSenderKey.get());
    }

    @Test
    void testKeyCapsulesClientImplRsaPKCS12() throws Exception {
        String prop = "cdoc2.client.server.id=testKeyCapsulesClientImplRsaPKCS12\n";
        prop += "cdoc2.client.server.base-url.post=" + baseUrl + "\n";
        prop += "cdoc2.client.server.base-url.get=" + baseUrl + "\n";
        prop += "cdoc2.client.ssl.trust-store.type=JKS\n";
        prop += "cdoc2.client.ssl.trust-store="
            + TestData.getKeysDirectory().resolve("clienttruststore.jks") + "\n";
        prop += "cdoc2.client.ssl.trust-store-password=passwd\n";

        prop += "cdoc2.client.ssl.client-store.type=PKCS12\n";
        prop += "cdoc2.client.ssl.client-store="
            + TestData.getKeysDirectory().resolve("rsa/client-rsa-2048.p12") + "\n";
        prop += "cdoc2.client.ssl.client-store-password=passwd\n";

        prop = prop.replace("\\", "\\\\");

        Properties p = new Properties();
        p.load(new StringReader(prop));

        var config = KeyCapsuleClientConfiguration.load(p);
        KeyCapsuleClientImpl client = (KeyCapsuleClientImpl) KeyCapsuleClientImpl.create(config);

        X509Certificate cert = (X509Certificate) client.getClientCertificate();
        assertNotNull(cert);
        RSAPublicKey senderPubKey = (RSAPublicKey) cert.getPublicKey();

        RSAPublicKey rsaPublicKey = (RSAPublicKey) cert.getPublicKey();
        byte[] kek = new byte[Crypto.FMK_LEN_BYTES];
        Crypto.getSecureRandom().nextBytes(kek);
        byte[] encryptedKek = RsaUtils.rsaEncrypt(kek, rsaPublicKey);

        var capsule = new Capsule()
            .ephemeralKeyMaterial(encryptedKek)
            .recipientId(RsaUtils.encodeRsaPubKey(senderPubKey))
            .capsuleType(Capsule.CapsuleTypeEnum.RSA);

        String transactionID = this.saveCapsule(capsule, EXPIRY_TIME).getTransactionId();

        assertNotNull(transactionID);

        Optional<byte[]> serverEncKek = new RsaCapsuleClientImpl(client).getEncryptedKek(transactionID);

        assertTrue(serverEncKek.isPresent());
        assertArrayEquals(encryptedKek, serverEncKek.get());
    }

    @Test
    @Tag("pkcs11")
    void testKeyServerPropertiesClientPKCS11Passwd() throws Exception {
        testKeyServerPropertiesClientPKCS11(false);
    }

    @Test
    @Tag("pkcs11")
    @Disabled("Requires user interaction. Needs to be run separately from other PKCS11 tests as SunPKCS11 caches "
        + "passwords ")
    void testKeyServerPropertiesClientPKCS11Prompt() throws Exception {
        if (System.console() == null) {
            //SpringBootTest sets headless to true and causes graphic dialog to fail, when running inside IDE
            System.setProperty("java.awt.headless", "false");
        }

        testKeyServerPropertiesClientPKCS11(true);
    }

    void testKeyServerPropertiesClientPKCS11(boolean interactive) throws Exception {
        String prop = "cdoc2.client.server.id=testKeyServerPropertiesClientPKCS11\n";
        prop += "cdoc2.client.server.base-url.post=" + baseUrl + "\n";
        prop += "cdoc2.client.server.base-url.get=" + baseUrl + "\n";
        prop += "cdoc2.client.ssl.trust-store.type=JKS\n";
        prop += "cdoc2.client.ssl.trust-store=" + TestData.getKeysDirectory().resolve("clienttruststore.jks") + "\n";
        prop += "cdoc2.client.ssl.trust-store-password=passwd\n";
        prop += "pkcs11-library=" + pkcs11Conf.pkcs11Library() + "\n";

        prop += "cdoc2.client.ssl.client-store.type=PKCS11\n";

        if (interactive) {
            prop += "cdoc2.client.ssl.client-store-password.prompt=PIN1\n";
        } else {
            prop += "cdoc2.client.ssl.client-store-password=" + new String(pkcs11Conf.pin()) + "\n";
        }

        prop = prop.replace("\\", "\\\\");

        Properties p = new Properties();
        p.load(new StringReader(prop));

        var config = KeyCapsuleClientConfiguration.load(p);
        KeyCapsuleClientImpl client = (KeyCapsuleClientImpl) KeyCapsuleClientImpl.create(config);

        KeyPair senderKeyPair = EllipticCurve.SECP384R1.generateEcKeyPair();
        ECPublicKey senderPubKey = (ECPublicKey) senderKeyPair.getPublic();

        // Storing clientKeyStore in KeyServerPropertiesClient is a bit of hack for tests.
        // It's required to get recipient pub key
        // normally recipient certificate would come from LDAP, but for test-id card certs are not in LDAP
        X509Certificate cert  = (X509Certificate) client.getClientCertificate(pkcs11Conf.keyAlias());
        assertNotNull(cert);
        // Client public key TLS encoded binary base64 encoded
        PublicKey recipientPubKey = cert.getPublicKey();

        if (recipientPubKey instanceof ECPublicKey pubKey) {
            var curve = EllipticCurve.forPubKey(recipientPubKey);
            var capsule = new Capsule()
                .ephemeralKeyMaterial(ECKeys.encodeEcPubKeyForTls(senderPubKey))
                .recipientId(ECKeys.encodeEcPubKeyForTls(curve, pubKey))
                .capsuleType(Capsule.CapsuleTypeEnum.ECC_SECP384R1);

            String id = this.saveCapsule(capsule, EXPIRY_TIME).getTransactionId();
            assertNotNull(id);

            Optional<ECPublicKey> payload = new EcCapsuleClientImpl(client).getSenderKey(id);
            assertTrue(payload.isPresent());
            assertEquals(senderPubKey, payload.get());
        } else if (recipientPubKey instanceof RSAPublicKey pubKey) {
            var keyMaterial = senderPubKey.getEncoded();
            var capsule = new Capsule()
                .ephemeralKeyMaterial(keyMaterial)
                .recipientId(RsaUtils.encodeRsaPubKey(pubKey))
                .capsuleType(Capsule.CapsuleTypeEnum.RSA);

            String id = this.saveCapsule(capsule, EXPIRY_TIME).getTransactionId();
            assertNotNull(id);

            Optional<byte[]> payload = new RsaCapsuleClientImpl(client).getEncryptedKek(id);
            assertTrue(payload.isPresent());
            assertArrayEquals(keyMaterial, payload.get());
        } else {
            throw new RuntimeException("Unsupported PKCS11 public key type: " + recipientPubKey.getClass());
        }
    }

    @Test
    @Tag("pkcs11")
    void testPKCS11Client() throws Exception {

        //PIN1 for 37101010021 test id-kaart
        var protectionParameter = new KeyStore.PasswordProtection(pkcs11Conf.pin());

        //Or ask pin interactively
        @SuppressWarnings("java:S125")
        //KeyStore.ProtectionParameter protectionParameter = getKeyStoreCallbackProtectionParameter("PIN1");

        KeyStore clientKeyStore = null;
        KeyStore trustKeyStore = null;
        try {
            clientKeyStore = Pkcs11Tools.initPKCS11KeysStore(
                pkcs11Conf.pkcs11Library(),
                pkcs11Conf.slot(),
                protectionParameter
            );

            trustKeyStore = KeyStore.getInstance("JKS");
            trustKeyStore.load(Files.newInputStream(TestData.getKeysDirectory().resolve("clienttruststore.jks")),
                "passwd".toCharArray());

        }  catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            log.error("Error initializing key stores", e);
        }

        assertNotNull(clientKeyStore);
        log.debug("aliases: {}", Collections.list(clientKeyStore.aliases()));


        X509Certificate cert  = (X509Certificate) clientKeyStore.getCertificate(pkcs11Conf.keyAlias());
        log.debug("Certificate issuer is {}.  This must be in server truststore "
            + "or SSL handshake will fail with cryptic error", cert.getIssuerDN());

        var builder = Cdoc2KeyCapsuleApiClient.builder();
        builder.withBaseUrl(baseUrl);
        builder.withClientKeyStore(clientKeyStore);
        builder.withClientKeyStoreProtectionParameter(protectionParameter);
        builder.withTrustKeyStore(trustKeyStore);

        Cdoc2KeyCapsuleApiClient client = builder.build();

        Capsule capsule = new Capsule();

        KeyPair senderKeyPair = EllipticCurve.SECP384R1.generateEcKeyPair();
        ECPublicKey senderPubKey = (ECPublicKey) senderKeyPair.getPublic();

        PublicKey pubKey = cert.getPublicKey();

        if (pubKey instanceof ECPublicKey publicKey) {
            capsule.capsuleType(Capsule.CapsuleTypeEnum.ECC_SECP384R1);
            // Client public key TLS encoded and base64 encoded from id-kaart
            //recipient must match to client's cert pub key or GET will fail with 404
            capsule.recipientId(ECKeys.encodeEcPubKeyForTls(publicKey));
            capsule.ephemeralKeyMaterial(ECKeys.encodeEcPubKeyForTls(senderPubKey));
        } else if (pubKey instanceof RSAPublicKey publicKey) {
            capsule.capsuleType(Capsule.CapsuleTypeEnum.RSA);
            capsule.recipientId(RsaUtils.encodeRsaPubKey(publicKey));
            capsule.ephemeralKeyMaterial(senderPubKey.getEncoded());
        }

        String id = this.saveCapsule(capsule, EXPIRY_TIME).getTransactionId();

        assertNotNull(id);

        var serverCapsule = client.getCapsule(id);
        assertTrue(serverCapsule.isPresent());

        // comparing client.model.Capsule and server.model.Capsule.
        // Since these are different classes, can't do simple equals
        assertArrayEquals(capsule.getRecipientId(), serverCapsule.get().getRecipientId());
        assertArrayEquals(capsule.getEphemeralKeyMaterial(), serverCapsule.get().getEphemeralKeyMaterial());
        assertEquals(String.valueOf(capsule.getCapsuleType()), String.valueOf(serverCapsule.get().getCapsuleType()));
    }

    @Test
    void shouldGetRsaCapsule() throws Exception {
        var recipientCert = PemTools.loadCertificate(
            new ByteArrayInputStream(
                Files.readAllBytes(TestData.getKeysDirectory().resolve("rsa/client-rsa-2048-cert.pem")
                    .toAbsolutePath())
            )
        );

        var rsaCapsule = new Capsule()
            .capsuleType(Capsule.CapsuleTypeEnum.RSA)
            .ephemeralKeyMaterial(UUID.randomUUID().toString().getBytes())
            .recipientId(RsaUtils.encodeRsaPubKey((RSAPublicKey) recipientCert.getPublicKey()));

        String txId = this.saveCapsule(rsaCapsule, EXPIRY_TIME).getTransactionId();

        ResponseEntity<Capsule> response = this.restClient
            .get()
            .uri(this.capsuleApiUrl() + "/" + txId)
            .retrieve()
            .toEntity(Capsule.class);

        assertTrue(response.getHeaders().containsKey(Constants.X_EXPIRY_TIME_HEADER));
        assertTrue(response.getHeaders().containsKey(Constants.X_EXPIRY_TIME_ADJUSTED));

        log.debug("expiry-time {}", response.getHeaders().get(Constants.X_EXPIRY_TIME_HEADER));
        //no exception means that x-expiry-time is formatted correctly
        DateTimeFormatter.ISO_INSTANT.parse(response.getHeaders().get(Constants.X_EXPIRY_TIME_HEADER).get(0));

        assertNotNull(response);
        assertNotNull(response.getBody());

        assertEquals(rsaCapsule.getCapsuleType(), response.getBody().getCapsuleType());
        assertArrayEquals(rsaCapsule.getRecipientId(), response.getBody().getRecipientId());
        assertArrayEquals(rsaCapsule.getEphemeralKeyMaterial(), response.getBody().getEphemeralKeyMaterial());
    }

    @Test
    void shouldGetHttp400() throws Exception {
        // constraint errors should be converted to HTTP 400, see GlobalExceptionHandler

        String txId = "KC123"; // too short tx
        URI requestUri = new URI(this.capsuleApiUrl() + "/" + txId);

        RestClientResponseException ex = assertThrows(
            RestClientResponseException.class,
            () -> this.restClient
                .get()
                .uri(requestUri)
                .retrieve()
                .toEntity(Capsule.class)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void shouldGetHttp404() throws Exception {
        String txId = "KC12345678901234567890"; // certificate provided and passes validation, but capsule not found
        URI requestUri = new URI(this.capsuleApiUrl() + "/" + txId);
        RestClientResponseException ex = assertThrows(
            RestClientResponseException.class,
            () -> this.restClient
                .get()
                .uri(requestUri)
                .retrieve()
                .toEntity(Capsule.class)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void shouldThrowUserExceptions() throws Exception {
        // unknown serverId should throw exception
        var client = createPkcs12ServerClient(baseUrl);
        String nonExistingUuid = UUID.randomUUID().toString();

        CDocUserException notFoundException = assertThrows(
            CDocUserException.class,
            () -> client.getForId(nonExistingUuid)
        );
        assertEquals(UserErrorCode.SERVER_NOT_FOUND, notFoundException.getErrorCode());

        // test network error
        var misconfiguredClient = createPkcs12ServerClient("https://foo");
        String uuid = UUID.randomUUID().toString();

        CDocUserException networkException = assertThrows(
            CDocUserException.class,
            () -> misconfiguredClient.getCapsule(uuid)
        );
        assertEquals(UserErrorCode.NETWORK_ERROR, networkException.getErrorCode());
    }

    @Test
    void shouldFailWithNotFoundError() throws Exception {
        String txId = "KC12345678901234567890"; // certificate provided and passes validation, but capsule not found

        Cdoc2KeyCapsuleApiClient client = createClientWithPkcs12();

        var response = client.getCapsule(txId);
        assertTrue(response.isEmpty());
    }

    @Test
    void testSubjectCommonNameRemoval() throws Exception {
        // Recipient public key TLS encoded and base64 encoded from client-certificate.pem
        var path = TestData.getKeysDirectory().resolve("ca_certs/client-certificate.pem");

        X509Certificate clientCertificate;
        try (InputStream in = Files.newInputStream(path)) {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            clientCertificate = (X509Certificate) factory.generateCertificate(in);
        }

        String censoredSubjectNameStr = GetKeyCapsuleApi.getCertSubjectNameWithoutCN(clientCertificate);

        String original = clientCertificate.getSubjectX500Principal().getName();
        assertTrue(original.contains("CN="));

        assertFalse(censoredSubjectNameStr.contains("CN="));
        // Check if censoredSubjectNameStr is a valid, if not,
        // then the following code will throw InvalidNameException
        new LdapName(censoredSubjectNameStr);
    }

    private static KeyCapsuleClientImpl createPkcs12ServerClient(String serverBaseUrl) throws Exception {
        String prop = "cdoc2.client.server.id=testKeyServerPropertiesClientPKCS12\n";
        prop += "cdoc2.client.server.base-url.post=" + serverBaseUrl + "\n";
        prop += "cdoc2.client.server.base-url.get=" + serverBaseUrl + "\n";
        prop += "cdoc2.client.ssl.trust-store.type=JKS\n";
        prop += "cdoc2.client.ssl.trust-store=" + TestData.getKeysDirectory().resolve("clienttruststore.jks") + "\n";
        prop += "cdoc2.client.ssl.trust-store-password=passwd\n";

        prop += "cdoc2.client.ssl.client-store.type=PKCS12\n";
        prop += "cdoc2.client.ssl.client-store=" + TestData.getKeysDirectory().resolve("cdoc2client.p12") + "\n";
        prop += "cdoc2.client.ssl.client-store-password=passwd\n";

        prop = prop.replace("\\", "\\\\");

        Properties p = new Properties();
        p.load(new StringReader(prop));

        var config = KeyCapsuleClientConfiguration.load(p);
        return (KeyCapsuleClientImpl) KeyCapsuleClientImpl.create(config);
    }

    private Cdoc2KeyCapsuleApiClient createClientWithPkcs12() throws GeneralSecurityException {
        KeyStore clientKeyStore = null;
        KeyStore trustKeyStore = null;
        try {
            clientKeyStore = KeyStore.getInstance("PKCS12");
            clientKeyStore.load(Files.newInputStream(TestData.getKeysDirectory().resolve("cdoc2client.p12")),
                "passwd".toCharArray());
            clientKeyStore.aliases().asIterator().forEachRemaining(a -> log.debug("client KS alias: {}", a));

            trustKeyStore = KeyStore.getInstance("JKS");
            trustKeyStore.load(Files.newInputStream(TestData.getKeysDirectory().resolve("clienttruststore.jks")),
                "passwd".toCharArray());
            trustKeyStore.aliases().asIterator().forEachRemaining(a -> log.debug("client trust KS alias: {}", a));
        }  catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            log.error("Error initializing key stores", e);
        }


        var builder = Cdoc2KeyCapsuleApiClient.builder();
        builder.withBaseUrl(baseUrl);
        builder.withClientKeyStore(clientKeyStore);
        builder.withClientKeyStorePassword("passwd".toCharArray());
        builder.withTrustKeyStore(trustKeyStore);

        return builder.build();
    }

}
