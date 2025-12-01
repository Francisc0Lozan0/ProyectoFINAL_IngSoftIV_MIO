package com.sitm.mio.service;

import com.sitm.mio.entity.CutoffAnalysis;
import com.sitm.mio.entity.PerformanceMetric;
import com.sitm.mio.entity.SummaryStats;
import com.sitm.mio.entity.VelocityRecord;
import com.sitm.mio.repository.CutoffAnalysisRepository;
import com.sitm.mio.repository.PerformanceMetricRepository;
import com.sitm.mio.repository.SummaryStatsRepository;
import com.sitm.mio.repository.VelocityRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import SITM.MIO.VelocityResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class VelocityDatabaseManager {
    
    @Autowired
    private VelocityRecordRepository velocityRecordRepository;
    
    @Autowired
    private PerformanceMetricRepository performanceMetricRepository;
    
    @Autowired
    private CutoffAnalysisRepository cutoffAnalysisRepository;
    
    @Autowired
    private SummaryStatsRepository summaryStatsRepository;
    
    /**
     * Guarda resultados de velocidad en la base de datos
     */
    @Transactional
    public void saveVelocityResults(VelocityResult[] results, String testLabel, 
                                   long datagramCount, long processingTime) {
        if (results == null || results.length == 0) {
            System.out.println("⚠ No hay resultados para guardar");
            return;
        }
        
        List<VelocityRecord> records = new ArrayList<>();
        int savedCount = 0;
        
        for (VelocityResult result : results) {
            if (result.sampleCount > 0 && result.averageVelocity > 0) {
                String lineId = extractLineId(result.arcId);
                
                VelocityRecord record = VelocityRecord.builder()
                    .arcId(result.arcId)
                    .lineId(lineId)
                    .velocityMs(result.averageVelocity)
                    .velocityKmh(result.averageVelocity * 3.6)
                    .sampleCount(result.sampleCount)
                    .testLabel(testLabel)
                    .datagramCount(datagramCount)
                    .processingTimeMs(processingTime)
                    .timestamp(LocalDateTime.now())
                    .build();
                
                records.add(record);
                savedCount++;
            }
        }
        
        velocityRecordRepository.saveAll(records);
        System.out.printf("✅ Guardados %,d resultados en la base de datos%n", savedCount);
    }
    
    /**
     * Guarda métricas de performance
     */
    @Transactional
    public void savePerformanceMetrics(String testLabel, long datagramCount, 
                                      long processingTime, int batchCount,
                                      int workers, double throughput) {
        PerformanceMetric metric = PerformanceMetric.builder()
            .testLabel(testLabel)
            .datagramCount(datagramCount)
            .processingTimeMs(processingTime)
            .batchCount(batchCount)
            .workers(workers)
            .throughputDps(throughput)
            .throughputDpm(throughput * 60.0)
            .timestamp(LocalDateTime.now())
            .build();
        
        performanceMetricRepository.save(metric);
        System.out.printf("✅ Métricas de performance guardadas en la base de datos%n");
    }
    
    /**
     * Guarda datos para análisis de punto de corte
     */
    @Transactional
    public void saveCutoffPointData(String scale, int workers, int batches, 
                                   long processingTime, double throughput) {
        CutoffAnalysis analysis = CutoffAnalysis.builder()
            .scale(scale)
            .workers(workers)
            .batches(batches)
            .processingTimeMs(processingTime)
            .throughputDps(throughput)
            .timestamp(LocalDateTime.now())
            .build();
        
        cutoffAnalysisRepository.save(analysis);
        System.out.printf("✅ Datos de punto de corte guardados en la base de datos%n");
    }
    
    /**
     * Guarda estadísticas resumidas del procesamiento
     */
    @Transactional
    public void saveSummaryStats(String testLabel, List<VelocityResult> results, 
                                long datagramCount, long processingTime) {
        // Calcular estadísticas
        int validResults = 0;
        int totalSamples = 0;
        double totalVelocity = 0.0;
        double maxVelocity = 0.0;
        double minVelocity = Double.MAX_VALUE;
        
        for (VelocityResult result : results) {
            if (result.sampleCount > 0 && result.averageVelocity > 0) {
                validResults++;
                totalSamples += result.sampleCount;
                totalVelocity += result.averageVelocity * result.sampleCount;
                maxVelocity = Math.max(maxVelocity, result.averageVelocity);
                minVelocity = Math.min(minVelocity, result.averageVelocity);
            }
        }
        
        double avgVelocity = totalSamples > 0 ? totalVelocity / totalSamples : 0.0;
        
        SummaryStats stats = SummaryStats.builder()
            .testLabel(testLabel)
            .datagramCount(datagramCount)
            .processingTimeMs(processingTime)
            .validResults(validResults)
            .totalResults(results.size())
            .totalSamples(totalSamples)
            .avgVelocityMs(avgVelocity)
            .maxVelocityMs(maxVelocity)
            .minVelocityMs(minVelocity == Double.MAX_VALUE ? 0.0 : minVelocity)
            .timestamp(LocalDateTime.now())
            .build();
        
        summaryStatsRepository.save(stats);
        System.out.printf("✅ Estadísticas resumidas guardadas en la base de datos%n");
        
        // Imprimir resumen en consola
        System.out.println();
        System.out.println("=".repeat(80));
        System.out.println("RESUMEN DE PROCESAMIENTO - SITM MIO");
        System.out.println("=".repeat(80));
        System.out.printf("Test: %s%n", testLabel);
        System.out.printf("Datagramas procesados: %,d%n", datagramCount);
        System.out.printf("Tiempo total: %,d ms (%.2f minutos)%n", 
            processingTime, processingTime / 60000.0);
        System.out.printf("Resultados válidos: %,d / %,d%n", validResults, results.size());
        System.out.printf("Velocidad promedio: %.2f m/s (%.1f km/h)%n", avgVelocity, avgVelocity * 3.6);
        System.out.printf("Velocidad máxima: %.2f m/s (%.1f km/h)%n", maxVelocity, maxVelocity * 3.6);
        System.out.printf("Velocidad mínima: %.2f m/s (%.1f km/h)%n", 
            minVelocity == Double.MAX_VALUE ? 0 : minVelocity, 
            (minVelocity == Double.MAX_VALUE ? 0 : minVelocity) * 3.6);
    }
    
    /**
     * Obtiene los top N arcos más rápidos de un test
     */
    public List<VelocityRecord> getTopVelocitiesByTest(String testLabel, int limit) {
        List<VelocityRecord> all = velocityRecordRepository.findTopVelocitiesByTestLabel(testLabel);
        return all.stream().limit(limit).toList();
    }
    
    /**
     * Obtiene todas las líneas disponibles
     */
    public List<String> getAvailableLines() {
        return velocityRecordRepository.findDistinctLineIds();
    }
    
    private String extractLineId(String arcId) {
        if (arcId == null || !arcId.contains("_")) return "unknown";
        try {
            String[] parts = arcId.split("_");
            return parts.length >= 2 ? parts[1] : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }
}
