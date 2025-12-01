package com.sitm.mio.controller;

import com.sitm.mio.dto.ApiResponse;
import com.sitm.mio.dto.HistoricalProcessRequestDTO;
import com.sitm.mio.service.DataProcessingService;
import com.sitm.mio.service.IceMasterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * Controller para procesamiento de datos históricos
 */
@RestController
@RequestMapping("/api/historical")
@CrossOrigin(origins = "*")
public class HistoricalDataController {
    
    @Autowired
    private DataProcessingService processingService;
    
    @Autowired
    private IceMasterService masterService;
    
    /**
     * POST /api/historical/process
     * Procesa un archivo de datos históricos
     */
    @PostMapping("/process")
    public ApiResponse<DataProcessingService.ProcessingResult> processHistorical(
            @RequestBody HistoricalProcessRequestDTO request) {
        try {
            // Validar request
            if (request.getDataFilePath() == null || request.getDataFilePath().isEmpty()) {
                return ApiResponse.error("Data file path is required");
            }
            
            if (request.getTestLabel() == null || request.getTestLabel().isEmpty()) {
                return ApiResponse.error("Test label is required");
            }
            
            // Verificar que el Master esté corriendo
            if (!masterService.isRunning()) {
                return ApiResponse.error("Ice Master is not running. Please start it first.");
            }
            
            // Procesar datos
            DataProcessingService.ProcessingResult result = processingService.processHistorical(
                request.getDataFilePath(),
                request.getTestLabel(),
                request.getBatchSize(),
                request.getMaxRecords()
            );
            
            if (result.isSuccess()) {
                return ApiResponse.success("Processing completed successfully", result);
            } else {
                return ApiResponse.error(result.getError());
            }
            
        } catch (Exception e) {
            return ApiResponse.error("Error processing historical data: " + e.getMessage());
        }
    }
    
    /**
     * GET /api/historical/status
     * Obtiene el estado del sistema de procesamiento
     */
    @GetMapping("/status")
    public ApiResponse<String> getStatus() {
        try {
            String status = masterService.getMasterStatus();
            return ApiResponse.success(status);
        } catch (Exception e) {
            return ApiResponse.error("Error getting status: " + e.getMessage());
        }
    }
}
