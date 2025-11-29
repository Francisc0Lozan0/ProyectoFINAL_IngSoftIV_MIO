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
                System.out.println("\n" + "=".repeat(80));
                System.out.println("Processing " + size + " datagrams");
                System.out.println("=".repeat(80));
                
                long loadStart = System.currentTimeMillis();
                
                String datagramFile = dataPath + "/datagrams4streaming.csv";
                BusDatagram[] datagrams = CSVDataLoader.loadDatagrams(datagramFile, size);
                
                long loadEnd = System.currentTimeMillis();
                System.out.println("✓ Load time: " + (loadEnd - loadStart) + "ms");
                
                if (datagrams.length == 0) {
                    System.out.println("⚠ Could not load datagrams");
                    continue;
                }
                
                System.out.println("⚙ Processing " + datagrams.length + " datagrams...");
                VelocityResult[] results = master.processHistoricalData(datagrams, null, null, null);
                
                long processEnd = System.currentTimeMillis();
                long processTime = processEnd - loadEnd;
                
                printResults(results, datagrams.length, processTime);
                
                // IMPORTANTE: Mostrar el grafo con las velocidades
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
            System.out.println("\n📊 Generando visualización del grafo con velocidades...");
            
            // Crear visualizador
            GraphVisualizer visualizer = new GraphVisualizer();
            visualizer.loadData(dataPath);
            
            // CRÍTICO: Cargar las velocidades en el visualizador
            visualizer.loadVelocities(results);
            
            // Crear ventana
            javax.swing.JFrame frame = new javax.swing.JFrame("SITM-MIO - Grafo de Velocidades Promedio");
            frame.setDefaultCloseOperation(javax.swing.JFrame.DISPOSE_ON_CLOSE);
            frame.add(visualizer);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            
            // Exportar a JPG
            String timestamp = String.valueOf(System.currentTimeMillis());
            String outputFile = "grafo_velocidades_" + timestamp + ".jpg";
            visualizer.exportToJPG(outputFile);
            
            System.out.println("✓ Visualización guardada en: " + outputFile);
            System.out.println("✓ Ventana interactiva abierta");
            
            // Mostrar resumen de velocidades
            System.out.println("\n📈 Resumen de velocidades calculadas:");
            System.out.println("-".repeat(80));
            
            int count = 0;
            for (VelocityResult result : results) {
                if (result.sampleCount > 0 && count < 10) { // Mostrar primeros 10
                    System.out.printf("   %-30s: %6.2f m/s (%5.1f km/h) - %d muestras%n",
                            result.arcId, result.averageVelocity, 
                            result.averageVelocity * 3.6, result.sampleCount);
                    count++;
                }
            }
            
            if (results.length > 10) {
                System.out.println("   ... y " + (results.length - 10) + " arcos más");
            }
            
        } catch (java.lang.Exception e) {
            System.err.println("⚠ Error en visualización: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void printResults(VelocityResult[] results, int datagramCount, long processTime) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("RESULTADOS DEL PROCESAMIENTO");
        System.out.println("=".repeat(80));
        
        System.out.println("⏱ Processing time: " + processTime + "ms");
        System.out.println("📦 Datagrams processed: " + datagramCount);
        System.out.println("📊 Results generated: " + results.length);
        System.out.println("🖥 System status: " + master.getSystemStatus());
        
        int totalSamples = 0;
        double totalVelocity = 0.0;
        int arcsWithData = 0;
        
        for (VelocityResult result : results) {
            if (result.sampleCount > 0) {
                totalSamples += result.sampleCount;
                totalVelocity += result.averageVelocity * result.sampleCount;
                arcsWithData++;
            }
        }
        
        double overallAvg = totalSamples > 0 ? totalVelocity / totalSamples : 0.0;
        
        System.out.println("\n📈 MÉTRICAS GLOBALES");
        System.out.println("-".repeat(80));
        System.out.printf("   Total velocity samples: %,d%n", totalSamples);
        System.out.printf("   Arcs with velocity data: %d / %d%n", arcsWithData, results.length);
        System.out.printf("   Global average velocity: %.2f m/s (%.1f km/h)%n", 
                overallAvg, overallAvg * 3.6);
        System.out.printf("   Throughput: %.2f datagrams/second%n", 
                (double) datagramCount / processTime * 1000);
    }
    
    public void shutdown() {
        if (communicator != null) {
            communicator.destroy();
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: PerformanceClient <data_directory>");
            System.out.println("Example: PerformanceClient ./data");
            return;
        }
        
        PerformanceClient client = new PerformanceClient();
        
        try {
            client.initialize(args);
            
            int[] testSizes = {100000};
            
            client.runTest(args[0], testSizes);
            
            System.out.println("\n✓ Pruebas completadas. Presiona ENTER para salir...");
            System.in.read();
            
        } catch (java.lang.Exception e) {
            System.err.println("Client error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            client.shutdown();
        }
    }
}