package ee.cyber.cdoc2.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;


/**
 * Security configuration properties for management endpoints credentials
 */
@ConfigurationProperties(prefix = "management.endpoints.metrics")
public record ConfigProperties(String username, String password) {
}
