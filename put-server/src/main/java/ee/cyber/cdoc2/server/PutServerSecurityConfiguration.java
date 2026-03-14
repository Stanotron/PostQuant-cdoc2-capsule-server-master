package ee.cyber.cdoc2.server;


import ee.cyber.cdoc2.server.config.ConfigProperties;
import ee.cyber.cdoc2.server.config.SecurityConfiguration;

/**
 * Put server security configuration
 */
public class PutServerSecurityConfiguration extends SecurityConfiguration {

    public PutServerSecurityConfiguration(ConfigProperties configProperties) {
        super(configProperties);
    }

}
