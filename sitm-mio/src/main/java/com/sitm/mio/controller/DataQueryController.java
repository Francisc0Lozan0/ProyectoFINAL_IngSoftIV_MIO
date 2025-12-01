package com.sitm.mio.controller;

import com.sitm.mio.dto.ApiResponse;
import com.sitm.mio.dto.SystemStatsDTO;
import com.sitm.mio.dto.VelocityResponseDTO;
import com.sitm.mio.entity.VelocityRecord;
import com.sitm.mio.repository.VelocityRecordRepository;
import com.sitm.mio.service.IceMasterService;
import com.sitm.mio.service.DataProcessingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller para consultar datos procesados
 */
@RestController
@RequestMapping("/api/data")
@CrossOrigin(origins = "*")
public class DataQueryController {
    
    @Autowired
    private VelocityRecordRepository velocityRepository;
    
    @Autowired
    private IceMasterService masterService;
    
    @Autowired
    private DataProcessingService processingService;
    
    /**
     * GET /api/data/velocities
     * Obtiene todas las velocidades (con paginación opcional)
     */
    @GetMapping("/velocities")
    public ApiResponse<List<VelocityResponseDTO>> getVelocities(
            @RequestParam(required = false) String testLabel,
            @RequestParam(required = false) String lineId,
            @RequestParam(defaultValue = "100") int limit) {
        try {
            List<VelocityRecord> records;
            
            if (testLabel != null && !testLabel.isEmpty()) {
                records = velocityRepository.findByTestLabel(testLabel);
            } else if (lineId != null && !lineId.isEmpty()) {
                records = velocityRepository.findByLineId(lineId);
            } else {
                records = velocityRepository.findAll(
                    PageRequest.of(0, limit, Sort.by("createdAt").descending())
                ).getContent();
            }
            
            List<VelocityResponseDTO> dtos = records.stream()
                .limit(limit)
                .map(this::toDTO)
                .collect(Collectors.toList());
            
            return ApiResponse.success(dtos);
        } catch (Exception e) {
            return ApiResponse.error("Error retrieving velocities: " + e.getMessage());
        }
    }
    
    /**
     * GET /api/data/velocities/historical
     * Procesa datos históricos Y retorna datos actualizados de la base de datos
     * PASO 1: Ejecuta procesamiento distribuido con Ice Master + Workers
     * PASO 2: Consulta y retorna datos actualizados de PostgreSQL
     */
    @GetMapping("/velocities/historical")
    public ApiResponse<List<VelocityResponseDTO>> getHistoricalData(
            @RequestParam(required = false) String dataFilePath,
            @RequestParam(required = false) String testLabel,
            @RequestParam(required = false) String lineId,
            @RequestParam(defaultValue = "1000") int limit,
            @RequestParam(defaultValue = "10000") int batchSize,
            @RequestParam(defaultValue = "0") int maxRecords) {
        try {
            // PASO 1: Si se proporciona archivo, procesarlo primero
            if (dataFilePath != null && !dataFilePath.isEmpty()) {
                if (testLabel == null || testLabel.isEmpty()) {
                    return ApiResponse.error("testLabel is required when processing a file");
                }
                
                System.out.println("🔄 PASO 1: Procesando datos históricos con Ice Master...");
                
                // Procesar archivo con Ice Master + Workers distribuidos
                DataProcessingService.ProcessingResult result = processingService.processHistorical(
                    dataFilePath,
                    testLabel,
                    batchSize,
                    maxRecords
                );
                
                if (!result.isSuccess()) {
                    return ApiResponse.error("Processing failed: " + result.getError());
                }
                
                System.out.printf("✅ Procesamiento completado: %d registros procesados en %.2f segundos%n",
                    result.getTotalRecords(), result.getElapsedTimeMs() / 1000.0);
            }
            
            // PASO 2: Consultar datos actualizados de la BD
            System.out.println("📊 PASO 2: Consultando datos actualizados de PostgreSQL...");
            
            List<VelocityRecord> records;
            
            if (testLabel != null && !testLabel.isEmpty() && lineId != null && !lineId.isEmpty()) {
                // Ambos filtros
                records = velocityRepository.findByTestLabelAndLineId(testLabel, lineId);
            } else if (testLabel != null && !testLabel.isEmpty()) {
                records = velocityRepository.findByTestLabel(testLabel);
            } else if (lineId != null && !lineId.isEmpty()) {
                records = velocityRepository.findByLineId(lineId);
            } else {
                // Todos los datos históricos
                records = velocityRepository.findAll(
                    PageRequest.of(0, limit, Sort.by("createdAt").descending())
                ).getContent();
            }
            
            List<VelocityResponseDTO> dtos = records.stream()
                .limit(limit)
                .map(this::toDTO)
                .collect(Collectors.toList());
            
            System.out.printf("✅ Retornando %d registros actualizados de BD%n", dtos.size());
            
            return ApiResponse.success(dtos);
        } catch (Exception e) {
            return ApiResponse.error("Error in historical data processing: " + e.getMessage());
        }
    }
    
    /**
     * GET /api/data/velocities/top
     * Obtiene las N velocidades más altas
     */
    @GetMapping("/velocities/top")
    public ApiResponse<List<VelocityResponseDTO>> getTopVelocities(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String testLabel) {
        try {
            List<VelocityRecord> records;
            
            if (testLabel != null && !testLabel.isEmpty()) {
                records = velocityRepository.findTopVelocitiesByTestLabel(testLabel);
            } else {
                records = velocityRepository.findAll(
                    Sort.by("velocityKmh").descending()
                );
            }
            
            List<VelocityResponseDTO> dtos = records.stream()
                .limit(limit)
                .map(this::toDTO)
                .collect(Collectors.toList());
            
            return ApiResponse.success(dtos);
        } catch (Exception e) {
            return ApiResponse.error("Error retrieving top velocities: " + e.getMessage());
        }
    }
    
    /**
     * GET /api/data/lines
     * Obtiene todas las líneas disponibles
     */
    @GetMapping("/lines")
    public ApiResponse<List<String>> getLines() {
        try {
            List<String> lines = velocityRepository.findDistinctLineIds();
            return ApiResponse.success(lines);
        } catch (Exception e) {
            return ApiResponse.error("Error retrieving lines: " + e.getMessage());
        }
    }
    
    /**
     * GET /api/data/velocities/line/{lineId}
     * Obtiene datos de una línea específica
     */
    @GetMapping("/velocities/line/{lineId}")
    public ApiResponse<List<VelocityResponseDTO>> getLineData(
            @PathVariable String lineId,
            @RequestParam(required = false) String testLabel) {
        try {
            List<VelocityRecord> records;
            
            if (testLabel != null && !testLabel.isEmpty()) {
                records = velocityRepository.findByTestLabelAndLineId(testLabel, lineId);
            } else {
                records = velocityRepository.findByLineId(lineId);
            }
            
            List<VelocityResponseDTO> dtos = records.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
            
            return ApiResponse.success(dtos);
        } catch (Exception e) {
            return ApiResponse.error("Error retrieving line data: " + e.getMessage());
        }
    }
    
    /**
     * GET /api/data/velocities/arc/{arcId}
     * Obtiene datos de un arco específico
     */
    @GetMapping("/velocities/arc/{arcId}")
    public ApiResponse<List<VelocityResponseDTO>> getArcData(
            @PathVariable String arcId,
            @RequestParam(required = false) String testLabel) {
        try {
            List<VelocityRecord> records;
            
            if (testLabel != null && !testLabel.isEmpty()) {
                records = velocityRepository.findByTestLabelAndArcId(testLabel, arcId);
            } else {
                records = velocityRepository.findByArcId(arcId);
            }
            
            List<VelocityResponseDTO> dtos = records.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
            
            return ApiResponse.success(dtos);
        } catch (Exception e) {
            return ApiResponse.error("Error retrieving arc data: " + e.getMessage());
        }
    }
    
    /**
     * GET /api/data/stats
     * Obtiene estadísticas del sistema
     */
    @GetMapping("/stats")
    public ApiResponse<SystemStatsDTO> getSystemStats() {
        try {
            // Obtener estadísticas de BD
            long totalRecords = velocityRepository.count();
            
            Double avgVelocity = velocityRepository.findAll().stream()
                .mapToDouble(VelocityRecord::getVelocityKmh)
                .average()
                .orElse(0.0);
            
            Double maxVelocity = velocityRepository.findAll().stream()
                .mapToDouble(VelocityRecord::getVelocityKmh)
                .max()
                .orElse(0.0);
            
            LocalDateTime lastProcessing = velocityRepository.findAll(
                PageRequest.of(0, 1, Sort.by("createdAt").descending())
            ).stream()
                .findFirst()
                .map(VelocityRecord::getCreatedAt)
                .orElse(null);
            
            // Obtener workers activos
            String masterStatus = masterService.getMasterStatus();
            int activeWorkers = extractWorkerCount(masterStatus);
            
            SystemStatsDTO stats = SystemStatsDTO.builder()
                .activeWorkers(activeWorkers)
                .totalRecordsProcessed(0L) // Se puede calcular de performance_metrics
                .totalVelocityRecords(totalRecords)
                .avgVelocityKmh(avgVelocity)
                .maxVelocityKmh(maxVelocity)
                .lastProcessingTime(lastProcessing)
                .systemStatus(masterStatus)
                .build();
            
            return ApiResponse.success(stats);
        } catch (Exception e) {
            return ApiResponse.error("Error retrieving stats: " + e.getMessage());
        }
    }
    
    /**
     * GET /api/data/tests
     * Obtiene lista de tests disponibles
     */
    @GetMapping("/tests")
    public ApiResponse<List<String>> getTests() {
        try {
            List<String> tests = velocityRepository.findAll().stream()
                .map(VelocityRecord::getTestLabel)
                .distinct()
                .collect(Collectors.toList());
            
            return ApiResponse.success(tests);
        } catch (Exception e) {
            return ApiResponse.error("Error retrieving tests: " + e.getMessage());
        }
    }
    
    private VelocityResponseDTO toDTO(VelocityRecord record) {
        return VelocityResponseDTO.builder()
            .arcId(record.getArcId())
            .lineId(record.getLineId())
            .velocityMs(record.getVelocityMs())
            .velocityKmh(record.getVelocityKmh())
            .sampleCount(record.getSampleCount())
            .testLabel(record.getTestLabel())
            .timestamp(record.getTimestamp())
            .build();
    }
    
    private int extractWorkerCount(String status) {
        try {
            if (status != null && status.contains("Workers:")) {
                String workersStr = status.substring(status.indexOf("Workers:") + 8).trim();
                return Integer.parseInt(workersStr.split("\\s+")[0]);
            }
        } catch (Exception e) {
            // Ignorar errores
        }
        return 0;
    }
}
