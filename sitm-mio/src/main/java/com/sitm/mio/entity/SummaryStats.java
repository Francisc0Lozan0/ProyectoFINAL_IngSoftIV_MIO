package com.sitm.mio.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "summary_stats", indexes = {
    @Index(name = "idx_test_label_summary", columnList = "test_label"),
    @Index(name = "idx_timestamp_summary", columnList = "timestamp")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SummaryStats {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "test_label", nullable = false, length = 200)
    private String testLabel;
    
    @Column(name = "datagram_count", nullable = false)
    private Long datagramCount;
    
    @Column(name = "processing_time_ms", nullable = false)
    private Long processingTimeMs;
    
    @Column(name = "valid_results")
    private Integer validResults;
    
    @Column(name = "total_results")
    private Integer totalResults;
    
    @Column(name = "total_samples")
    private Integer totalSamples;
    
    @Column(name = "avg_velocity_ms")
    private Double avgVelocityMs;
    
    @Column(name = "max_velocity_ms")
    private Double maxVelocityMs;
    
    @Column(name = "min_velocity_ms")
    private Double minVelocityMs;
    
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
