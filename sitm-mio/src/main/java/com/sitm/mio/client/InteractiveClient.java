package com.sitm.mio.client;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.Arrays;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import com.sitm.mio.graphs.GraphVisualizer;
import com.sitm.mio.util.CSVDataLoader;
import com.sitm.mio.util.ConfigManager;

import Ice.Communicator;
import Ice.ObjectPrx;
import Ice.Util;
import SITM.MIO.BusDatagram;
import SITM.MIO.MasterPrx;
import SITM.MIO.MasterPrxHelper;
import SITM.MIO.StreamingWindow;
import SITM.MIO.VelocityResult;

public class InteractiveClient extends JFrame {
    private Communicator communicator;
    private MasterPrx master;
    private GraphVisualizer visualizer;
    private JTextArea logArea;
    private String dataPath;
    
    public InteractiveClient() {
        initializeGUI();
    }
    
    private void initializeGUI() {
        setTitle("SITM-MIO Client Interactivo");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        
        // Panel de controles
        JPanel controlPanel = new JPanel(new FlowLayout());
        
        JButton connectBtn = new JButton("Conectar al Master");
        JButton processBtn = new JButton("Procesar Datos");
        JButton visualizeBtn = new JButton("Visualizar Grafo");
        JButton streamingBtn = new JButton("Modo Streaming");
        
        connectBtn.addActionListener(e -> connectToMaster());
        processBtn.addActionListener(e -> processData());
        visualizeBtn.addActionListener(e -> showGraph());
        streamingBtn.addActionListener(e -> startStreaming());
        
        controlPanel.add(connectBtn);
        controlPanel.add(processBtn);
        controlPanel.add(visualizeBtn);
        controlPanel.add(streamingBtn);
        
        // Área de logs
        logArea = new JTextArea(20, 60);
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);
        
        add(controlPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        
        pack();
        setLocationRelativeTo(null);
        
        log(" Cliente Interactivo SITM-MIO Iniciado");
        log(" Especifica el directorio de datos al ejecutar: java InteractiveClient <data_path>");
    }
    
    private void connectToMaster() {
        try {
            String[] args = {};
            communicator = Util.initialize(args);
            
            ConfigManager config = ConfigManager.getInstance();
            String masterHost = config.getString("master.host", "localhost");
            int masterPort = config.getInt("master.port", 10000);
            String masterEndpoint = "tcp -h " + masterHost + " -p " + masterPort;
            
            ObjectPrx base = communicator.stringToProxy("Master:" + masterEndpoint);
            master = MasterPrxHelper.checkedCast(base);
            
            if (master == null) {
                log(" Error: No se pudo conectar al Master");
                return;
            }
            
            log(" Conectado al Master en: " + masterEndpoint);
            log(" Estado del sistema: " + master.getSystemStatus());
            
        } catch (java.lang.Exception e) {
            log(" Error de conexión: " + e.getMessage());
        }
    }
    
    private void processData() {
        if (master == null) {
            log("  Primero conéctate al Master");
            return;
        }
        
        new Thread(() -> {
            try {
                log(" Iniciando procesamiento de datos...");
                
                String datagramFile = dataPath + "/datagrams4streaming.csv";
                BusDatagram[] datagrams = CSVDataLoader.loadDatagrams(datagramFile, 10000);
                
                log(" Datagramas cargados: " + datagrams.length);
                
                if (datagrams.length == 0) {
                    log(" No se pudieron cargar datagramas");
                    return;
                }
                
                VelocityResult[] results = master.processHistoricalData(datagrams, null, null, null);
                
                log(" Procesamiento completado");
                log(" Resultados generados: " + results.length);
                
                // Mostrar resumen
                int totalSamples = 0;
                double totalVelocity = 0.0;
                
                for (VelocityResult result : results) {
                    totalSamples += result.sampleCount;
                    totalVelocity += result.averageVelocity * result.sampleCount;
                }
                
                double overallAvg = totalSamples > 0 ? totalVelocity / totalSamples : 0.0;
                
                log(" Métricas Globales:");
                log("   • Muestras de velocidad: " + totalSamples);
                log("   • Velocidad promedio: " + String.format("%.2f m/s (%.1f km/h)", 
                    overallAvg, overallAvg * 3.6));
                
                // Mostrar detalles por arco
                log(" Velocidades por Arco:");
                for (VelocityResult result : results) {
                    if (result.sampleCount > 0) {
                        log(String.format("   • %s: %.2f m/s (%d muestras)", 
                            result.arcId, result.averageVelocity, result.sampleCount));
                    }
                }
                
            } catch (java.lang.Exception e) {
                log(" Error en procesamiento: " + e.getMessage());
            }
        }).start();
    }
    
    private void showGraph() {
        try {
            log(" Generando visualización del grafo...");
            
            visualizer = new GraphVisualizer();
            visualizer.loadData(dataPath);
            
            JFrame graphFrame = new JFrame("Grafo SITM-MIO - Visualización en Tiempo Real");
            graphFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            graphFrame.add(visualizer);
            graphFrame.pack();
            graphFrame.setLocationRelativeTo(null);
            graphFrame.setVisible(true);
            
            // Exportar imagen
            String timestamp = String.valueOf(System.currentTimeMillis());
            String outputFile = "grafo_interactivo_" + timestamp + ".jpg";
            visualizer.exportToJPG(outputFile);
            
            log("  Grafo exportado a: " + outputFile);
            
        } catch (java.lang.Exception e) {
            log(" Error en visualización: " + e.getMessage());
        }
    }
    
    private void startStreaming() {
        if (master == null) {
            log("  Primero conéctate al Master");
            return;
        }
        
        new Thread(() -> {
            try {
                log(" Iniciando modo streaming...");
                
                String datagramFile = dataPath + "/datagrams4streaming.csv";
                BusDatagram[] allDatagrams = CSVDataLoader.loadDatagrams(datagramFile, 1000);
                int windowSize = 100;
                
                for (int start = 0; start < allDatagrams.length; start += windowSize) {
                    int end = Math.min(start + windowSize, allDatagrams.length);
                    BusDatagram[] windowDatagrams = Arrays.copyOfRange(allDatagrams, start, end);
                    
                    StreamingWindow window = new StreamingWindow();
                    window.windowId = "window-" + start + "-" + end;
                    window.datagrams = windowDatagrams;
                    window.startTimestamp = System.currentTimeMillis();
                    window.endTimestamp = window.startTimestamp + 5000;
                    
                    log(" Ventana streaming: " + window.windowId + " - " + windowDatagrams.length + " datagramas");
                    
                    VelocityResult[] results = master.processStreamingData(window, null);
                    
                    for (VelocityResult result : results) {
                        log(String.format("    %s: %.2f m/s (%d muestras)", 
                            result.arcId, result.averageVelocity, result.sampleCount));
                    }
                    
                    Thread.sleep(3000); // Simular tiempo real
                }
                
                log(" Streaming completado");
                
            } catch (java.lang.Exception e) {
                log(" Error en streaming: " + e.getMessage());
            }
        }).start();
    }
    
    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: InteractiveClient <data_directory>");
            System.out.println("Example: InteractiveClient ./data");
            return;
        }
        
        SwingUtilities.invokeLater(() -> {
            InteractiveClient client = new InteractiveClient();
            client.dataPath = args[0];
            client.setVisible(true);
        });
    }
}