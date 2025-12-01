package com.sitm.mio.config;

import com.sitm.mio.worker.WorkerServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import com.sitm.mio.service.IceMasterService;

/**
 * Starts a small pool of local ICE workers after the Master is up,
 * so scheduled streaming processing can actually run and persist results.
 */
@Component
public class WorkerAutoStarter {

    @Autowired
    private IceMasterService masterService;

    @Value("${ice.master.port:10000}")
    private int masterPort;

    @Value("${ice.workers.autostart:true}")
    private boolean autoStart;

    @Value("${ice.workers.count:2}")
    private int workerCount;

    @EventListener(ApplicationReadyEvent.class)
    public void startLocalWorkers() {
        if (!autoStart) {
            System.out.println("[Workers] Auto-start disabled. Skipping local workers.");
            return;
        }

        try {
            // Ensure master is running
            if (!masterService.isRunning()) {
                System.out.println("[Workers] Master not running yet; delaying worker startup...");
                Thread.sleep(1000);
            }

            if (!masterService.isRunning()) {
                System.err.println("[Workers] Master still not running. Workers will not start.");
                return;
            }

            String endpoint = "tcp -h 127.0.0.1 -p " + masterPort;
            int count = Math.max(1, workerCount);
            System.out.println("[Workers] Starting " + count + " local worker(s) -> " + endpoint);

            for (int i = 1; i <= count; i++) {
                final String workerId = "worker" + i;
                Thread t = new Thread(() -> {
                    try {
                        // Lightweight ICE client thread for the worker
                        new WorkerServer(workerId).start(new String[]{"--Ice.ThreadPool.Client.Size=2"}, endpoint);
                    } catch (Exception e) {
                        System.err.println("[Workers] Failed to start " + workerId + ": " + e.getMessage());
                    }
                }, "WorkerThread-" + workerId);
                t.setDaemon(true); // do not block JVM shutdown
                t.start();
            }

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("[Workers] Error starting local workers: " + e.getMessage());
        }
    }
}
