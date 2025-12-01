package com.sitm.mio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para peticiones de streaming simulado
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamingRequestDTO {
    private String dataFilePath;
    private Integer intervalMs; // Intervalo entre datagramas en milisegundos
    private Integer durationSeconds; // Duración del streaming
    private Boolean autoStart; // Iniciar automáticamente
}
