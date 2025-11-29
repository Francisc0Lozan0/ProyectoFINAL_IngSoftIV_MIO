package com.sitm.mio.graphs;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;

import SITM.MIO.VelocityResult;

public class GraphVisualizer extends JPanel {
    
    static class Stop {
        String stopId;
        String shortName;
        double latitude;
        double longitude;
        Point2D position; 
        
        Stop(String id, String name, double lat, double lon) {
            this.stopId = id;
            this.shortName = name;
            this.latitude = lat;
            this.longitude = lon;
        }
    }
    
    static class Arc {
        String lineId;
        String lineName;
        Stop start;
        Stop end;
        int orientation;
        Color color;
        
        Arc(String lineId, String lineName, Stop start, Stop end, int orientation) {
            this.lineId = lineId;
            this.lineName = lineName;
            this.start = start;
            this.end = end;
            this.orientation = orientation;
            this.color = generateColor(lineId);
        }
        
        private static Color generateColor(String lineId) {
            int hash = lineId.hashCode();
            Random rand = new Random(hash);
            return new Color(
                rand.nextInt(156) + 100,
                rand.nextInt(156) + 100,
                rand.nextInt(156) + 100
            );
        }
    }
    
    private Map<String, Stop> stops = new HashMap<>();
    private List<Arc> arcs = new ArrayList<>();
    private Map<String, String> lineNames = new HashMap<>();
    
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    private static final int MARGIN = 100;
    
    public GraphVisualizer() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.WHITE);
    }
    
    public void loadData(String dataPath) throws IOException {
        System.out.println("Cargando datos para visualización...");
        loadStops(dataPath + "/stops.csv");
        loadLines(dataPath + "/lines.csv");
        loadLineStops(dataPath + "/linestops.csv");
        calculatePositions();
        System.out.println("Datos cargados exitosamente");
    }
    
    private void loadStops(String filePath) throws IOException {
        try (BufferedReader br = Files.newBufferedReader(Paths.get(filePath))) {
            String line;
            boolean firstLine = true;
            
            while ((line = br.readLine()) != null) {
                if (firstLine) { firstLine = false; continue; }
                
                String[] parts = line.split(",");
                if (parts.length < 8) continue;
                
                Stop stop = new Stop(
                    parts[0].trim(),
                    parts[2].trim(),
                    Double.parseDouble(parts[7].trim()),
                    Double.parseDouble(parts[6].trim())
                );
                
                stops.put(stop.stopId, stop);
            }
        }
    }
    
    private void loadLines(String filePath) throws IOException {
        try (BufferedReader br = Files.newBufferedReader(Paths.get(filePath))) {
            String line;
            boolean firstLine = true;
            
            while ((line = br.readLine()) != null) {
                if (firstLine) { firstLine = false; continue; }
                
                String[] parts = line.split(",");
                if (parts.length < 4) continue;
                
                lineNames.put(parts[0].trim(), parts[2].trim());
            }
        }
    }
    
    private void loadLineStops(String filePath) throws IOException {
        // Agrupar por línea y orientación
        Map<String, Map<Integer, List<LineStopData>>> grouped = new HashMap<>();
        
        try (BufferedReader br = Files.newBufferedReader(Paths.get(filePath))) {
            String line;
            boolean firstLine = true;
            
            while ((line = br.readLine()) != null) {
                if (firstLine) { firstLine = false; continue; }
                
                String[] parts = line.split(",");
                if (parts.length < 5) continue;
                
                int seq = Integer.parseInt(parts[1].trim());
                int orient = Integer.parseInt(parts[2].trim());
                String lineId = parts[3].trim();
                String stopId = parts[4].trim();
                
                LineStopData lsd = new LineStopData(seq, orient, lineId, stopId);
                
                grouped.computeIfAbsent(lineId, k -> new HashMap<>())
                       .computeIfAbsent(orient, k -> new ArrayList<>())
                       .add(lsd);
            }
        }
        
        // Construir arcos
        for (Map.Entry<String, Map<Integer, List<LineStopData>>> entry : grouped.entrySet()) {
            String lineId = entry.getKey();
            String lineName = lineNames.getOrDefault(lineId, lineId);
            
            for (Map.Entry<Integer, List<LineStopData>> orientEntry : entry.getValue().entrySet()) {
                int orientation = orientEntry.getKey();
                List<LineStopData> sequence = orientEntry.getValue();
                sequence.sort(Comparator.comparingInt(ls -> ls.sequence));
                
                for (int i = 0; i < sequence.size() - 1; i++) {
                    Stop start = stops.get(sequence.get(i).stopId);
                    Stop end = stops.get(sequence.get(i + 1).stopId);
                    
                    if (start != null && end != null) {
                        arcs.add(new Arc(lineId, lineName, start, end, orientation));
                    }
                }
            }
        }
    }
    
    private void calculatePositions() {
        if (stops.isEmpty()) return;
        
        // Encontrar límites geográficos
        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
        
        for (Stop stop : stops.values()) {
            minLat = Math.min(minLat, stop.latitude);
            maxLat = Math.max(maxLat, stop.latitude);
            minLon = Math.min(minLon, stop.longitude);
            maxLon = Math.max(maxLon, stop.longitude);
        }
        
        // Mapear coordenadas geográficas a coordenadas de pantalla
        double latRange = maxLat - minLat;
        double lonRange = maxLon - minLon;
        
        for (Stop stop : stops.values()) {
            double x = MARGIN + ((stop.longitude - minLon) / lonRange) * (WIDTH - 2 * MARGIN);
            double y = HEIGHT - MARGIN - ((stop.latitude - minLat) / latRange) * (HEIGHT - 2 * MARGIN);
            stop.position = new Point2D.Double(x, y);
        }
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        
        // Anti-aliasing
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        
        // Título
        g2.setColor(Color.BLACK);
        g2.setFont(new Font("Arial", Font.BOLD, 24));
        g2.drawString("GRAFO DE RUTAS SITM-MIO", WIDTH / 2 - 150, 40);
        
        // Dibujar arcos
        g2.setStroke(new BasicStroke(2f));
        for (Arc arc : arcs) {
            if (arc.start.position != null && arc.end.position != null) {
                g2.setColor(new Color(arc.color.getRed(), arc.color.getGreen(), arc.color.getBlue(), 150));
                
                // Línea con flecha
                drawArrow(g2, 
                    (int) arc.start.position.getX(), 
                    (int) arc.start.position.getY(),
                    (int) arc.end.position.getX(), 
                    (int) arc.end.position.getY()
                );
            }
        }
        
        // Dibujar paradas
        for (Stop stop : stops.values()) {
            if (stop.position != null) {
                int x = (int) stop.position.getX();
                int y = (int) stop.position.getY();
                
                // Nodo
                g2.setColor(new Color(70, 130, 180));
                g2.fillOval(x - 5, y - 5, 10, 10);
                
                // Borde
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawOval(x - 5, y - 5, 10, 10);
            }
        }
        
        // Leyenda
        drawLegend(g2);
        
        // Info
        g2.setColor(Color.DARK_GRAY);
        g2.setFont(new Font("Arial", Font.PLAIN, 12));
        g2.drawString(String.format("Paradas: %d  |  Arcos: %d  |  Rutas: %d", 
            stops.size(), arcs.size(), lineNames.size()), 20, HEIGHT - 20);
    }
    
    private void drawArrow(Graphics2D g2, int x1, int y1, int x2, int y2) {
        g2.drawLine(x1, y1, x2, y2);
        
        // Flecha
        double angle = Math.atan2(y2 - y1, x2 - x1);
        int arrowSize = 8;
        
        int[] xPoints = {
            x2,
            x2 - (int) (arrowSize * Math.cos(angle - Math.PI / 6)),
            x2 - (int) (arrowSize * Math.cos(angle + Math.PI / 6))
        };
        int[] yPoints = {
            y2,
            y2 - (int) (arrowSize * Math.sin(angle - Math.PI / 6)),
            y2 - (int) (arrowSize * Math.sin(angle + Math.PI / 6))
        };
        
        g2.fillPolygon(xPoints, yPoints, 3);
    }
    
    private void drawLegend(Graphics2D g2) {
        int x = WIDTH - 300;
        int y = 80;
        
        g2.setColor(new Color(255, 255, 255, 200));
        g2.fillRoundRect(x - 10, y - 30, 280, 150, 10, 10);
        
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(x - 10, y - 30, 280, 150, 10, 10);
        
        g2.setFont(new Font("Arial", Font.BOLD, 14));
        g2.drawString("LEYENDA", x, y);
        
        y += 25;
        g2.setFont(new Font("Arial", Font.PLAIN, 12));
        
        // Parada
        g2.setColor(new Color(70, 130, 180));
        g2.fillOval(x, y - 5, 10, 10);
        g2.setColor(Color.BLACK);
        g2.drawString("Parada", x + 20, y + 5);
        
        // Arco
        y += 25;
        Set<String> uniqueLines = new HashSet<>();
        for (Arc arc : arcs) {
            if (uniqueLines.size() >= 3) break;
            if (uniqueLines.add(arc.lineId)) {
                g2.setColor(arc.color);
                g2.setStroke(new BasicStroke(2f));
                g2.drawLine(x, y, x + 15, y);
                g2.setColor(Color.BLACK);
                g2.drawString(arc.lineName, x + 20, y + 5);
                y += 20;
            }
        }
    }
    
    public void exportToJPG(String filename) throws IOException {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, WIDTH, HEIGHT);
        
        paint(g2);
        g2.dispose();
        
        ImageIO.write(image, "jpg", new File(filename));
        System.out.println("Grafo exportado a: " + filename);
    }

public void displayVelocityResults(VelocityResult[] results) {
    // Crear un mapa de velocidades por arco
    Map<String, Double> velocityByArc = new HashMap<>();
    for (VelocityResult result : results) {
        if (result.sampleCount > 0) {
            velocityByArc.put(result.arcId, result.averageVelocity);
        }
    }
    

    System.out.println("Velocidades cargadas para " + velocityByArc.size() + " arcos");
    repaint();
}
    
    static class LineStopData {
        int sequence;
        int orientation;
        String lineId;
        String stopId;
        
        LineStopData(int seq, int orient, String line, String stop) {
            this.sequence = seq;
            this.orientation = orient;
            this.lineId = line;
            this.stopId = stop;
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Uso: GraphVisualizer <directorio_datos> [archivo_salida.jpg]");
            System.out.println("Ejemplo: GraphVisualizer ./sitm-mio/data grafo_mio.jpg");
            System.exit(1);
        }
        
        String dataPath = args[0];
        String outputFile = args.length > 1 ? args[1] : "grafo_sitm_mio.jpg";
        
        try {
            GraphVisualizer visualizer = new GraphVisualizer();
            visualizer.loadData(dataPath);
            
            // Mostrar en ventana
            JFrame frame = new JFrame("Grafo SITM-MIO");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(visualizer);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            
            // Exportar a JPG
            visualizer.exportToJPG(outputFile);
            
            System.out.println("Visualización completada");
            System.out.println("Presiona Ctrl+C para cerrar");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
