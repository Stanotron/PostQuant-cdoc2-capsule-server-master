package ee.cyber.cdoc2.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;


/**
 * Configuration properties for data source connection
 */
@ConfigurationProperties(prefix = "spring.datasource")
public record DbConnectionConfigProperties(
    String url,
    String username,
    String password,
    String driverClassName
) {
}
