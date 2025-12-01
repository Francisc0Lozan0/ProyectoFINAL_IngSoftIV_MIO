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
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.imageio.ImageIO;
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
        String arcId;
        double velocity; // Nueva: velocidad promedio
        
        Arc(String lineId, String lineName, Stop start, Stop end, int orientation, int sequence) {
            this.lineId = lineId;
            this.lineName = lineName;
            this.start = start;
            this.end = end;
            this.orientation = orientation;
            this.color = generateColor(lineId);
            this.velocity = 0.0;
            // Generar arcId igual que en RouteGraphBuilder
            this.arcId = String.format("ARC_%s_%s_%d_%d", 
                lineId, orientation == 0 ? "IDA" : "VTA", sequence, sequence + 1);
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
    private Map<String, Double> velocityByArc = new HashMap<>(); // Nueva: mapa de velocidades
    
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
    
    // Nuevo método: cargar velocidades desde VelocityResult[]
    public void loadVelocities(VelocityResult[] results) {
        velocityByArc.clear();
        
        if (results == null || results.length == 0) {
            System.out.println("⚠ No hay resultados de velocidad para mostrar");
            return;
        }
        
        System.out.println("\n🔍 DEBUG: Procesando velocidades...");
        int loadedCount = 0;
        for (VelocityResult result : results) {
            System.out.println("  - Result arcId: '" + result.arcId + "' velocity: " + 
                             result.averageVelocity + " m/s, samples: " + result.sampleCount);
            if (result.sampleCount > 0 && result.averageVelocity > 0) {
                velocityByArc.put(result.arcId, result.averageVelocity);
                loadedCount++;
            }
        }
        
        System.out.println("\n🔍 DEBUG: Arcos del grafo:");
        for (Arc arc : arcs) {
            System.out.println("  - Graph arcId: '" + arc.arcId + "'");
        }
        
        // Asignar velocidades a los arcos
        int matchedCount = 0;
        for (Arc arc : arcs) {
            if (velocityByArc.containsKey(arc.arcId)) {
                arc.velocity = velocityByArc.get(arc.arcId);
                matchedCount++;
                System.out.println("  ✓ MATCHED: " + arc.arcId + " = " + arc.velocity + " m/s");
            } else {
                System.out.println("  ✗ NO MATCH: " + arc.arcId);
            }
        }
        
        System.out.println("\n✓ Velocidades cargadas: " + loadedCount + " resultados");
        System.out.println("✓ Arcos coincidentes: " + matchedCount + " / " + arcs.size());
        repaint();
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
                        arcs.add(new Arc(lineId, lineName, start, end, orientation, sequence.get(i).sequence));
                    }
                }
            }
        }
    }
    
    private void calculatePositions() {
        if (stops.isEmpty()) return;
        
        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
        
        for (Stop stop : stops.values()) {
            minLat = Math.min(minLat, stop.latitude);
            maxLat = Math.max(maxLat, stop.latitude);
            minLon = Math.min(minLon, stop.longitude);
            maxLon = Math.max(maxLon, stop.longitude);
        }
        
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
        
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        
        // Título
        g2.setColor(Color.BLACK);
        g2.setFont(new Font("Arial", Font.BOLD, 24));
        g2.drawString("GRAFO DE RUTAS SITM-MIO - VELOCIDADES PROMEDIO", WIDTH / 2 - 300, 40);
        
        // Dibujar arcos con velocidades
        for (Arc arc : arcs) {
            if (arc.start.position != null && arc.end.position != null) {
                // Color según velocidad
                Color arcColor = getColorForVelocity(arc.velocity);
                g2.setColor(arcColor);
                g2.setStroke(new BasicStroke(3f));
                
                int x1 = (int) arc.start.position.getX();
                int y1 = (int) arc.start.position.getY();
                int x2 = (int) arc.end.position.getX();
                int y2 = (int) arc.end.position.getY();
                
                drawArrow(g2, x1, y1, x2, y2);
                
                // Dibujar velocidad en el medio del arco
                if (arc.velocity > 0) {
                    int midX = (x1 + x2) / 2;
                    int midY = (y1 + y2) / 2;
                    
                    g2.setFont(new Font("Arial", Font.BOLD, 11));
                    g2.setColor(Color.WHITE);
                    String velText = String.format("%.1f km/h", arc.velocity * 3.6);
                    
                    // Fondo para el texto
                    int textWidth = g2.getFontMetrics().stringWidth(velText);
                    g2.fillRect(midX - textWidth/2 - 3, midY - 10, textWidth + 6, 16);
                    
                    g2.setColor(Color.BLACK);
                    g2.drawString(velText, midX - textWidth/2, midY + 3);
                }
            }
        }
        
        // Dibujar paradas
        for (Stop stop : stops.values()) {
            if (stop.position != null) {
                int x = (int) stop.position.getX();
                int y = (int) stop.position.getY();
                
                g2.setColor(new Color(70, 130, 180));
                g2.fillOval(x - 6, y - 6, 12, 12);
                
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2f));
                g2.drawOval(x - 6, y - 6, 12, 12);
            }
        }
        
        // Leyenda con escala de colores
        drawLegendWithScale(g2);
        
        // Estadísticas
        drawStatistics(g2);
    }
    
    private Color getColorForVelocity(double velocity) {
        if (velocity <= 0) {
            return new Color(150, 150, 150, 180); // Gris para sin datos
        }
        
        double kmh = velocity * 3.6;
        
        // Escala de colores: rojo (lento) -> amarillo -> verde (rápido)
        if (kmh < 10) {
            return new Color(220, 20, 20, 200); // Rojo oscuro (muy lento)
        } else if (kmh < 20) {
            return new Color(255, 100, 0, 200); // Naranja (lento)
        } else if (kmh < 30) {
            return new Color(255, 200, 0, 200); // Amarillo (medio)
        } else if (kmh < 40) {
            return new Color(180, 220, 50, 200); // Verde-amarillo (bueno)
        } else {
            return new Color(50, 200, 50, 200); // Verde (rápido)
        }
    }
    
    private void drawArrow(Graphics2D g2, int x1, int y1, int x2, int y2) {
        g2.drawLine(x1, y1, x2, y2);
        
        double angle = Math.atan2(y2 - y1, x2 - x1);
        int arrowSize = 10;
        
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
    
    private void drawLegendWithScale(Graphics2D g2) {
        int x = WIDTH - 320;
        int y = 80;
        
        g2.setColor(new Color(255, 255, 255, 230));
        g2.fillRoundRect(x - 10, y - 30, 300, 200, 10, 10);
        
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(x - 10, y - 30, 300, 200, 10, 10);
        
        g2.setFont(new Font("Arial", Font.BOLD, 14));
        g2.drawString("ESCALA DE VELOCIDADES", x, y);
        
        y += 25;
        g2.setFont(new Font("Arial", Font.PLAIN, 12));
        
        // Escala de colores
        String[][] scale = {
            {"< 10 km/h", "220,20,20"},
            {"10-20 km/h", "255,100,0"},
            {"20-30 km/h", "255,200,0"},
            {"30-40 km/h", "180,220,50"},
            {"> 40 km/h", "50,200,50"},
            {"Sin datos", "150,150,150"}
        };
        
        for (String[] entry : scale) {
            String[] rgb = entry[1].split(",");
            g2.setColor(new Color(
                Integer.parseInt(rgb[0]),
                Integer.parseInt(rgb[1]),
                Integer.parseInt(rgb[2])
            ));
            g2.fillRect(x, y - 8, 30, 15);
            g2.setColor(Color.BLACK);
            g2.drawRect(x, y - 8, 30, 15);
            g2.drawString(entry[0], x + 40, y + 4);
            y += 22;
        }
    }
    
    private void drawStatistics(Graphics2D g2) {
        g2.setColor(Color.DARK_GRAY);
        g2.setFont(new Font("Arial", Font.PLAIN, 12));
        
        int arcsWithVelocity = 0;
        double totalVelocity = 0;
        double maxVel = 0, minVel = Double.MAX_VALUE;
        
        for (Arc arc : arcs) {
            if (arc.velocity > 0) {
                arcsWithVelocity++;
                totalVelocity += arc.velocity;
                maxVel = Math.max(maxVel, arc.velocity);
                minVel = Math.min(minVel, arc.velocity);
            }
        }
        
        double avgVel = arcsWithVelocity > 0 ? totalVelocity / arcsWithVelocity : 0;
        
        String stats = String.format(
            "Paradas: %d  |  Arcos: %d  |  Con velocidad: %d  |  Promedio: %.1f km/h  |  Máx: %.1f km/h  |  Mín: %.1f km/h",
            stops.size(), arcs.size(), arcsWithVelocity, avgVel * 3.6, maxVel * 3.6, minVel == Double.MAX_VALUE ? 0 : minVel * 3.6
        );
        
        g2.drawString(stats, 20, HEIGHT - 20);
    }
    
    public void exportToJPG(String filename) throws IOException {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, WIDTH, HEIGHT);
        
        paint(g2);
        g2.dispose();
        
        ImageIO.write(image, "jpg", new File(filename));
        System.out.println("✓ Grafo exportado a: " + filename);
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
}