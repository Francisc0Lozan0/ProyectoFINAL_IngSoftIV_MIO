package com.sitm.mio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO para respuestas de velocidad
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VelocityResponseDTO {
    private String arcId;
    private String lineId;
    private Double velocityMs;
    private Double velocityKmh;
    private Integer sampleCount;
    private String testLabel;
    private LocalDateTime timestamp;
}
