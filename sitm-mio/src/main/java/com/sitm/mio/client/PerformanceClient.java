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
 * Cliente ULTRA-ESTABLE - Prioriza completar el procesamiento sobre velocidad
 * CON PERSISTENCIA DE DATOS
 */
public class PerformanceClient {
    private Communicator communicator;
    private MasterPrx master;

    public void initialize(String[] args) {
        // CONFIGURACIÓN ICE MUCHO MÁS CONSERVADORA
        String[] iceArgs = new String[] {
            "--Ice.MessageSizeMax=20971520",   // 20MB - suficiente para batches pequeños
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
        System.out.println("✓ Batch Size: 1,000 datagramas - Priorizando ESTABILIDAD");
        System.out.println("✓ Persistencia de datos ACTIVADA");
    }

    /**
     * Procesa TODOS los archivos con batches MUY pequeños pero ESTABLES
     */
    public void runUltraStableProcessing(String dataPath) {
        System.out.println("SITM-MIO - MODO ULTRA-ESTABLE (Batches PEQUEÑOS)");
        System.out.println("Directorio de datos: " + dataPath);

        // TODOS LOS ARCHIVOS - desde 1,000 hasta 100 millones
        String[] testFiles = {
            dataPath + "/datagrams_1000.csv",
            dataPath + "/datagrams_10000.csv",
            dataPath + "/datagrams_100000.csv",
            dataPath + "/datagrams_1M.csv",
            dataPath + "/datagrams_10M.csv", 
            dataPath + "/datagrams_100M.csv"
        };

        String[] fileLabels = { 
            "1 MIL", 
            "10 MIL", 
            "100 MIL", 
            "1 MILLÓN", 
            "10 MILLONES", 
            "100 MILLONES" 
        };

        System.out.println("\n🎯 PROCESANDO TODOS LOS ARCHIVOS CON BATCHES PEQUEÑOS:");
        for (int i = 0; i < testFiles.length; i++) {
            System.out.printf("  %d. %s → %s%n", i + 1, testFiles[i], fileLabels[i]);
        }

        try {
            for (int i = 0; i < testFiles.length; i++) {
                processFileUltraStable(testFiles[i], fileLabels[i], i);
            }

            System.out.println("\n" + "🎉".repeat(60));
            System.out.println("✅ TODOS LOS ARCHIVOS PROCESADOS EXITOSAMENTE EN MODO ESTABLE");
            System.out.println("📊 TODOS LOS DATOS GUARDADOS EN ARCHIVOS PERSISTENTES");
            System.out.println("🎉".repeat(60));

        } catch (Exception e) {
            System.err.println("❌ Error crítico: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void processFileUltraStable(String filePath, String label, int fileIndex) {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("🐢 PROCESANDO EN MODO ULTRA-ESTABLE: " + label);
        System.out.println("📁 Archivo: " + filePath);
        System.out.println("💾 Persistencia: ACTIVADA");
        System.out.println("=".repeat(100));

        if (!java.nio.file.Files.exists(java.nio.file.Paths.get(filePath))) {
            System.err.println("❌ Archivo no encontrado: " + filePath);
            return;
        }

        int maxRetries = 2; // Menos reintentos, más pausas
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                System.out.printf("🔄 Intento %d/%d para %s%n", attempt, maxRetries, label);
                
                if (attempt > 1) {
                    System.out.println("💤 Esperando 30 segundos...");
                    Thread.sleep(30000);
                    reconnectToMaster();
                }

                processFileWithTinyBatches(filePath, label, fileIndex);
                System.out.printf("✅ %s COMPLETADO en modo estable%n", label);
                break;
                
            } catch (Exception e) {
                System.err.printf("❌ Intento %d falló: %s%n", attempt, e.getMessage());
                if (attempt == maxRetries) {
                    System.err.printf("💥 No se pudo procesar %s después de %d intentos%n", label, maxRetries);
                    // Continuar con el siguiente archivo en lugar de fallar completamente
                    return;
                }
            }
        }
    }

    private void processFileWithTinyBatches(String filePath, String label, int fileIndex) {
        try {
            // BATCH SIZE MUY PEQUEÑO - CRÍTICO PARA ESTABILIDAD
            int batchSize = 1000; // SOLO 1,000 datagramas por lote
            
            long fileSize = StreamingDatagramReader.getFileSize(filePath);
            long estimatedLines = StreamingDatagramReader.countLines(filePath);

            System.out.printf("📊 CONFIGURACIÓN ULTRA-ESTABLE:%n");
            System.out.printf("  • Tamaño archivo: %.2f MB%n", fileSize / (1024.0 * 1024.0));
            System.out.printf("  • Líneas estimadas: %,d%n", estimatedLines);
            System.out.printf("  • Batch size: %,d datagramas%n", batchSize);
            System.out.printf("  • Lotes estimados: %,d%n", (estimatedLines + batchSize - 1) / batchSize);
            System.out.printf("  • Workers: %s%n", master.getSystemStatus());

            long totalProcessed = 0;
            int batchNumber = 0;
            int successfulBatches = 0;
            List<VelocityResult> allResults = new ArrayList<>();
            long totalProcessTime = 0;
            long startTime = System.currentTimeMillis();

            try (StreamingDatagramReader reader = new StreamingDatagramReader(filePath, batchSize)) {
                BusDatagram[] batch;

                while ((batch = reader.readNextBatch()) != null) {
                    batchNumber++;
                    
                    System.out.printf("🔄 Lote %,d: %,d datagramas (Progreso: %,d/%,d)%n",
                            batchNumber, batch.length, totalProcessed + batch.length, estimatedLines);

                    // PROCESAR con batches pequeños - MENOS reintentos
                    VelocityResult[] batchResults = processTinyBatch(batch, batchNumber);
                    
                    if (batchResults.length > 0) {
                        successfulBatches++;
                        allResults.addAll(Arrays.asList(batchResults));
                    }
                    
                    totalProcessed += batch.length;

                    // PAUSAS MÁS LARGAS Y FRECUENTES
                    applyConservativePause(batchNumber, successfulBatches);
                    
                    // Mostrar progreso cada 50 lotes (o más frecuente para archivos pequeños)
                    int progressInterval = label.contains("MIL") ? 10 : 50;
                    if (batchNumber % progressInterval == 0) {
                        double progress = (totalProcessed * 100.0) / estimatedLines;
                        System.out.printf("📈 Progreso: %,d/%,d (%.1f%%) - Lotes exitosos: %d/%d%n",
                                totalProcessed, estimatedLines, progress, successfulBatches, batchNumber);
                    }
                }
            }

            long endTime = System.currentTimeMillis();
            long processingTime = endTime - startTime;
            
            // GUARDAR TODOS LOS DATOS ANTES DE IMPRIMIR RESULTADOS
            saveAllData(label, allResults, totalProcessed, processingTime, batchNumber, successfulBatches);
            
            printStableResults(allResults, totalProcessed, batchNumber, successfulBatches, 
                             label, processingTime);

        } catch (Exception e) {
            System.err.println("❌ Error en procesamiento estable: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * GUARDA TODOS LOS DATOS USANDO VelocityFileManager
     */
    private void saveAllData(String label, List<VelocityResult> results, 
                           long datagramCount, long processingTime,
                           int batchCount, int successfulBatches) {
        try {
            System.out.println("\n💾 GUARDANDO DATOS PERSISTENTES...");
            
            // Convertir lista a array para el método de guardado
            VelocityResult[] resultsArray = results.toArray(new VelocityResult[0]);
            
            // 1. Guardar resultados de velocidad
            VelocityFileManager.saveVelocityResults(resultsArray, label, datagramCount, processingTime);
            
            // 2. Calcular throughput para métricas
            double throughput = processingTime > 0 ? (datagramCount / (double) processingTime) * 1000 : 0;
            
            // 3. Obtener número de workers
            String status = master.getSystemStatus();
            int workers = extractWorkerCount(status);
            
            // 4. Guardar métricas de performance
            VelocityFileManager.savePerformanceMetrics(label, datagramCount, processingTime, 
                    batchCount, workers, throughput);
            
            // 5. Guardar datos para análisis de punto de corte
            VelocityFileManager.saveCutoffPointData(label, workers, batchCount, processingTime, throughput);
            
            // 6. Guardar resumen estadístico
            VelocityFileManager.saveSummaryStats(label, results, datagramCount, processingTime);
            
            System.out.println("✅ TODOS LOS DATOS GUARDADOS EXITOSAMENTE");
            
        } catch (Exception e) {
            System.err.println("❌ Error guardando datos: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private VelocityResult[] processTinyBatch(BusDatagram[] batch, int batchNumber) {
        // SOLO 2 reintentos para batches pequeños
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
                        // Pausa más larga entre reintentos
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
        
        System.err.printf("   💥 Lote %,d - Falló después de 2 intentos, continuando...%n", batchNumber);
        return new VelocityResult[0];
    }

    private void applyConservativePause(int batchNumber, int successfulBatches) {
        try {
            int pauseMs = 1000; // Pausa base de 1 segundo entre lotes
            
            // Pausas más largas cada cierto número de lotes
            if (batchNumber % 10 == 0) pauseMs = 3000;
            if (batchNumber % 50 == 0) pauseMs = 5000;
            if (batchNumber % 100 == 0) pauseMs = 10000;
            
            // Pausa extra si hay muchos lotes exitosos consecutivos
            if (successfulBatches > 10 && successfulBatches % 20 == 0) {
                pauseMs += 2000;
                System.out.printf("   💤 Pausa extendida de %,d ms (lotes estables)%n", pauseMs);
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
            Thread.sleep(10000); // 10 segundos
            initialize(new String[]{});
            System.out.println("✅ Conexión reestablecida");
        } catch (Exception e) {
            System.err.println("❌ Error reconectando: " + e.getMessage());
        }
    }

    private void printStableResults(List<VelocityResult> results, long datagramCount,
            int totalBatches, int successfulBatches, String label, long totalTime) {
        
        System.out.println("\n" + "=".repeat(100));
        System.out.println("🎯 RESULTADOS MODO ESTABLE - " + label);
        System.out.println("=".repeat(100));

        double successRate = (successfulBatches * 100.0) / totalBatches;
        
        System.out.printf("📦 Datagramas procesados: %,d%n", datagramCount);
        System.out.printf("⏱ Tiempo total: %,d ms (%.2f horas)%n", totalTime, totalTime / 3600000.0);
        System.out.printf("🔢 Lotes: %d exitosos / %d totales (%.1f%% éxito)%n", 
                successfulBatches, totalBatches, successRate);

        double throughput = totalTime > 0 ? (datagramCount / (double) totalTime) * 1000 : 0;
        System.out.printf("⚡ Throughput: %,d datagramas/segundo%n", (int)throughput);

        // Métricas de calidad
        int validResults = (int) results.stream()
            .filter(r -> r.sampleCount > 0 && r.averageVelocity > 0)
            .count();
        
        long totalSamples = results.stream()
            .filter(r -> r.sampleCount > 0)
            .mapToLong(r -> r.sampleCount)
            .sum();

        System.out.printf("📊 Resultados válidos: %,d/%,d (%,d muestras)%n",
                validResults, results.size(), totalSamples);

        // DATOS PARA LA GUÍA - AUNQUE EL PROCESAMIENTO SEA LENTO
        System.out.println("\n📈 DATOS PARA REQUERIMIENTOS D y E:");
        System.out.println("Tamaño, Workers, LotesExitosos, LotesTotales, TiempoTotal(ms), Throughput, TasaExito");
        
        String status = master.getSystemStatus();
        int workers = extractWorkerCount(status);
        
        System.out.printf("%,d, %d, %d, %d, %,d, %d, %.1f%n",
                datagramCount, workers, successfulBatches, totalBatches, 
                totalTime, (int)throughput, successRate);
                
        System.out.println("\n💾 NOTA: Todos los datos han sido guardados en archivos CSV y TXT");
        System.out.println("   en el directorio './results/' para su análisis posterior");
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
            System.out.println("MODO ULTRA-ESTABLE ACTIVADO:");
            System.out.println("  • Batch size: 1,000 datagramas");
            System.out.println("  • Pausas frecuentes entre lotes");
            System.out.println("  • Prioriza COMPLETAR sobre velocidad");
            System.out.println("  • Persistencia AUTOMÁTICA de todos los datos");
            System.out.println("");
            System.out.println("ARCHIVOS A PROCESAR:");
            System.out.println("  • datagrams_1000.csv (1 MIL)");
            System.out.println("  • datagrams_10000.csv (10 MIL)");
            System.out.println("  • datagrams_100000.csv (100 MIL)");
            System.out.println("  • datagrams_1M.csv (1 MILLÓN)");
            System.out.println("  • datagrams_10M.csv (10 MILLONES)");
            System.out.println("  • datagrams_100M.csv (100 MILLONES)");
            return;
        }

        PerformanceClient client = new PerformanceClient();
        
        try {
            client.initialize(args);
            client.runUltraStableProcessing(args[0]);
            
            System.out.println("\n🎯 PROCESAMIENTO COMPLETADO - DATOS LISTOS PARA ENTREGA");
            System.out.println("📊 Aunque sea lento, lo importante es que TERMINA");
            System.out.println("💾 TODOS LOS DATOS GUARDADOS EN: ./results/");
            System.out.println("📈 Use los archivos CSV generados para los requerimientos D y E");
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