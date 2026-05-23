package tech.wenisch.kairos.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.Outage;

@Repository
public interface OutageRepository extends JpaRepository<Outage, Long> {

    List<Outage> findByResourceAndActiveTrueOrderByStartDateDesc(MonitoredResource resource);

    List<Outage> findByResourceOrderByStartDateDesc(MonitoredResource resource);

    List<Outage> findAllByOrderByStartDateDesc();

    @Query("SELECT o FROM Outage o LEFT JOIN FETCH o.resource ORDER BY o.startDate DESC")
    List<Outage> findAllWithResourceOrderByStartDateDesc();

    @Query("SELECT o FROM Outage o LEFT JOIN FETCH o.resource WHERE o.resource = :resource ORDER BY o.startDate DESC")
    List<Outage> findByResourceWithResourceOrderByStartDateDesc(MonitoredResource resource);

    /** Fetch all active outages with their resources in a single JOIN FETCH query. */
    @Query("SELECT o FROM Outage o JOIN FETCH o.resource WHERE o.active = true ORDER BY o.startDate DESC")
    List<Outage> findAllActiveWithResource();

    long deleteByActiveFalse();

    long deleteByActiveFalseAndEndDateBefore(LocalDateTime cutoff);

    long countByActiveTrue();
}
