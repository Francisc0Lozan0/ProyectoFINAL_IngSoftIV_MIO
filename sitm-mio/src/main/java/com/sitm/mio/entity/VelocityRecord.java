package com.sitm.mio.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "velocity_records", indexes = {
    @Index(name = "idx_arc_id", columnList = "arc_id"),
    @Index(name = "idx_line_id", columnList = "line_id"),
    @Index(name = "idx_timestamp", columnList = "timestamp")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VelocityRecord {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "arc_id", nullable = false, length = 100)
    private String arcId;
    
    @Column(name = "line_id", length = 50)
    private String lineId;
    
    @Column(name = "velocity_m_s", nullable = false)
    private Double velocityMs;
    
    @Column(name = "velocity_km_h", nullable = false)
    private Double velocityKmh;
    
    @Column(name = "sample_count", nullable = false)
    private Integer sampleCount;
    
    @Column(name = "test_label", length = 200)
    private String testLabel;
    
    @Column(name = "datagram_count")
    private Long datagramCount;
    
    @Column(name = "processing_time_ms")
    private Long processingTimeMs;
    
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        createdAt = LocalDateTime.now();
    }
}
