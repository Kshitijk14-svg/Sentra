package in.sentra.alert;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AlertDeliveryRepository extends JpaRepository<AlertDelivery, Long> {

    /** Returns 1 if this caller won the right to send, 0 if the unique index says it was already claimed. */
    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO alert_deliveries (incident_id, channel, transition_type)
            VALUES (:incidentId, :channel, :type)
            ON CONFLICT (incident_id, channel, transition_type) DO NOTHING
            """, nativeQuery = true)
    int claim(@Param("incidentId") Long incidentId, @Param("channel") String channel, @Param("type") String type);

    @Transactional
    @Modifying
    @Query(value = """
            UPDATE alert_deliveries SET status = :status, error = :error
            WHERE incident_id = :incidentId AND channel = :channel AND transition_type = :type
            """, nativeQuery = true)
    void mark(@Param("incidentId") Long incidentId, @Param("channel") String channel,
              @Param("type") String type, @Param("status") String status, @Param("error") String error);

    @Query(value = """
            SELECT count(DISTINCT transition_type) FROM alert_deliveries
            WHERE incident_id = :incidentId AND transition_type LIKE 'REMINDER%'
            """, nativeQuery = true)
    int countReminders(@Param("incidentId") Long incidentId);
}
