package com.sitm.mio.graphs;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class RouteGraphBuilder {
    
    public static class Stop {
        String stopId;
        String shortName;
        String longName;
        double latitude;
        double longitude;
        
        public String toString() {
            return String.format("%s [%s] (%.6f, %.6f)", shortName, stopId, latitude, longitude);
        }
    }
    
    public static class Line {
        String lineId;
        String shortName;
        String description;
        
        public String toString() {
            return String.format("%s - %s", shortName, description);
        }
    }
    
    public static class LineStop {
        String lineId;
        String stopId;
        int sequence;
        int orientation; 
        
        public String toString() {
            return String.format("Seq %d: Stop %s (%s)", sequence, stopId, orientation == 0 ? "IDA" : "VUELTA");
        }
    }
    
    public static class Arc {
        String arcId;
        String lineId;
        String startStopId;
        String endStopId;
        int sequence;
        int orientation;
        double distance;
        
        public String toString() {
            return String.format("Arc %s: %s -> %s (seq %d, %.2fm)", 
                arcId, startStopId, endStopId, sequence, distance);
        }
    }
    
    private Map<String, Stop> stops = new HashMap<>();
    private Map<String, Line> lines = new HashMap<>();
    private List<LineStop> lineStops = new ArrayList<>();
    private List<Arc> arcs = new ArrayList<>();
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Uso: RouteGraphBuilder <directorio_datos>");
            System.out.println("Ejemplo: RouteGraphBuilder ./sitm-mio/data");
            System.exit(1);
        }
        
        RouteGraphBuilder builder = new RouteGraphBuilder();
        String dataPath = args[0];
        
        try {
            System.out.println("=".repeat(80));
            System.out.println("CONSTRUCCIÓN DE GRAFO DE RUTAS SITM-MIO");
            System.out.println("=".repeat(80));
            System.out.println();
            
            builder.loadStops(dataPath + "/stops.csv");
            builder.loadLines(dataPath + "/lines.csv");
            builder.loadLineStops(dataPath + "/linestops.csv");
            
            builder.buildArcs();
            
            builder.displayGraph();
            
            System.out.println();
            System.out.println("=".repeat(80));
            System.out.println("CONSTRUCCIÓN COMPLETADA");
            System.out.println("=".repeat(80));
            
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void loadStops(String filePath) throws IOException {
        System.out.println("Cargando paradas desde: " + filePath);
        
        try (BufferedReader br = Files.newBufferedReader(Paths.get(filePath))) {
            String line;
            boolean firstLine = true;
            int count = 0;
            
            while ((line = br.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue;
                }
                
                String[] parts = line.split(",");
                if (parts.length < 8) continue;
                
                Stop stop = new Stop();
                stop.stopId = parts[0].trim();
                stop.shortName = parts[2].trim();
                stop.longName = parts[3].trim();
                stop.latitude = Double.parseDouble(parts[7].trim());
                stop.longitude = Double.parseDouble(parts[6].trim());
                
                stops.put(stop.stopId, stop);
                count++;
            }
            
            System.out.println("  ✓ Cargadas " + count + " paradas");
        }
    }
    
    private void loadLines(String filePath) throws IOException {
        System.out.println("Cargando rutas desde: " + filePath);
        
        try (BufferedReader br = Files.newBufferedReader(Paths.get(filePath))) {
            String line;
            boolean firstLine = true;
            int count = 0;
            
            while ((line = br.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue;
                }
                
                String[] parts = line.split(",");
                if (parts.length < 4) continue;
                
                Line route = new Line();
                route.lineId = parts[0].trim();
                route.shortName = parts[2].trim();
                route.description = parts[3].trim();
                
                lines.put(route.lineId, route);
                count++;
            }
            
            System.out.println("  ✓ Cargadas " + count + " rutas");
        }
    }
    
    private void loadLineStops(String filePath) throws IOException {
        System.out.println("Cargando paradas por ruta desde: " + filePath);
        
        try (BufferedReader br = Files.newBufferedReader(Paths.get(filePath))) {
            String line;
            boolean firstLine = true;
            int count = 0;
            
            while ((line = br.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue;
                }
                
                String[] parts = line.split(",");
                if (parts.length < 5) continue;
                
                LineStop ls = new LineStop();
                ls.sequence = Integer.parseInt(parts[1].trim());
                ls.orientation = Integer.parseInt(parts[2].trim());
                ls.lineId = parts[3].trim();
                ls.stopId = parts[4].trim();
                
                lineStops.add(ls);
                count++;
            }
            
            System.out.println("  ✓ Cargadas " + count + " paradas por ruta");
        }
    }
    
    private void buildArcs() {
        System.out.println();
        System.out.println("Construyendo arcos del grafo...");
        
        Map<String, Map<Integer, List<LineStop>>> groupedByLineAndOrientation = new HashMap<>();
        
        for (LineStop ls : lineStops) {
            String key = ls.lineId;
            groupedByLineAndOrientation
                .computeIfAbsent(key, k -> new HashMap<>())
                .computeIfAbsent(ls.orientation, k -> new ArrayList<>())
                .add(ls);
        }
        
        // Construir arcos para cada línea y orientación
        for (Map.Entry<String, Map<Integer, List<LineStop>>> lineEntry : groupedByLineAndOrientation.entrySet()) {
            String lineId = lineEntry.getKey();
            
            for (Map.Entry<Integer, List<LineStop>> orientEntry : lineEntry.getValue().entrySet()) {
                int orientation = orientEntry.getKey();
                List<LineStop> stopsInSequence = orientEntry.getValue();
                
                stopsInSequence.sort(Comparator.comparingInt(ls -> ls.sequence));
                
                for (int i = 0; i < stopsInSequence.size() - 1; i++) {
                    LineStop start = stopsInSequence.get(i);
                    LineStop end = stopsInSequence.get(i + 1);
                    
                    Arc arc = new Arc();
                    arc.lineId = lineId;
                    arc.startStopId = start.stopId;
                    arc.endStopId = end.stopId;
                    arc.sequence = start.sequence;
                    arc.orientation = orientation;
                    arc.arcId = String.format("ARC_%s_%s_%d_%d", 
                        lineId, orientation == 0 ? "IDA" : "VTA", start.sequence, end.sequence);
                    
                    Stop startStop = stops.get(start.stopId);
                    Stop endStop = stops.get(end.stopId);
                    
                    if (startStop != null && endStop != null) {
                        arc.distance = calculateDistance(
                            startStop.latitude, startStop.longitude,
                            endStop.latitude, endStop.longitude
                        );
                    }
                    
                    arcs.add(arc);
                }
            }
        }
        
        System.out.println("  ✓ Construidos " + arcs.size() + " arcos");
    }

    // Public API to load all graph data and expose results for other modules
    public void loadDataDirectory(String dataPath) throws IOException {
        loadStops(dataPath + "/stops.csv");
        loadLines(dataPath + "/lines.csv");
        loadLineStops(dataPath + "/linestops.csv");
        buildArcs();
    }

    public List<Arc> getArcs() {
        return arcs;
    }

    public Map<String, Stop> getStops() {
        return stops;
    }
    
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; 
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }
    
    private void displayGraph() {
        System.out.println();
        System.out.println("=".repeat(80));
        System.out.println("GRAFO DE ARCOS POR RUTA (ORDENADO Y EN SECUENCIA)");
        System.out.println("=".repeat(80));
        
        Map<String, List<Arc>> arcsByLine = new HashMap<>();
        for (Arc arc : arcs) {
            arcsByLine.computeIfAbsent(arc.lineId, k -> new ArrayList<>()).add(arc);
        }
        
        for (String lineId : arcsByLine.keySet()) {
            Line line = lines.get(lineId);
            List<Arc> lineArcs = arcsByLine.get(lineId);
            
            System.out.println();
            System.out.println("─".repeat(80));
            System.out.println("RUTA: " + (line != null ? line : "ID: " + lineId));
            System.out.println("─".repeat(80));
            
            Map<Integer, List<Arc>> arcsByOrientation = new HashMap<>();
            for (Arc arc : lineArcs) {
                arcsByOrientation.computeIfAbsent(arc.orientation, k -> new ArrayList<>()).add(arc);
            }
            if (arcsByOrientation.containsKey(0)) {
                System.out.println();
                System.out.println("  → SENTIDO IDA (Orientation 0):");
                System.out.println();
                
                List<Arc> idaArcs = arcsByOrientation.get(0);
                idaArcs.sort(Comparator.comparingInt(a -> a.sequence));
                
                displayArcSequence(idaArcs);
            }
            
            // Mostrar VUELTA
            if (arcsByOrientation.containsKey(1)) {
                System.out.println();
                System.out.println("  ← SENTIDO VUELTA (Orientation 1):");
                System.out.println();
                
                List<Arc> vueltaArcs = arcsByOrientation.get(1);
                vueltaArcs.sort(Comparator.comparingInt(a -> a.sequence));
                
                displayArcSequence(vueltaArcs);
            }
        }
        
        System.out.println();
        System.out.println("─".repeat(80));
        System.out.println("RESUMEN:");
        System.out.println("  • Total rutas: " + lines.size());
        System.out.println("  • Total paradas: " + stops.size());
        System.out.println("  • Total arcos: " + arcs.size());
        System.out.println("─".repeat(80));
    }
    
    private void displayArcSequence(List<Arc> arcs) {
        for (int i = 0; i < arcs.size(); i++) {
            Arc arc = arcs.get(i);
            Stop startStop = stops.get(arc.startStopId);
            Stop endStop = stops.get(arc.endStopId);
            
            System.out.printf("    %2d. [%s] %s  →  %s  (%.2f m)%n",
                i + 1,
                arc.arcId,
                startStop != null ? startStop.shortName : arc.startStopId,
                endStop != null ? endStop.shortName : arc.endStopId,
                arc.distance
            );
            
            if (i == 0) {
                System.out.println("        Parada inicial: " + (startStop != null ? startStop : arc.startStopId));
            }
            if (i == arcs.size() - 1) {
                System.out.println("        Parada final: " + (endStop != null ? endStop : arc.endStopId));
            }
        }
        
        // Calcular distancia total
        double totalDistance = arcs.stream().mapToDouble(a -> a.distance).sum();
        System.out.println();
        System.out.printf("    Distancia total: %.2f m (%.2f km)%n", totalDistance, totalDistance / 1000);
        System.out.printf("    Número de arcos: %d%n", arcs.size());
    }
}
