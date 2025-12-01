package com.sitm.mio.util;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MetricsCollector {
    private static MetricsCollector instance;
    private AtomicLong totalDatagramsProcessed = new AtomicLong(0);
    private AtomicLong totalProcessingTime = new AtomicLong(0);
    private AtomicInteger activeWorkers = new AtomicInteger(0);
    private Map<String, Integer> taskCountByWorker = new ConcurrentHashMap<>();
    
    private MetricsCollector() {}
    
    public static synchronized MetricsCollector getInstance() {
        if (instance == null) {
            instance = new MetricsCollector();
        }
        return instance;
    }
    
    public void recordProcessing(int datagramCount, long processingTime) {
        totalDatagramsProcessed.addAndGet(datagramCount);
        totalProcessingTime.addAndGet(processingTime);
    }
    
    public void workerRegistered(String workerId) {
        activeWorkers.incrementAndGet();
        taskCountByWorker.putIfAbsent(workerId, 0);
    }
    
    public void workerUnregistered(String workerId) {
        activeWorkers.decrementAndGet();
        taskCountByWorker.remove(workerId);
    }
    
    public void taskCompleted(String workerId) {
        taskCountByWorker.computeIfPresent(workerId, (k, v) -> v + 1);
    }
    
    public long getTotalDatagramsProcessed() {
        return totalDatagramsProcessed.get();
    }
    
    public long getTotalProcessingTime() {
        return totalProcessingTime.get();
    }
    
    public int getActiveWorkers() {
        return activeWorkers.get();
    }
    
    public double getAverageThroughput() {
        long totalTime = totalProcessingTime.get();
        if (totalTime == 0) return 0.0;
        return (double) totalDatagramsProcessed.get() / totalTime * 1000;
    }
    
    public Map<String, Integer> getTaskCountByWorker() {
        return new ConcurrentHashMap<>(taskCountByWorker);
    }
    
    public void reset() {
        totalDatagramsProcessed.set(0);
        totalProcessingTime.set(0);
        taskCountByWorker.clear();
    }
}