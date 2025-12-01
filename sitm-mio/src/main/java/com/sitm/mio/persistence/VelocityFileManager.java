package com.sitm.mio.persistence;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

import SITM.MIO.VelocityResult;

public class VelocityFileManager {
    private static final String RESULTS_DIR = "./results";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = 
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    
    static {
        // Crear directorio de resultados si no existe
        try {
            Files.createDirectories(Paths.get(RESULTS_DIR));
        } catch (IOException e) {
            System.err.println("❌ No se pudo crear directorio de resultados: " + e.getMessage());
        }
    }
    
    /**
     * Guarda resultados de velocidad en archivo CSV
     */
    public static void saveVelocityResults(VelocityResult[] results, String testLabel, 
                                         long datagramCount, long processingTime) {
        if (results == null || results.length == 0) {
            System.out.println("⚠ No hay resultados para guardar");
            return;
        }
        
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String filename = String.format("%s/velocities_%s_%s.csv", 
            RESULTS_DIR, testLabel.replace(" ", "_"), timestamp);
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            // Encabezado con metadatos
            writer.printf("# Test: %s%n", testLabel);
            writer.printf("# Timestamp: %s%n", LocalDateTime.now());
            writer.printf("# Datagramas procesados: %,d%n", datagramCount);
            writer.printf("# Tiempo procesamiento: %,d ms%n", processingTime);
            writer.printf("# Resultados válidos: %d/%d%n", 
                Arrays.stream(results).filter(r -> r.sampleCount > 0).count(), results.length);
            
            // Encabezado CSV
            writer.println("arc_id,velocity_m_s,velocity_km_h,sample_count,line_id,timestamp");
            
            // Datos
            int savedCount = 0;
            for (VelocityResult result : results) {
                if (result.sampleCount > 0 && result.averageVelocity > 0) {
                    String lineId = extractLineId(result.arcId);
                    writer.printf("%s,%.4f,%.4f,%d,%s,%s%n",
                        result.arcId,
                        result.averageVelocity,
                        result.averageVelocity * 3.6,
                        result.sampleCount,
                        lineId,
                        LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    );
                    savedCount++;
                }
            }
            
            System.out.printf("✅ Guardados %,d resultados en: %s%n", savedCount, filename);
            
        } catch (IOException e) {
            System.err.println("❌ Error guardando resultados: " + e.getMessage());
        }
    }
    
    /**
     * Guarda métricas de performance para análisis
     */
    public static void savePerformanceMetrics(String testLabel, long datagramCount, 
                                            long processingTime, int batchCount,
                                            int workers, double throughput) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String filename = String.format("%s/performance_%s_%s.csv", 
            RESULTS_DIR, testLabel.replace(" ", "_"), timestamp);
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename, true))) {
            // Si el archivo está vacío, escribir encabezado
            File file = new File(filename);
            if (file.length() == 0) {
                writer.println("test_label,timestamp,datagram_count,processing_time_ms,batch_count,workers,throughput_dps,throughput_dpm");
            }
            
            writer.printf("%s,%s,%,d,%,d,%d,%d,%.2f,%.2f%n",
                testLabel.replace(" ", "_"),
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                datagramCount,
                processingTime,
                batchCount,
                workers,
                throughput,
                throughput * 60.0  // datagramas por minuto
            );
            
            System.out.printf("✅ Métricas guardadas en: %s%n", filename);
            
        } catch (IOException e) {
            System.err.println("❌ Error guardando métricas: " + e.getMessage());
        }
    }
    
    /**
     * Guarda datos para gráfico de punto de corte (Requerimiento E)
     */
    public static void saveCutoffPointData(String scale, int workers, int batches, 
                                         long processingTime, double throughput) {
        String filename = RESULTS_DIR + "/cutoff_analysis.csv";
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename, true))) {
            File file = new File(filename);
            if (file.length() == 0) {
                writer.println("scale,workers,batches,processing_time_ms,throughput_dps,timestamp");
            }
            
            writer.printf("%s,%d,%d,%,d,%.2f,%s%n",
                scale,
                workers,
                batches,
                processingTime,
                throughput,
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            );
            
            System.out.printf("✅ Datos para punto de corte guardados: %s%n", filename);
            
        } catch (IOException e) {
            System.err.println("❌ Error guardando datos de punto de corte: " + e.getMessage());
        }
    }
    
    /**
     * Guarda estadísticas resumidas del procesamiento
     */
    public static void saveSummaryStats(String testLabel, List<VelocityResult> results, 
                                      long datagramCount, long processingTime) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String filename = String.format("%s/summary_%s_%s.txt", 
            RESULTS_DIR, testLabel.replace(" ", "_"), timestamp);
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("=".repeat(80));
            writer.println("RESUMEN DE PROCESAMIENTO - SITM MIO");
            writer.println("=".repeat(80));
            writer.println();
            writer.printf("Test: %s%n", testLabel);
            writer.printf("Timestamp: %s%n", LocalDateTime.now());
            writer.printf("Datagramas procesados: %,d%n", datagramCount);
            writer.printf("Tiempo total: %,d ms (%.2f minutos)%n", 
                processingTime, processingTime / 60000.0);
            
            // Estadísticas de resultados
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
            
            writer.println();
            writer.println("ESTADÍSTICAS DE VELOCIDAD:");
            writer.println("-".repeat(80));
            writer.printf("Resultados válidos: %,d / %,d%n", validResults, results.size());
            writer.printf("Muestras totales: %,d%n", totalSamples);
            writer.printf("Velocidad promedio: %.2f m/s (%.1f km/h)%n", avgVelocity, avgVelocity * 3.6);
            writer.printf("Velocidad máxima: %.2f m/s (%.1f km/h)%n", maxVelocity, maxVelocity * 3.6);
            writer.printf("Velocidad mínima: %.2f m/s (%.1f km/h)%n", 
                minVelocity == Double.MAX_VALUE ? 0 : minVelocity, 
                (minVelocity == Double.MAX_VALUE ? 0 : minVelocity) * 3.6);
            
            // Top 10 arcos más rápidos
            writer.println();
            writer.println("TOP 10 ARCOS MÁS RÁPIDOS:");
            writer.println("-".repeat(80));
            
            results.stream()
                .filter(r -> r.sampleCount > 0 && r.averageVelocity > 0)
                .sorted((a, b) -> Double.compare(b.averageVelocity, a.averageVelocity))
                .limit(10)
                .forEach(r -> {
                    writer.printf("  %-30s: %6.2f m/s (%5.1f km/h) - %d muestras%n",
                        r.arcId, r.averageVelocity, r.averageVelocity * 3.6, r.sampleCount);
                });
            
            System.out.printf("✅ Resumen guardado en: %s%n", filename);
            
        } catch (IOException e) {
            System.err.println("❌ Error guardando resumen: " + e.getMessage());
        }
    }
    
    private static String extractLineId(String arcId) {
        if (arcId == null || !arcId.contains("_")) return "unknown";
        try {
            String[] parts = arcId.split("_");
            return parts.length >= 2 ? parts[1] : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }
}