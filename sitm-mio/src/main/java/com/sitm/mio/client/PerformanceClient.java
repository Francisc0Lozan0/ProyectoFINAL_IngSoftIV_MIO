package com.sitm.mio.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.sitm.mio.persistence.VelocityFileManager;
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
 * Cliente ULTRA-ESTABLE para procesamiento de datos reales SITM-MIO
 * FORMATO REAL DE CSV con odómetro
 */
public class PerformanceClient {
    private Communicator communicator;
    private MasterPrx master;

    public void initialize(String[] args) {
        // Configuración ICE conservadora
        String[] iceArgs = new String[] {
            "--Ice.MessageSizeMax=20971520",   // 20MB
            "--Ice.ACM.Timeout=1200",          // 20 minutos
            "--Ice.TCP.RcvSize=65536",         // 64KB
            "--Ice.TCP.SndSize=65536"          // 64KB
        };
        
        communicator = Util.initialize(iceArgs);
        
        ConfigManager config = ConfigManager.getInstance();
        String masterHost = config.getString("master.host", "localhost");
        int masterPort = config.getInt("master.port", 10000);
        String masterEndpoint = String.format("tcp -h %s -p %d", masterHost, masterPort);

        ObjectPrx base = communicator.stringToProxy("Master:" + masterEndpoint);
        master = MasterPrxHelper.checkedCast(base);

        if (master == null) {
            throw new Error("Invalid master proxy");
        }

        System.out.println("✓ Conectado al Master - Modo ULTRA-ESTABLE activado");
        System.out.println("✓ Formato: CSV Real con ODÓMETRO");
        System.out.println("✓ Persistencia de datos ACTIVADA");
    }

    /**
     * Procesa TODOS los archivos con batches estables
     */
    public void runUltraStableProcessing(String dataPath) {
        System.out.println("SITM-MIO - PROCESAMIENTO CON ODÓMETRO REAL");
        System.out.println("Directorio de datos: " + dataPath);

        // Archivos de prueba (ajustar según tus archivos reales)
        String[] testFiles = {
            dataPath + "/datagrams_1000.csv",
            dataPath + "/datagrams_10000.csv",
            dataPath + "/datagrams_100000.csv",
            dataPath + "/datagrams_1M.csv",
            dataPath + "/datagrams_10M.csv"
        };

        String[] fileLabels = { 
            "1_MIL", 
            "10_MIL", 
            "100_MIL", 
            "1_MILLON", 
            "10_MILLONES"
        };

        System.out.println("\n🎯 PROCESANDO ARCHIVOS CON ODÓMETRO:");
        for (int i = 0; i < testFiles.length; i++) {
            System.out.printf("  %d. %s → %s%n", i + 1, testFiles[i], fileLabels[i]);
        }

        try {
            for (int i = 0; i < testFiles.length; i++) {
                processFileUltraStable(testFiles[i], fileLabels[i], i);
            }

            System.out.println("\n" + "🎉".repeat(60));
            System.out.println("✅ TODOS LOS ARCHIVOS PROCESADOS EXITOSAMENTE");
            System.out.println("📊 DATOS GUARDADOS EN ./results/");
            System.out.println("🎉".repeat(60));

        } catch (Exception e) {
            System.err.println("❌ Error crítico: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void processFileUltraStable(String filePath, String label, int fileIndex) {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("🐢 PROCESANDO: " + label);
        System.out.println("📁 Archivo: " + filePath);
        System.out.println("💾 Persistencia: ACTIVADA");
        System.out.println("=".repeat(100));

        if (!java.nio.file.Files.exists(java.nio.file.Paths.get(filePath))) {
            System.err.println("❌ Archivo no encontrado: " + filePath);
            return;
        }

        int maxRetries = 2;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                System.out.printf("🔄 Intento %d/%d para %s%n", attempt, maxRetries, label);
                
                if (attempt > 1) {
                    System.out.println("💤 Esperando 30 segundos...");
                    Thread.sleep(30000);
                    reconnectToMaster();
                }

                processFileWithTinyBatches(filePath, label, fileIndex);
                System.out.printf("✅ %s COMPLETADO%n", label);
                break;
                
            } catch (Exception e) {
                System.err.printf("❌ Intento %d falló: %s%n", attempt, e.getMessage());
                if (attempt == maxRetries) {
                    System.err.printf("💥 No se pudo procesar %s%n", label);
                    return;
                }
            }
        }
    }

    private void processFileWithTinyBatches(String filePath, String label, int fileIndex) {
        try {
            int batchSize = 1000; // Batch pequeño y estable
            
            long fileSize = StreamingDatagramReader.getFileSize(filePath);
            long estimatedLines = StreamingDatagramReader.countLines(filePath);

            System.out.printf("📊 CONFIGURACIÓN:%n");
            System.out.printf("  • Tamaño archivo: %.2f MB%n", fileSize / (1024.0 * 1024.0));
            System.out.printf("  • Líneas estimadas: %,d%n", estimatedLines);
            System.out.printf("  • Batch size: %,d datagramas%n", batchSize);
            System.out.printf("  • Lotes estimados: %,d%n", (estimatedLines + batchSize - 1) / batchSize);

            long totalProcessed = 0;
            int batchNumber = 0;
            int successfulBatches = 0;
            List<VelocityResult> allResults = new ArrayList<>();
            long startTime = System.currentTimeMillis();

            // USAR CONSTRUCTOR CORRECTO (2 parámetros)
            try (StreamingDatagramReader reader = new StreamingDatagramReader(filePath, batchSize)) {
                BusDatagram[] batch;

                while ((batch = reader.readNextBatch()) != null) {
                    batchNumber++;
                    
                    System.out.printf("🔄 Lote %,d: %,d datagramas (Progreso: %,d/%,d)%n",
                            batchNumber, batch.length, totalProcessed + batch.length, estimatedLines);

                    VelocityResult[] batchResults = processTinyBatch(batch, batchNumber);
                    
                    if (batchResults.length > 0) {
                        successfulBatches++;
                        allResults.addAll(Arrays.asList(batchResults));
                    }
                    
                    totalProcessed += batch.length;

                    // Pausas conservadoras
                    applyConservativePause(batchNumber, successfulBatches);
                    
                    // Progreso
                    if (batchNumber % 50 == 0) {
                        double progress = (totalProcessed * 100.0) / estimatedLines;
                        System.out.printf("📈 Progreso: %,d/%,d (%.1f%%) - Exitosos: %d/%d%n",
                                totalProcessed, estimatedLines, progress, successfulBatches, batchNumber);
                    }
                }
            }

            long endTime = System.currentTimeMillis();
            long processingTime = endTime - startTime;
            
            // GUARDAR TODOS LOS DATOS
            saveAllData(label, allResults, totalProcessed, processingTime, batchNumber, successfulBatches);
            
            printStableResults(allResults, totalProcessed, batchNumber, successfulBatches, 
                             label, processingTime);

        } catch (Exception e) {
            System.err.println("❌ Error en procesamiento: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void saveAllData(String label, List<VelocityResult> results, 
                           long datagramCount, long processingTime,
                           int batchCount, int successfulBatches) {
        try {
            System.out.println("\n💾 GUARDANDO DATOS...");
            
            VelocityResult[] resultsArray = results.toArray(new VelocityResult[0]);
            
            // 1. Resultados de velocidad
            VelocityFileManager.saveVelocityResults(resultsArray, label, datagramCount, processingTime);
            
            // 2. Métricas de performance
            double throughput = processingTime > 0 ? (datagramCount / (double) processingTime) * 1000 : 0;
            String status = master.getSystemStatus();
            int workers = extractWorkerCount(status);
            
            VelocityFileManager.savePerformanceMetrics(label, datagramCount, processingTime, 
                    batchCount, workers, throughput);
            
            // 3. Datos para punto de corte
            VelocityFileManager.saveCutoffPointData(label, workers, batchCount, processingTime, throughput);
            
            // 4. Resumen estadístico
            VelocityFileManager.saveSummaryStats(label, results, datagramCount, processingTime);
            
            System.out.println("✅ TODOS LOS DATOS GUARDADOS");
            
        } catch (Exception e) {
            System.err.println("❌ Error guardando datos: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private VelocityResult[] processTinyBatch(BusDatagram[] batch, int batchNumber) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                VelocityResult[] results = master.processHistoricalData(batch, null, null);
                System.out.printf("   ✅ Lote %,d exitoso - %d resultados%n", batchNumber, results.length);
                return results;
            } catch (Exception e) {
                System.err.printf("   ❌ Lote %,d - Intento %d falló: %s%n", 
                    batchNumber, attempt, e.getMessage());
                
                if (attempt < 2) {
                    try {
                        Thread.sleep(5000);
                        if (e instanceof Ice.ConnectionLostException) {
                            reconnectToMaster();
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        System.err.printf("   💥 Lote %,d falló, continuando...%n", batchNumber);
        return new VelocityResult[0];
    }

    private void applyConservativePause(int batchNumber, int successfulBatches) {
        try {
            int pauseMs = 1000;
            
            if (batchNumber % 10 == 0) pauseMs = 3000;
            if (batchNumber % 50 == 0) pauseMs = 5000;
            if (batchNumber % 100 == 0) pauseMs = 10000;
            
            if (successfulBatches > 10 && successfulBatches % 20 == 0) {
                pauseMs += 2000;
            }
            
            Thread.sleep(pauseMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void reconnectToMaster() {
        try {
            System.out.println("🔄 Reestableciendo conexión...");
            shutdown();
            Thread.sleep(10000);
            initialize(new String[]{});
            System.out.println("✅ Conexión reestablecida");
        } catch (Exception e) {
            System.err.println("❌ Error reconectando: " + e.getMessage());
        }
    }

    private void printStableResults(List<VelocityResult> results, long datagramCount,
            int totalBatches, int successfulBatches, String label, long totalTime) {
        
        System.out.println("\n" + "=".repeat(100));
        System.out.println("🎯 RESULTADOS - " + label);
        System.out.println("=".repeat(100));

        double successRate = (successfulBatches * 100.0) / totalBatches;
        
        System.out.printf("📦 Datagramas procesados: %,d%n", datagramCount);
        System.out.printf("⏱ Tiempo total: %,d ms (%.2f minutos)%n", totalTime, totalTime / 60000.0);
        System.out.printf("🔢 Lotes: %d exitosos / %d totales (%.1f%% éxito)%n", 
                successfulBatches, totalBatches, successRate);

        double throughput = totalTime > 0 ? (datagramCount / (double) totalTime) * 1000 : 0;
        System.out.printf("⚡ Throughput: %,d datagramas/segundo%n", (int)throughput);

        int validResults = (int) results.stream()
            .filter(r -> r.sampleCount > 0 && r.averageVelocity > 0)
            .count();
        
        long totalSamples = results.stream()
            .filter(r -> r.sampleCount > 0)
            .mapToLong(r -> r.sampleCount)
            .sum();

        System.out.printf("📊 Resultados válidos: %,d/%,d (%,d muestras)%n",
                validResults, results.size(), totalSamples);

        System.out.println("\n💾 Datos guardados en ./results/");
    }

    private int extractWorkerCount(String status) {
        try {
            String[] parts = status.split("Workers:");
            if (parts.length > 1) {
                String workersPart = parts[1].split(" ")[0];
                return Integer.parseInt(workersPart.split("/")[0].trim());
            }
        } catch (Exception e) {
            // Ignorar
        }
        return 1;
    }

    public void shutdown() {
        if (communicator != null) {
            try {
                communicator.destroy();
            } catch (Exception e) {
                // Ignorar
            }
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Uso: PerformanceClient <directorio_datos>");
            System.out.println("Ejemplo: PerformanceClient ./data");
            System.out.println("");
            System.out.println("PROCESAMIENTO CON ODÓMETRO REAL:");
            System.out.println("  • Batch size: 1,000 datagramas");
            System.out.println("  • Cálculo: velocidad = (odometer2 - odometer1) / (tiempo2 - tiempo1)");
            System.out.println("  • Formato CSV: eventType,date,stopId,odometer,lat,lon,taskId,lineId,tripId,unknown,timestamp,busId");
            return;
        }

        PerformanceClient client = new PerformanceClient();
        
        try {
            client.initialize(args);
            client.runUltraStableProcessing(args[0]);
            
            System.out.println("\n🎯 PROCESAMIENTO COMPLETADO");
            System.out.println("📊 TODOS LOS DATOS EN: ./results/");
            System.out.println("\nPresiona ENTER para salir...");
            System.in.read();
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            client.shutdown();
        }
    }
}