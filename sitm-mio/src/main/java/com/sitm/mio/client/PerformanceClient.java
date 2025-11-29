package com.sitm.mio.client;

import com.sitm.mio.graphs.GraphVisualizer;
import com.sitm.mio.util.CSVDataLoader;
import com.sitm.mio.util.ConfigManager;

import Ice.Communicator;
import Ice.ObjectPrx;
import Ice.Util;
import SITM.MIO.BusDatagram;
import SITM.MIO.MasterPrx;
import SITM.MIO.MasterPrxHelper;
import SITM.MIO.VelocityResult;

public class PerformanceClient {
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
        
        System.out.println("Connected to Master successfully");
    }
    
    public void runTest(String dataPath, int[] testSizes) {
        System.out.println("SITM-MIO Distributed Velocity Calculation");
        System.out.println("Data path: " + dataPath);
        
        try {
            for (int size : testSizes) {
                System.out.println("Processing " + size + " datagrams");
                
                long loadStart = System.currentTimeMillis();
                
                String datagramFile = dataPath + "/datagrams4streaming.csv";
                BusDatagram[] datagrams = CSVDataLoader.loadDatagrams(datagramFile, size);
                
                long loadEnd = System.currentTimeMillis();
                System.out.println("Load time: " + (loadEnd - loadStart) + "ms");
                
                if (datagrams.length == 0) {
                    System.out.println("Could not load datagrams");
                    continue;
                }
                
                VelocityResult[] results = master.processHistoricalData(datagrams, null, null, null);
                
                long processEnd = System.currentTimeMillis();
                long processTime = processEnd - loadEnd;
                
                printResults(results, datagrams.length, processTime);
                
                showGraphVisualization(dataPath, results);
                
                Thread.sleep(2000);
            }
            
        } catch (java.lang.Exception e) {
            System.err.println("Error during testing: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void showGraphVisualization(String dataPath, VelocityResult[] results) {
        try {
            System.out.println("\n Generando visualización del grafo...");
            
            // Crear y mostrar el visualizador
            GraphVisualizer visualizer = new GraphVisualizer();
            visualizer.loadData(dataPath);
            
            // Crear ventana
            javax.swing.JFrame frame = new javax.swing.JFrame("SITM-MIO - Grafo de Velocidades");
            frame.setDefaultCloseOperation(javax.swing.JFrame.DISPOSE_ON_CLOSE);
            frame.add(visualizer);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            
            // Exportar a JPG
            String timestamp = String.valueOf(System.currentTimeMillis());
            String outputFile = "grafo_velocidades_" + timestamp + ".jpg";
            visualizer.exportToJPG(outputFile);
            
            System.out.println(" Visualización guardada en: " + outputFile);
            System.out.println(" Resumen de velocidades calculadas:");
            
            // Mostrar resumen de velocidades por arco
            for (VelocityResult result : results) {
                if (result.sampleCount > 0) {
                    System.out.printf("   %s: %.2f m/s (%.1f km/h) - %d muestras%n",
                            result.arcId, result.averageVelocity, 
                            result.averageVelocity * 3.6, result.sampleCount);
                }
            }
            
        } catch (java.lang.Exception e) {
            System.err.println("  Error en visualización: " + e.getMessage());
        }
    }
    
    private void printResults(VelocityResult[] results, int datagramCount, long processTime) {
        System.out.println("Processing Results");
        System.out.println("Processing time: " + processTime + "ms");
        System.out.println("Datagrams processed: " + datagramCount);
        System.out.println("Results generated: " + results.length);
        System.out.println("System status: " + master.getSystemStatus());
        
        int totalSamples = 0;
        double totalVelocity = 0.0;
        
        for (VelocityResult result : results) {
            totalSamples += result.sampleCount;
            totalVelocity += result.averageVelocity * result.sampleCount;
        }
        
        double overallAvg = totalSamples > 0 ? totalVelocity / totalSamples : 0.0;
        
        System.out.println("Global Metrics");
        System.out.printf("Total velocity samples: %d%n", totalSamples);
        System.out.printf("Global average velocity: %.2f m/s (%.1f km/h)%n", 
                overallAvg, overallAvg * 3.6);
        System.out.printf("Throughput: %.2f datagrams/second%n", 
                (double) datagramCount / processTime * 1000);
        
        System.out.println("Results by Worker");
        for (int i = 0; i < Math.min(5, results.length); i++) {
            VelocityResult r = results[i];
            System.out.printf("Worker %d: %d samples, %.2f m/s, time: %dms%n",
                    i, r.sampleCount, r.averageVelocity, r.processingTime);
        }
    }
    
    public void shutdown() {
        if (communicator != null) {
            communicator.destroy();
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: PerformanceClient <data_directory>");
            System.out.println("Example: PerformanceClient /path/to/csv/files");
            return;
        }
        
        PerformanceClient client = new PerformanceClient();
        
        try {
            client.initialize(args);
            
            int[] testSizes = {100000, 1000000, 10000000};
            
            client.runTest(args[0], testSizes);
            
        } catch (java.lang.Exception e) {
            System.err.println("Client error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            client.shutdown();
        }
    }
}