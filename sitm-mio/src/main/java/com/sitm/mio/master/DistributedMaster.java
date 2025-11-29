package com.sitm.mio.master;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import com.sitm.mio.persistence.DBConnection;
import com.sitm.mio.util.CSVDataLoader;
import com.sitm.mio.util.ConfigManager;
import com.sitm.mio.util.MetricsCollector;

import Ice.Current;
import SITM.MIO.Arc;
import SITM.MIO.BusDatagram;
import SITM.MIO.LineStop;
import SITM.MIO.ProcessingTask;
import SITM.MIO.Stop;
import SITM.MIO.StreamingWindow;
import SITM.MIO.VelocityResult;
import SITM.MIO.Worker;
import SITM.MIO._MasterDisp;

public class DistributedMaster extends _MasterDisp {
    private final List<Worker> workers = new CopyOnWriteArrayList<>();
    private final Map<Worker, String> workerIds = new ConcurrentHashMap<>();
    private final int maxWorkers;
    private final AtomicInteger taskCounter = new AtomicInteger(0);
    private Stop[] stops;
    private Arc[] arcs;
    private final LoadBalancer loadBalancer;
    private final MetricsCollector metricsCollector;
    private final ScheduledExecutorService healthCheckExecutor;
    private final ExecutorService taskExecutor;
    private final long taskTimeout;

    public DistributedMaster(String dataPath) {
        ConfigManager config = ConfigManager.getInstance();
        this.maxWorkers = config.getInt("cluster.max.workers", 10);
        this.taskTimeout = config.getLong("processing.timeout.minutes", 10) * 60 * 1000;
        this.loadBalancer = new LoadBalancer(LoadBalancer.Strategy.ROUND_ROBIN);
        this.metricsCollector = MetricsCollector.getInstance();
        this.healthCheckExecutor = Executors.newScheduledThreadPool(1);
        this.taskExecutor = Executors.newCachedThreadPool();
        loadStaticData(dataPath);
        startHealthChecks();
        // Initialize persistence helper
        try {
            // force DB pool init
            DBConnection.getConnection().close();
        } catch (Exception e) {
            System.err.println("Warning: DB connection not available: " + e.getMessage());
        }
        System.out.println("Distributed Master initialized - Max workers: " + maxWorkers);
    }

    private void loadStaticData(String dataPath) {
        try {
            System.out.println("Loading static data from: " + dataPath);
            this.stops = CSVDataLoader.loadStops(dataPath + "/stops.csv");
            LineStop[] lineStops = CSVDataLoader.loadLineStops(dataPath + "/linestops.csv");
            this.arcs = CSVDataLoader.buildArcs(lineStops, stops);
            System.out.println("Static data loaded - Stops: " + stops.length + ", Arcs: " + arcs.length);
        } catch (Exception e) {
            System.err.println("Error loading static data: " + e.getMessage());
        }
    }

    @Override
    public synchronized void registerWorker(Worker worker, Current current) {
        String workerId = current.id.name;

        if (workers.size() >= maxWorkers) {
            System.out.println("Worker limit reached. Rejecting: " + workerId);
            return;
        }

        if (!workers.contains(worker)) {
            workers.add(worker);
            workerIds.put(worker, workerId);
            metricsCollector.workerRegistered(workerId);
            System.out.println("Worker registered: " + workerId + " - Total: " + workers.size());
        }
    }

    @Override
    public synchronized void unregisterWorker(Worker worker, Current current) {
        String workerId = current.id.name;
        workers.remove(worker);
        workerIds.remove(worker);
        metricsCollector.workerUnregistered(workerId);
        System.out.println("Worker unregistered: " + workerId + " - Remaining: " + workers.size());
    }

    @Override
    public VelocityResult[] processHistoricalData(BusDatagram[] datagrams, Arc[] arcs, Stop[] stops, Current current) {
        System.out.println("Processing historical data: " + datagrams.length + " datagrams");

        if (workers.isEmpty()) {
            throw new RuntimeException("No workers available for processing");
        }

        long startTime = System.currentTimeMillis();

        try {
            List<ProcessingTask> tasks = partitionData(datagrams, workers.size());
            List<VelocityResult> allResults = Collections.synchronizedList(new ArrayList<>());
            List<CompletableFuture<VelocityResult>> futures = new ArrayList<>();

            System.out.println("Distributing " + tasks.size() + " tasks to " + workers.size() + " workers");

            for (ProcessingTask task : tasks) {
                        Worker worker = loadBalancer.selectWorker(workers);
                if (worker != null) {
                    CompletableFuture<VelocityResult> future = CompletableFuture.supplyAsync(() -> {
                        try {
                            VelocityResult result = worker.processTask(task);
                                    String wid = workerIds.getOrDefault(worker, "unknown");
                                    metricsCollector.taskCompleted(wid);
                            return result;
                        } catch (Exception e) {
                            System.err.println("Task " + task.taskId + " failed: " + e.getMessage());
                            VelocityResult errorResult = new VelocityResult();
                            errorResult.arcId = "error-" + task.taskId;
                            errorResult.averageVelocity = 0.0;
                            errorResult.sampleCount = 0;
                            errorResult.processingTime = 0;
                            return errorResult;
                        }
                    }, taskExecutor);
                    futures.add(future);
                }
            }

            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
            );
            
            VelocityResult[] results = allFutures.thenApply(v -> 
                futures.stream()
                    .map(CompletableFuture::join)
                    .toArray(VelocityResult[]::new)
            ).get(taskTimeout, TimeUnit.MILLISECONDS);

            long endTime = System.currentTimeMillis();
            metricsCollector.recordProcessing(datagrams.length, endTime - startTime);
            
            // Workers persist per-arc results themselves; master will not double-persist here.

            System.out.println("Distributed processing completed in " + (endTime - startTime) + "ms");
            return results;

        } catch (TimeoutException e) {
            System.err.println("Processing timeout after " + taskTimeout + "ms");
            throw new RuntimeException("Processing timeout");
        } catch (Exception e) {
            System.err.println("Error in distributed processing: " + e.getMessage());
            throw new RuntimeException("Processing failed: " + e.getMessage());
        }
    }

    @Override
    public VelocityResult[] processStreamingData(StreamingWindow window, Current current) {
        System.out.println("Processing streaming data: " + window.datagrams.length + " datagrams");
        if (workers.isEmpty()) {
            throw new RuntimeException("No workers available for processing");
        }

        Worker worker = workers.get(0);
        try {
            VelocityResult result = worker.processStreamingWindow(window);
            return new VelocityResult[]{result};
        } catch (Exception e) {
            System.err.println("Error in streaming processing: " + e.getMessage());
            throw new RuntimeException("Streaming processing failed");
        }
    }

    @Override
    public String getSystemStatus(Current current) {
        int activeWorkers = workers.size();
        double utilization = (double) activeWorkers / maxWorkers * 100;
        return String.format("Master Status - Workers: %d/%d (%.1f%%) - Tasks: %d - Arcs: %d",
                activeWorkers, maxWorkers, utilization, taskCounter.get(), arcs.length);
    }

    private List<ProcessingTask> partitionData(BusDatagram[] datagrams, int numPartitions) {
        List<ProcessingTask> tasks = new ArrayList<>();

        if (numPartitions <= 0) numPartitions = 1;
        int chunkSize = Math.max(1, datagrams.length / numPartitions);

        System.out.println("Partitioning " + datagrams.length + " datagrams into " +
                numPartitions + " chunks of ~" + chunkSize);

        for (int i = 0; i < numPartitions; i++) {
            int startIdx = i * chunkSize;
            int endIdx = (i == numPartitions - 1) ? datagrams.length : (i + 1) * chunkSize;

            if (startIdx >= datagrams.length) break;

            BusDatagram[] chunk = Arrays.copyOfRange(datagrams, startIdx, endIdx);

            ProcessingTask task = new ProcessingTask();
            task.taskId = "task-" + taskCounter.incrementAndGet() + "-" + i;
            task.datagrams = chunk;
            task.arcs = this.arcs;
            task.stops = this.stops;
            task.totalWorkers = numPartitions;
            task.workerId = i;

            tasks.add(task);
        }

        return tasks;
    }

    private void startHealthChecks() {
        healthCheckExecutor.scheduleAtFixedRate(() -> {
            Iterator<Worker> iterator = workers.iterator();
            while (iterator.hasNext()) {
                Worker worker = iterator.next();
                try {
                    if (!worker.isAlive()) {
                        String wid = workerIds.getOrDefault(worker, "unknown");
                        System.out.println("Removing unresponsive worker: " + wid);
                        iterator.remove();
                        workerIds.remove(worker);
                    }
                } catch (Exception e) {
                    String wid = workerIds.getOrDefault(worker, "unknown");
                    System.err.println("Health check failed for worker: " + wid);
                    iterator.remove();
                    workerIds.remove(worker);
                }
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    public void shutdown() {
        healthCheckExecutor.shutdown();
        taskExecutor.shutdown();
    }
}