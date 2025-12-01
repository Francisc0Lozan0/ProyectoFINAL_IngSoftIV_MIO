package com.sitm.mio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para peticiones de procesamiento histórico
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoricalProcessRequestDTO {
    private String dataFilePath;
    private String testLabel;
    private Integer batchSize;
    private Integer maxRecords;
}
