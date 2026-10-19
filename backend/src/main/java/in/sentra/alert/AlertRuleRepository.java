package in.sentra.alert;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

    List<AlertRule> findByCheckIdAndEnabledTrue(Long checkId);

    List<AlertRule> findByCheckId(Long checkId);
}
