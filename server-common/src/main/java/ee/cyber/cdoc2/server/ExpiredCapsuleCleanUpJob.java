package ee.cyber.cdoc2.server;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Types;
import org.springframework.jdbc.core.JdbcTemplate;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ee.cyber.cdoc2.server.exeptions.JobFailureException;


/**
 * Cleanup job for expired CDOC2 key capsule table
 */
@Component
@Slf4j
@RequiredArgsConstructor
public final class ExpiredCapsuleCleanUpJob {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Executes the stored function {@code expired_cdoc2_capsule_cleanup()} in CDOC2 database
     */
    @Scheduled(cron = "${key-capsule.expired.clean-up.cron}")
    public int cleanUpExpiredCapsules() {
        log.debug("Executing expired key capsules deletion from database");

        try {
            Integer deleted = jdbcTemplate.execute((Connection connection) -> {
                String query = "{? = call expired_cdoc2_capsule_cleanup()}";
                try (CallableStatement stmt = connection.prepareCall(query)) {
                    stmt.registerOutParameter(1, Types.INTEGER);
                    stmt.execute();
                    return stmt.getInt(1);
                }
            });

            if (deleted == null || deleted == 0) {
                log.debug("No expired key capsules");
                return 0;
            } else {
                log.info("Total number of successfully deleted expired key capsules is {}", deleted);
                return deleted;
            }
        } catch (Exception e) {
            String errorMsg = "Expired key capsules deletion has failed";
            log.error(errorMsg, e);
            throw new JobFailureException(errorMsg, e);
        }
    }
}
