package com.careroute.data;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface WaitTimeObservationRepository extends JpaRepository<WaitTimeObservationEntity, UUID> {
    Optional<WaitTimeObservationEntity> findTopByHospital_IdOrderByObservedAtDesc(String hospitalId);
    List<WaitTimeObservationEntity> findTop20ByHospital_IdOrderByObservedAtDesc(String hospitalId);
    List<WaitTimeObservationEntity> findByHospital_IdOrderByObservedAtAsc(String hospitalId);
    boolean existsByHospital_IdAndObservedAtAndWaitMinutes(String hospitalId, java.time.Instant observedAt, int waitMinutes);
}
