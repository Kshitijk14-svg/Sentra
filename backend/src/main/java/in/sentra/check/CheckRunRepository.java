package in.sentra.check;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CheckRunRepository extends JpaRepository<CheckRun, Long> {

    List<CheckRun> findTop50ByCheckIdOrderByStartedAtDesc(Long checkId);
}
