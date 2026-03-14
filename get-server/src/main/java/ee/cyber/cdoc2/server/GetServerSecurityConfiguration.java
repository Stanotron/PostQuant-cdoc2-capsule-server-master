package ee.cyber.cdoc2.server;


import ee.cyber.cdoc2.server.config.ConfigProperties;
import ee.cyber.cdoc2.server.config.SecurityConfiguration;

/**
 * Get server security configuration
 */
public class GetServerSecurityConfiguration extends SecurityConfiguration {

    public GetServerSecurityConfiguration(ConfigProperties configProperties) {
        super(configProperties);
    }

}
