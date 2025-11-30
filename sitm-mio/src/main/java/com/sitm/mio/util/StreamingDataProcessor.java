package com.sitm.mio.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import SITM.MIO.BusDatagram;

/**
 * Coordinador de procesamiento paralelo para grandes volúmenes
 * Usa StreamingDatagramReader internamente + ExecutorService para paralelismo
 */
public class StreamingDataProcessor implements AutoCloseable {
    private final int batchSize;
    private final ExecutorService executor;
    private final int maxConcurrentBatches;
    private final AtomicInteger activeBatches = new AtomicInteger(0);
    private final AtomicLong totalProcessed = new AtomicLong(0);
    
    public StreamingDataProcessor(int batchSize, int parallelThreads, int maxConcurrentBatches) {
        this.batchSize = batchSize;
        this.executor = Executors.newFixedThreadPool(parallelThreads);
        this.maxConcurrentBatches = maxConcurrentBatches;
    }
    
    /**
     * Procesa archivo grande con paralelismo controlado (backpressure)
     */
    public void processLargeFile(String filePath, Consumer<BusDatagram[]> batchProcessor) {
        try (StreamingDatagramReader reader = new StreamingDatagramReader(filePath, batchSize)) {
            BusDatagram[] batch;
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            int batchNumber = 0;
            
            System.out.printf("🏁 Iniciando procesamiento paralelo: batchSize=%,d, threads=%d%n",
                batchSize, ((ThreadPoolExecutor) executor).getMaximumPoolSize());
            
            while ((batch = reader.readNextBatch()) != null) {
                batchNumber++;
                final BusDatagram[] currentBatch = batch;
                final int currentBatchNum = batchNumber;
                
                // Control de backpressure: esperar si hay muchos batches activos
                while (activeBatches.get() >= maxConcurrentBatches) {
                    Thread.sleep(100);
                    System.out.printf("⏳ Backpressure: %d batches activos...%n", activeBatches.get());
                }
                
                activeBatches.incrementAndGet();
                
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        long startTime = System.currentTimeMillis();
                        
                        System.out.printf("🔄 Procesando lote %,d (%,d datagramas)%n",
                            currentBatchNum, currentBatch.length);
                        
                        // Ejecutar el procesamiento del batch
                        batchProcessor.accept(currentBatch);
                        
                        long endTime = System.currentTimeMillis();
                        long processed = totalProcessed.addAndGet(currentBatch.length);
                        
                        System.out.printf("✅ Lote %,d completado: %,d ms (Total: %,d)%n",
                            currentBatchNum, (endTime - startTime), processed);
                        
                    } catch (Exception e) {
                        System.err.printf("❌ Error en lote %,d: %s%n", currentBatchNum, e.getMessage());
                    } finally {
                        activeBatches.decrementAndGet();
                    }
                }, executor);
                
                futures.add(future);
                
                // Limpiar futures completados periódicamente
                if (futures.size() > maxConcurrentBatches * 2) {
                    futures.removeIf(CompletableFuture::isDone);
                }
            }
            
            // Esperar finalización de todos los batches
            System.out.println("⏳ Esperando finalización de batches pendientes...");
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(1, TimeUnit.HOURS);
            
            System.out.printf("🎉 Procesamiento completado: %,d datagramas totales%n", 
                totalProcessed.get());
            
        } catch (IOException | InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException("Error procesando archivo grande", e);
        }
    }
    
    public long getTotalProcessed() {
        return totalProcessed.get();
    }
    
    public int getActiveBatches() {
        return activeBatches.get();
    }
    
    @Override
    public void close() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}