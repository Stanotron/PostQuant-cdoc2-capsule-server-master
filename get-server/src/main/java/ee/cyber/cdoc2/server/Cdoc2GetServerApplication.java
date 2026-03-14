package ee.cyber.cdoc2.server;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.web.server.Ssl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

import ee.cyber.cdoc2.server.config.ConfigProperties;
import ee.cyber.cdoc2.server.config.DbConnectionConfigProperties;


@SpringBootApplication
@Configuration
@EnableJpaAuditing
@Slf4j
@RequiredArgsConstructor
@EnableConfigurationProperties({
    ConfigProperties.class,
    DbConnectionConfigProperties.class
})
@EnableScheduling
public class Cdoc2GetServerApplication {

    final BuildProperties buildProperties;

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(Cdoc2GetServerApplication.class);
        // capture startup events for startup actuator endpoint
        app.setApplicationStartup(MonitoringUtil.getApplicationStartupInfo());
        app.run(args);
        log.info("CDOC2 key capsule get-server is running.");
    }

    @Bean
    MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        // 'application' tag for all metrics
        return registry -> registry.config().commonTags(
                "application", buildProperties.getArtifact() //cdoc2-get-server
        );
    }

    /**
     * Checks that the application is configured with mutual TLS.
     * @param event the context
     * @throws IllegalStateException when mutual TLS is not configured
     */
    @EventListener
    public static void checkMutualTlsConfigured(ContextRefreshedEvent event) {
        var env = event.getApplicationContext().getEnvironment();
        var clientAuth = env.getRequiredProperty("server.ssl.client-auth");

        if (Ssl.ClientAuth.NEED != Ssl.ClientAuth.valueOf(clientAuth.toUpperCase())) {
            throw new IllegalStateException("TLS client authentication not enabled");
        }
    }

}
