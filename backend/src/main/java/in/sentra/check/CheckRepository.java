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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Check c WHERE c.id = :id")
    Optional<Check> findByIdForUpdate(@Param("id") Long id);

    @Query(value = """
            SELECT * FROM checks
            WHERE enabled AND next_run_at <= :now
            ORDER BY next_run_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Check> findDueForUpdate(@Param("now") Instant now, @Param("limit") int limit);
}
