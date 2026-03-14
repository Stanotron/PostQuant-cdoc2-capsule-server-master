package ee.cyber.cdoc2.server;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

import ee.cyber.cdoc2.server.config.DbConnectionConfigProperties;
import ee.cyber.cdoc2.server.config.ConfigProperties;
import ee.cyber.cdoc2.server.config.KeyCapsuleConfigProperties;


@SpringBootApplication
@Configuration
@EnableJpaAuditing
@Slf4j
@RequiredArgsConstructor
@EnableConfigurationProperties({
    ConfigProperties.class,
    DbConnectionConfigProperties.class,
    KeyCapsuleConfigProperties.class
})
@EnableScheduling
public class Cdoc2PutServerApplication implements CommandLineRunner {

    final BuildProperties buildProperties;

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(Cdoc2PutServerApplication.class);
        // capture startup events for startup actuator endpoint
        app.setApplicationStartup(MonitoringUtil.getApplicationStartupInfo());
        app.run(args);
    }

    @Override
    public void run(final String... args) throws Exception {
        log.info("CDOC2 key capsule put-server is running.");
    }

    @Bean
    MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        // 'application' tag for all metrics
        return registry -> registry.config()
                .commonTags("application", buildProperties.getArtifact() //cdoc2-put-server
        );
    }

}
