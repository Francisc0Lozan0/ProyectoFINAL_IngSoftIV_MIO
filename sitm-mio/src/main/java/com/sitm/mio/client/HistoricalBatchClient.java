package com.sitm.mio.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sitm.mio.graphs.GraphVisualizer;
import com.sitm.mio.util.ConfigManager;
import com.sitm.mio.util.StreamingDatagramReader;

import Ice.Communicator;
import Ice.ObjectPrx;
import Ice.Util;
import SITM.MIO.BusDatagram;
import SITM.MIO.MasterPrx;
import SITM.MIO.MasterPrxHelper;
import SITM.MIO.VelocityResult;

/**
 * Cliente para procesamiento de datos HISTÓRICOS masivos
 * CON ODÓMETRO REAL
 */
public class HistoricalBatchClient {
    
    private Communicator communicator;
    private MasterPrx master;
    
    public void initialize(String[] args) {
        communicator = Util.initialize(args);
        
        ConfigManager config = ConfigManager.getInstance();
        String masterHost = config.getString("master.host", "localhost");
        int masterPort = config.getInt("master.port", 10000);
        String masterEndpoint = "tcp -h " + masterHost + " -p " + masterPort;
        
        ObjectPrx base = communicator.stringToProxy("Master:" + masterEndpoint);
        master = MasterPrxHelper.checkedCast(base);
        
        if (master == null) {
            throw new Error("Invalid master proxy");
        }
        
        System.out.println("✓ Conectado al Master: " + masterEndpoint);
        System.out.println("✓ Formato: CSV Real con ODÓMETRO");
    }
    
    /**
     * Procesa archivo histórico grande por lotes
     */
    public void processHistoricalFile(String filePath, String dataPath) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("PROCESAMIENTO HISTÓRICO - ODÓMETRO REAL");
        System.out.println("=".repeat(80));
        System.out.println("Archivo: " + filePath);
        
        try {
            // 1. Análisis del archivo
            long fileSize = StreamingDatagramReader.getFileSize(filePath);
            long estimatedLines = StreamingDatagramReader.countLines(filePath);
            
            System.out.println("\n📊 ANÁLISIS:");
            System.out.printf("  • Tamaño: %.2f GB%n", fileSize / (1024.0 * 1024.0 * 1024.0));
            System.out.printf("  • Líneas: %,d%n", estimatedLines);
            
            // 2. Configurar batch size
            ConfigManager config = ConfigManager.getInstance();
            int batchSize = config.getInt("processing.batch.size", 1000);
            System.out.println("\n🖥 Cluster: " + master.getSystemStatus());
            System.out.printf("  • Batch size: %,d%n", batchSize);
            
            // 3. Procesamiento por lotes
            long globalStartTime = System.currentTimeMillis();
            List<VelocityResult> allResults = new ArrayList<>();
            int batchNumber = 0;
            long totalProcessed = 0;
            
            // USAR CONSTRUCTOR CORRECTO (2 parámetros)
            try (StreamingDatagramReader reader = new StreamingDatagramReader(filePath, batchSize)) {
                
                BusDatagram[] batch;
                while ((batch = reader.readNextBatch()) != null) {
                    batchNumber++;
                    
                    System.out.println("\n" + "-".repeat(80));
                    System.out.printf("LOTE #%d - %,d datagramas%n", batchNumber, batch.length);
                    
                    long batchStartTime = System.currentTimeMillis();
                    
                    VelocityResult[] batchResults = master.processHistoricalData(batch, null, null);
                    
                    long batchEndTime = System.currentTimeMillis();
                    long batchTime = batchEndTime - batchStartTime;
                    
                    allResults.addAll(Arrays.asList(batchResults));
                    totalProcessed += batch.length;
                    
                    double throughput = (batch.length / (double) batchTime) * 1000;
                    System.out.printf("  ⏱ Tiempo: %,d ms%n", batchTime);
                    System.out.printf("  ⚡ Throughput: %.2f dps%n", throughput);
                    System.out.printf("  📈 Progreso: %,d / %,d (%.1f%%)%n", 
                        totalProcessed, estimatedLines, 
                        (totalProcessed * 100.0 / estimatedLines));
                    
                    if (batchNumber % 10 == 0) {
                        Thread.sleep(500);
                    }
                }
            }
            
            long globalEndTime = System.currentTimeMillis();
            long totalTime = globalEndTime - globalStartTime;
            
            printConsolidatedResults(allResults, totalProcessed, totalTime, batchNumber);
            
            if (allResults.size() > 0) {
                generateVisualization(dataPath, allResults);
            }
            
        } catch (IOException e) {
            System.err.println("❌ Error de lectura: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("❌ Error de procesamiento: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void printConsolidatedResults(List<VelocityResult> results, 
                                         long totalProcessed, 
                                         long totalTime,
                                         int totalBatches) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("RESULTADOS CONSOLIDADOS");
        System.out.println("=".repeat(80));
        
        System.out.printf("📦 Datagramas: %,d%n", totalProcessed);
        System.out.printf("⏱ Tiempo: %,d ms (%.2f min)%n", 
            totalTime, totalTime / 60000.0);
        System.out.printf("🔢 Lotes: %d%n", totalBatches);
        
        double globalThroughput = totalTime > 0 ? (totalProcessed / (double) totalTime) * 1000 : 0;
        System.out.printf("⚡ Throughput: %.2f dps%n", globalThroughput);
        
        Map<String, ArcStats> statsByArc = new HashMap<>();
        
        for (VelocityResult result : results) {
            if (result.sampleCount > 0 && result.averageVelocity > 0) {
                ArcStats stats = statsByArc.computeIfAbsent(
                    result.arcId, 
                    k -> new ArcStats()
                );
                
                stats.totalVelocity += result.averageVelocity * result.sampleCount;
                stats.totalSamples += result.sampleCount;
            }
        }
        
        System.out.println("\n📊 ESTADÍSTICAS:");
        System.out.printf("  • Arcos: %,d%n", statsByArc.size());
        System.out.printf("  • Muestras: %,d%n", 
            statsByArc.values().stream().mapToLong(s -> s.totalSamples).sum());
        
        double globalAvg = statsByArc.values().stream()
            .filter(s -> s.totalSamples > 0)
            .mapToDouble(s -> s.totalVelocity / s.totalSamples)
            .average()
            .orElse(0.0);
        
        System.out.printf("  • Velocidad promedio: %.2f m/s (%.1f km/h)%n", 
            globalAvg, globalAvg * 3.6);
        
        System.out.println("\n🏆 TOP 10 ARCOS MÁS RÁPIDOS:");
        statsByArc.entrySet().stream()
            .filter(e -> e.getValue().totalSamples >= 50)
            .sorted((a, b) -> Double.compare(
                b.getValue().getAverageVelocity(),
                a.getValue().getAverageVelocity()
            ))
            .limit(10)
            .forEach(e -> {
                ArcStats stats = e.getValue();
                double avg = stats.getAverageVelocity();
                System.out.printf("  %s: %.2f km/h (%,d muestras)%n", 
                    e.getKey(), avg * 3.6, stats.totalSamples);
            });
    }
    
    private void generateVisualization(String dataPath, List<VelocityResult> results) {
        try {
            System.out.println("\n📊 Generando visualización...");
            
            GraphVisualizer visualizer = new GraphVisualizer();
            visualizer.loadData(dataPath);
            visualizer.loadVelocities(results.toArray(new VelocityResult[0]));
            
            javax.swing.JFrame frame = new javax.swing.JFrame(
                "SITM-MIO - Velocidades Históricas (ODÓMETRO)"
            );
            frame.setDefaultCloseOperation(javax.swing.JFrame.DISPOSE_ON_CLOSE);
            frame.add(visualizer);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            
            String timestamp = String.valueOf(System.currentTimeMillis());
            String outputFile = "grafo_historico_" + timestamp + ".jpg";
            visualizer.exportToJPG(outputFile);
            
            System.out.println("✓ Visualización: " + outputFile);
            
        } catch (Exception e) {
            System.err.println("⚠ Error en visualización: " + e.getMessage());
        }
    }
    
    public void shutdown() {
        if (communicator != null) {
            communicator.destroy();
        }
    }
    
    static class ArcStats {
        double totalVelocity = 0;
        long totalSamples = 0;
        
        double getAverageVelocity() {
            return totalSamples > 0 ? totalVelocity / totalSamples : 0.0;
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Uso: HistoricalBatchClient <archivo_csv> <directorio_datos>");
            System.out.println("Ejemplo: HistoricalBatchClient ./data/datagrams.csv ./data");
            System.out.println("");
            System.out.println("FORMATO CSV REAL:");
            System.out.println("  eventType,date,stopId,odometer,lat,lon,taskId,lineId,tripId,unknown,timestamp,busId");
            return;
        }
        
        String historicalFile = args[0];
        String dataPath = args[1];
        
        HistoricalBatchClient client = new HistoricalBatchClient();
        
        try {
            client.initialize(args);
            client.processHistoricalFile(historicalFile, dataPath);
            
            System.out.println("\n✓ Completado");
            System.out.println("Presiona ENTER para salir...");
            System.in.read();
            
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            client.shutdown();
        }
    }
}