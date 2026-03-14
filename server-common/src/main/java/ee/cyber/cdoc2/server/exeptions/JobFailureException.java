package ee.cyber.cdoc2.server.exeptions;


/**
 * Thrown when scheduled job execution has failed.
 */
public class JobFailureException extends RuntimeException {

    /**
     * Error message constructor
     * @param message error message text
     * @param exception thrown exception
     */
    public JobFailureException(String message, Throwable exception) {
        super(message, exception);
    }

}
