package com.sitm.mio.master;

import Ice.*;
import com.sitm.mio.util.ConfigManager;

public class MasterServer {
    private Communicator communicator;
    private ObjectAdapter adapter;
    private DistributedMaster master;

    public void start(String[] args, String dataPath) {
        try {
            communicator = Util.initialize(args);
            ConfigManager config = ConfigManager.getInstance();
            String masterHost = config.getString("master.host", "localhost");
            int masterPort = config.getInt("master.port", 10000);
            
            adapter = communicator.createObjectAdapterWithEndpoints(
                "MasterAdapter", "tcp -h " + masterHost + " -p " + masterPort);
            
            master = new DistributedMaster(dataPath);
            adapter.add(master, Util.stringToIdentity("Master"));
            adapter.activate();
            
            System.out.println("Distributed Master Server started on " + masterHost + ":" + masterPort);
            System.out.println("Data path: " + dataPath);
            System.out.println("Waiting for workers...");
            
            communicator.waitForShutdown();
            
        } catch (java.lang.Exception e) {
            System.err.println("Error starting Master Server: " + e.getMessage());
            e.printStackTrace();
        } finally {
            shutdown();
        }
    }

    public void shutdown() {
        if (communicator != null) {
            communicator.destroy();
        }
        if (master != null) {
            master.shutdown();
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: MasterServer <data_directory_path>");
            System.out.println("Example: MasterServer /path/to/csv/files");
            return;
        }
        
        MasterServer server = new MasterServer();
        server.start(args, args[0]);
    }
}