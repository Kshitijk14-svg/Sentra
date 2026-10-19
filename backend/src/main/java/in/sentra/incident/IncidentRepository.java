package in.sentra.incident;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    Optional<Incident> findByCheckIdAndClosedAtIsNull(Long checkId);

    List<Incident> findByClosedAtIsNull();

    @Query("SELECT i FROM Incident i WHERE i.checkId IN (SELECT c.id FROM Check c WHERE c.projectId IN "
            + "(SELECT p.id FROM Project p WHERE p.userId = :userId)) ORDER BY i.openedAt DESC")
    List<Incident> findRecentForUser(@Param("userId") Long userId, Pageable page);

    List<Incident> findByCheckIdOrderByOpenedAtDesc(Long checkId);
}
