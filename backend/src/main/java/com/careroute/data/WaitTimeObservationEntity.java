package com.careroute.data;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wait_time_observations")
public class WaitTimeObservationEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hospital_id")
    private HospitalEntity hospital;

    private int waitMinutes;
    private Instant observedAt;
    private Instant collectedAt;
    private String source;
    private String sourceUrl;

    protected WaitTimeObservationEntity() {}

    public WaitTimeObservationEntity(HospitalEntity hospital, int waitMinutes, Instant observedAt,
                                     Instant collectedAt, String source, String sourceUrl) {
        this.id = UUID.randomUUID();
        this.hospital = hospital;
        this.waitMinutes = waitMinutes;
        this.observedAt = observedAt;
        this.collectedAt = collectedAt;
        this.source = source;
        this.sourceUrl = sourceUrl;
    }

    public UUID getId() { return id; }
    public HospitalEntity getHospital() { return hospital; }
    public int getWaitMinutes() { return waitMinutes; }
    public Instant getObservedAt() { return observedAt; }
    public Instant getCollectedAt() { return collectedAt; }
    public String getSource() { return source; }
    public String getSourceUrl() { return sourceUrl; }
}
