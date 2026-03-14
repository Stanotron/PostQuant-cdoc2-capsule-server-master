package ee.cyber.cdoc2.server;

import lombok.SneakyThrows;

import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;


/**
 * Test configuration
 */
@Configuration
public class TestingConfiguration {

    /**
     * @return a REST template that trusts all hosts it connects to
     */
    @Bean(name = "trustAllNoClientAuth")
    @SneakyThrows
    public RestClient getTrustAllRestTemplate() {
        try {
            SSLContext sslContext = SSLContextBuilder
                .create()
                .loadTrustMaterial(new TrustAllStrategy())
                .build();

            final SSLConnectionSocketFactory sslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
                .setSslContext(sslContext)
                .build();
            final HttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
                .setSSLSocketFactory(sslSocketFactory)
                .build();
            var client = HttpClients.custom()
                .setConnectionManager(cm)
                .build();

            return RestClient.builder()
                .requestFactory(new HttpComponentsClientHttpRequestFactory(client))
                .build();
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            throw new GeneralSecurityException("Failed to create SSL context: " + e.getMessage());
        }
    }

    /**
     * @return a REST template with client authentication and trusts all hosts strategy
     */
    @Bean(name = "trustAllWithClientAuth")
    @SneakyThrows
    public RestClient getTrustAllWithClientAuthRestTemplate() {
        try {
            SSLContext sslContext = SSLContextBuilder
                .create()
                .loadTrustMaterial(new TrustAllStrategy())
                .loadKeyMaterial(
                    TestData.getKeysDirectory().resolve("rsa/client-rsa-2048.p12").toFile(),
                    "passwd".toCharArray(),
                    "passwd".toCharArray()
                )
                .build();

            final SSLConnectionSocketFactory sslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
                .setSslContext(sslContext)
                .build();
            final HttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
                .setSSLSocketFactory(sslSocketFactory)
                .build();
            var client = HttpClients.custom()
                .setConnectionManager(cm)
                .build();

            return RestClient.builder()
                .requestFactory(new HttpComponentsClientHttpRequestFactory(client))
                .build();
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            throw new GeneralSecurityException("Failed to create SSL context: " + e.getMessage());
        }
    }

}
