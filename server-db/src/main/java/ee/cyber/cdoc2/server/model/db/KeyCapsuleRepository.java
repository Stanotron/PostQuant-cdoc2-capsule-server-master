package ee.cyber.cdoc2.server.model.db;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KeyCapsuleRepository extends JpaRepository<KeyCapsuleDb, String> {

    Optional<KeyCapsuleDb> findByRecipient(byte[] recipient);

}
