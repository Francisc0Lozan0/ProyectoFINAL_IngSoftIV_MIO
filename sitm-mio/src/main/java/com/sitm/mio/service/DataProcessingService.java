package com.sitm.mio.service;

import com.sitm.mio.entity.VelocityRecord;
import com.sitm.mio.util.StreamingDatagramReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import SITM.MIO.BusDatagram;
import SITM.MIO.MasterPrx;
import SITM.MIO.VelocityResult;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio para procesar datos históricos y en streaming
 */
@Service
public class DataProcessingService {
    
    @Autowired
    private IceMasterService iceMasterService;
    
    @Autowired
    private VelocityDatabaseManager databaseManager;
    
    private static final int DEFAULT_BATCH_SIZE = 10000;
    
    /**
     * Procesa datos históricos desde un archivo CSV
     */
    public ProcessingResult processHistorical(String filePath, String testLabel, 
                                             Integer batchSize, Integer maxRecords) {
        ProcessingResult result = new ProcessingResult();
        result.setTestLabel(testLabel);
        result.setStartTime(LocalDateTime.now());
        
        try {
            // Verificar que el Master esté corriendo
            if (!iceMasterService.isRunning()) {
                throw new Exception("Ice Master is not running");
            }
            
            MasterPrx master = iceMasterService.getMasterProxy();
            if (master == null) {
                throw new Exception("Master proxy not available");
            }
            
            // Verificar archivo
            if (!Files.exists(Paths.get(filePath))) {
                throw new Exception("File not found: " + filePath);
            }
            
            result.setFilePath(filePath);
            
            // Leer datagramas del archivo
            System.out.println("📖 Reading data from: " + filePath);
            BusDatagram[] allDatagrams = StreamingDatagramReader.loadFromCSV(filePath);
            
            // Limitar si es necesario
            if (maxRecords != null && maxRecords > 0 && maxRecords < allDatagrams.length) {
                allDatagrams = Arrays.copyOf(allDatagrams, maxRecords);
            }
            
            result.setTotalRecords(allDatagrams.length);
            System.out.printf("📊 Loaded %,d datagrams%n", allDatagrams.length);
            
            // Procesar en lotes
            int batch = batchSize != null ? batchSize : DEFAULT_BATCH_SIZE;
            List<VelocityResult> allResults = new ArrayList<>();
            long startTime = System.currentTimeMillis();
            int batchCount = 0;
            
            for (int i = 0; i < allDatagrams.length; i += batch) {
                int end = Math.min(i + batch, allDatagrams.length);
                BusDatagram[] batchData = Arrays.copyOfRange(allDatagrams, i, end);
                
                try {
                    VelocityResult[] batchResults = master.processHistoricalData(batchData, null, null);
                    allResults.addAll(Arrays.asList(batchResults));
                    batchCount++;
                    
                    System.out.printf("✅ Batch %d processed: %,d/%,d records%n", 
                        batchCount, end, allDatagrams.length);
                } catch (Exception e) {
                    System.err.printf("❌ Error in batch %d: %s%n", batchCount, e.getMessage());
                    result.setError("Error processing batch " + batchCount + ": " + e.getMessage());
                }
            }
            
            long processingTime = System.currentTimeMillis() - startTime;
            result.setProcessingTimeMs(processingTime);
            result.setValidResults(allResults.size());
            result.setBatchCount(batchCount);
            
            // Guardar en base de datos
            if (!allResults.isEmpty()) {
                VelocityResult[] resultsArray = allResults.toArray(new VelocityResult[0]);
                databaseManager.saveVelocityResults(resultsArray, testLabel, 
                    allDatagrams.length, processingTime);
                
                double throughput = processingTime > 0 ? 
                    (allDatagrams.length / (double) processingTime) * 1000 : 0;
                databaseManager.savePerformanceMetrics(testLabel, allDatagrams.length, 
                    processingTime, batchCount, getWorkerCount(), throughput);
                
                databaseManager.saveSummaryStats(testLabel, allResults, 
                    allDatagrams.length, processingTime);
            }
            
            result.setEndTime(LocalDateTime.now());
            result.setSuccess(true);
            
            System.out.printf("✅ Processing completed: %,d records in %,d ms%n", 
                allDatagrams.length, processingTime);
            
        } catch (Exception e) {
            result.setSuccess(false);
            result.setError(e.getMessage());
            result.setEndTime(LocalDateTime.now());
            System.err.println("❌ Processing error: " + e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }
    
    /**
     * Obtiene el número de workers activos
     */
    private int getWorkerCount() {
        try {
            String status = iceMasterService.getMasterStatus();
            if (status.contains("Workers:")) {
                String workersStr = status.substring(status.indexOf("Workers:") + 8).trim();
                return Integer.parseInt(workersStr.split("\\s+")[0]);
            }
        } catch (Exception e) {
            // Ignorar errores
        }
        return 0;
    }
    
    /**
     * Clase para resultado de procesamiento
     */
    public static class ProcessingResult {
        private boolean success;
        private String testLabel;
        private String filePath;
        private Integer totalRecords;
        private Integer validResults;
        private Integer batchCount;
        private Long processingTimeMs;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String error;
        
        // Getters y Setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getTestLabel() { return testLabel; }
        public void setTestLabel(String testLabel) { this.testLabel = testLabel; }
        
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        
        public Integer getTotalRecords() { return totalRecords; }
        public void setTotalRecords(Integer totalRecords) { this.totalRecords = totalRecords; }
        
        public Integer getValidResults() { return validResults; }
        public void setValidResults(Integer validResults) { this.validResults = validResults; }
        
        public Integer getBatchCount() { return batchCount; }
        public void setBatchCount(Integer batchCount) { this.batchCount = batchCount; }
        
        public Long getProcessingTimeMs() { return processingTimeMs; }
        public void setProcessingTimeMs(Long processingTimeMs) { this.processingTimeMs = processingTimeMs; }
        
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
        
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        
        public Double getThroughputDps() {
            if (processingTimeMs != null && processingTimeMs > 0 && totalRecords != null) {
                return (totalRecords / (double) processingTimeMs) * 1000;
            }
            return 0.0;
        }
        
        public double getElapsedTimeMs() {
            return processingTimeMs != null ? processingTimeMs : 0.0;
        }
    }
}
