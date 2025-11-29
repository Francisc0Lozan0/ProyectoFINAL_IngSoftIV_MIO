package com.sitm.mio.client;

import SITM.MIO.*;
import com.sitm.mio.util.CSVDataLoader;
import com.sitm.mio.util.ConfigManager;
import Ice.*;
import java.io.*;
import java.util.*;

public class StreamingClient {
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
        
        System.out.println("Connected to Master successfully for streaming");
    }
    
    public void runStreamingSimulation(String dataPath, int windowSize) {
        System.out.println("Starting streaming simulation with window size: " + windowSize);
        
        try {
            String datagramFile = dataPath + "/datagrams4streaming.csv";
            BusDatagram[] allDatagrams = CSVDataLoader.loadDatagrams(datagramFile, 100000);
            
            for (int start = 0; start < allDatagrams.length; start += windowSize) {
                int end = Math.min(start + windowSize, allDatagrams.length);
                BusDatagram[] windowDatagrams = Arrays.copyOfRange(allDatagrams, start, end);
                
                StreamingWindow window = new StreamingWindow();
                window.windowId = "window-" + start + "-" + end;
                window.datagrams = windowDatagrams;
                window.startTimestamp = System.currentTimeMillis();
                window.endTimestamp = window.startTimestamp + 5000;
                
                System.out.println("Sending streaming window: " + window.windowId + " with " + windowDatagrams.length + " datagrams");
                
                VelocityResult[] results = master.processStreamingData(window, null);
                
                System.out.println("Streaming results: " + results.length + " results");
                for (VelocityResult result : results) {
                    System.out.printf("Arc: %s, Avg Velocity: %.2f m/s, Samples: %d%n",
                            result.arcId, result.averageVelocity, result.sampleCount);
                }
                
                Thread.sleep(5000);
            }
            
        } catch (java.lang.Exception e) {
            System.err.println("Error during streaming simulation: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void shutdown() {
        if (communicator != null) {
            communicator.destroy();
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: StreamingClient <data_directory> [window_size]");
            System.out.println("Example: StreamingClient /path/to/csv/files 1000");
            return;
        }
        
        StreamingClient client = new StreamingClient();
        int windowSize = 1000;
        if (args.length >= 2) {
            windowSize = Integer.parseInt(args[1]);
        }
        
        try {
            client.initialize(args);
            client.runStreamingSimulation(args[0], windowSize);
        } catch (java.lang.Exception e) {
            System.err.println("Streaming client error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            client.shutdown();
        }
    }
}