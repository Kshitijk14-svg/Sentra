package in.sentra.check;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CheckRepository extends JpaRepository<Check, Long> {

    // User-facing lookups: ownership is part of the query, so a check that belongs to someone
    // else is indistinguishable from one that doesn't exist (Doc 1 §7.2).
    @Query("SELECT c FROM Check c WHERE c.id = :id AND c.projectId IN "
            + "(SELECT p.id FROM Project p WHERE p.userId = :userId)")
    Optional<Check> findByIdForUser(@Param("id") Long id, @Param("userId") Long userId);

    @Query("SELECT c FROM Check c WHERE c.projectId IN (SELECT p.id FROM Project p WHERE p.userId = :userId) "
            + "ORDER BY c.id")
    List<Check> findAllForUser(@Param("userId") Long userId);

    // Everything below is for the engine (scheduler, incidents, alerts), which runs with no user
    // and is keyed by check id on purpose. Never call these from a controller.

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Check c WHERE c.id = :id")
    Optional<Check> findByIdForUpdate(@Param("id") Long id);

    Optional<Check> findByPingToken(String pingToken);

    @Query(value = """
            SELECT * FROM checks
            WHERE enabled AND next_run_at <= :now
            ORDER BY next_run_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Check> findDueForUpdate(@Param("now") Instant now, @Param("limit") int limit);
}
