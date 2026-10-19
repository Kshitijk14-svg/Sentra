package in.sentra.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    Optional<Incident> findByCheckIdAndClosedAtIsNull(Long checkId);

    List<Incident> findByClosedAtIsNull();

    List<Incident> findTop100ByOrderByOpenedAtDesc();

    List<Incident> findByCheckIdOrderByOpenedAtDesc(Long checkId);
}
