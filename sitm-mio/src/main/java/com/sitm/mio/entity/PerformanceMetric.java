package com.sitm.mio.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "performance_metrics", indexes = {
    @Index(name = "idx_test_label", columnList = "test_label"),
    @Index(name = "idx_timestamp_perf", columnList = "timestamp")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceMetric {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "test_label", nullable = false, length = 200)
    private String testLabel;
    
    @Column(name = "datagram_count", nullable = false)
    private Long datagramCount;
    
    @Column(name = "processing_time_ms", nullable = false)
    private Long processingTimeMs;
    
    @Column(name = "batch_count")
    private Integer batchCount;
    
    @Column(name = "workers")
    private Integer workers;
    
    @Column(name = "throughput_dps", nullable = false)
    private Double throughputDps;
    
    @Column(name = "throughput_dpm", nullable = false)
    private Double throughputDpm;
    
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
