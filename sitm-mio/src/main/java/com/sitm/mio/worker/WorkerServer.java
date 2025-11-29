package com.sitm.mio.worker;

import com.sitm.mio.persistence.DBConnection;

import Ice.Communicator;
import Ice.ObjectAdapter;
import Ice.ObjectPrx;
import Ice.Util;
import SITM.MIO.MasterPrx;
import SITM.MIO.MasterPrxHelper;

public class WorkerServer {
    private final String workerId;
    private Communicator communicator;

    public WorkerServer(String workerId) {
        this.workerId = workerId;
    }

    public void start(String[] args, String masterEndpoint) {
        try {
            communicator = Util.initialize(args);
            
            ObjectAdapter adapter = communicator.createObjectAdapterWithEndpoints(
                "WorkerAdapter", "tcp -h 0.0.0.0 -p 0");
            
            VelocityWorker worker = new VelocityWorker(workerId);
            ObjectPrx workerPrx = adapter.add(worker, Util.stringToIdentity(workerId));
            adapter.activate();
            
            registerWithMaster(worker, workerPrx, masterEndpoint);
            
            System.out.println("Worker " + workerId + " started successfully");
            System.out.println("Registered with master: " + masterEndpoint);

                // Initialize DB pool (best-effort)
                    try { DBConnection.getConnection().close(); } catch (java.lang.Exception e) {}
            
            communicator.waitForShutdown();
            
        } catch (java.lang.Exception e) {
            System.err.println("Error starting Worker " + workerId + ": " + e.getMessage());
            e.printStackTrace();
        } finally {
            shutdown();
        }
    }

    private void registerWithMaster(VelocityWorker worker, ObjectPrx workerPrx, String masterEndpoint) {
        try {
            ObjectPrx base = communicator.stringToProxy("Master:" + masterEndpoint);
            MasterPrx master = MasterPrxHelper.checkedCast(base);
            
            if (master == null) {
                throw new Error("Invalid master proxy");
            }
            
            // Pass the worker proxy to the master. The generated API expects a WorkerPrx.
            SITM.MIO.WorkerPrx workerProxy = SITM.MIO.WorkerPrxHelper.uncheckedCast(workerPrx);
            master.registerWorker(workerProxy);
            
        } catch (java.lang.Exception e) {
            System.err.println("Failed to register with master: " + e.getMessage());
            throw new RuntimeException("Registration failed", e);
        }
    }

    public void shutdown() {
        if (communicator != null) {
            communicator.destroy();
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: WorkerServer <workerId> <masterEndpoint>");
            System.out.println("Example: WorkerServer worker1 \"tcp -h master-host -p 10000\"");
            return;
        }
        
        String workerId = args[0];
        String masterEndpoint = args[1];
        
        WorkerServer server = new WorkerServer(workerId);
        server.start(args, masterEndpoint);
    }
}