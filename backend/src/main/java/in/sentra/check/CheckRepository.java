package in.sentra.check;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface CheckRepository extends JpaRepository<Check, Long> {

    @Query(value = """
            SELECT * FROM checks
            WHERE enabled AND next_run_at <= :now
            ORDER BY next_run_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Check> findDueForUpdate(@Param("now") Instant now, @Param("limit") int limit);
}
