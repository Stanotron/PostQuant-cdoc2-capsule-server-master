package ee.cyber.cdoc2.server;

public final class Constants {
    private Constants() {
    }

    // https://github.com/open-eid/cdoc2-openapi/blob/04eac9013b919c405eee6e88f497897758af29a0
    // /cdoc2-key-capsules-openapi.yaml#L38
    public static final String X_EXPIRY_TIME_HEADER = "x-expiry-time";
    public static final String X_EXPIRY_TIME_ADJUSTED = "x-expiry-time-adjusted";
}
