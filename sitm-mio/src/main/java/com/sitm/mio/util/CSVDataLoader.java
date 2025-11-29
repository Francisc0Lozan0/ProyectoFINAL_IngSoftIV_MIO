package com.sitm.mio.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import SITM.MIO.Arc;
import SITM.MIO.BusDatagram;
import SITM.MIO.Stop;

public class CSVDataLoader {
    
    public static BusDatagram[] loadDatagrams(String filePath, int limit) throws IOException {
        List<BusDatagram> datagrams = new ArrayList<>();
        int count = 0;
        
        try (BufferedReader br = Files.newBufferedReader(Paths.get(filePath))) {
            String line;
            boolean firstLine = true;
            
            while ((line = br.readLine()) != null && count < limit) {
                if (firstLine) {
                    firstLine = false;
                    continue;
                }
                
                BusDatagram dgram = parseDatagram(line);
                if (dgram != null) {
                    datagrams.add(dgram);
                    count++;
                }
                
                if (count % 100000 == 0) {
                    System.out.println("Loaded " + count + " datagrams");
                }
            }
        }
        
        System.out.println("Total datagrams loaded: " + datagrams.size());
        return datagrams.toArray(new BusDatagram[0]);
    }

    private static BusDatagram parseDatagram(String line) {
        try {
            String[] parts = line.split(",");
            if (parts.length < 11) return null;
            
            BusDatagram dgram = new BusDatagram();
            dgram.eventType = Integer.parseInt(parts[0].trim());
            dgram.stopId = parts[2].trim();
            dgram.odometer = Double.parseDouble(parts[3].trim());
            dgram.latitude = parseCoordinate(parts[4].trim());
            dgram.longitude = parseCoordinate(parts[5].trim());
            dgram.lineId = parts[6].trim();   
            dgram.tripId = parts[7].trim();    
            dgram.datagramDate = parts[9].trim(); 
            dgram.busId = parts[10].trim();    
            
            return dgram;
        } catch (Exception e) {
            System.err.println("Error parsing datagram: " + line);
            return null;
        }
    }

    private static double parseCoordinate(String coord) {
        try {
            return Double.parseDouble(coord.trim());
        } catch (NumberFormatException e) {
            System.err.println("Error parsing coordinate: " + coord);
            return 0.0;
        }
    }

    public static Stop[] loadStops(String filePath) throws IOException {
        List<Stop> stops = new ArrayList<>();
        
        try (BufferedReader br = Files.newBufferedReader(Paths.get(filePath))) {
            String line;
            boolean firstLine = true;
            
            while ((line = br.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue;
                }
                
                Stop stop = parseStop(line);
                if (stop != null) {
                    stops.add(stop);
                }
            }
        }
        
        System.out.println("Total stops loaded: " + stops.size());
        return stops.toArray(new Stop[0]);
    }

    private static Stop parseStop(String line) {
        try {
            String cleaned = line.replaceAll("\"", "");
            String[] parts = cleaned.split(",");
            
            if (parts.length < 8) return null;
            
            Stop stop = new Stop();
            stop.stopId = parts[0].trim();
            stop.shortName = parts[2].trim();
            stop.longName = parts[3].trim();
            stop.longitude = Double.parseDouble(parts[7].trim());
            stop.latitude = Double.parseDouble(parts[6].trim());
            
            return stop;
        } catch (Exception e) {
            System.err.println("Error parsing stop: " + line);
            return null;
        }
    }

    public static SITM.MIO.LineStop[] loadLineStops(String filePath) throws IOException {
        List<SITM.MIO.LineStop> lineStops = new ArrayList<>();
        
        try (BufferedReader br = Files.newBufferedReader(Paths.get(filePath))) {
            String line;
            boolean firstLine = true;
            
            while ((line = br.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue;
                }
                
                SITM.MIO.LineStop lineStop = parseLineStop(line);
                if (lineStop != null) {
                    lineStops.add(lineStop);
                }
            }
        }
        
        System.out.println("Total line stops loaded: " + lineStops.size());
        return lineStops.toArray(new SITM.MIO.LineStop[0]);
    }

    private static SITM.MIO.LineStop parseLineStop(String line) {
        try {
            String cleaned = line.replaceAll("\"", "");
            String[] parts = cleaned.split(",");
            
            if (parts.length < 6) return null;
            
            SITM.MIO.LineStop lineStop = new SITM.MIO.LineStop();
            lineStop.lineId = parts[3].trim();
            lineStop.stopId = parts[4].trim();
            lineStop.stopSequence = Integer.parseInt(parts[1].trim());
            
            return lineStop;
        } catch (Exception e) {
            System.err.println("Error parsing line stop: " + line);
            return null;
        }
    }

    public static Arc[] buildArcs(SITM.MIO.LineStop[] lineStops, Stop[] stops) {
        // Agrupar por línea Y orientación
        Map<String, Map<Integer, List<SITM.MIO.LineStop>>> stopsByLineAndOrientation = new HashMap<>();
        List<Arc> arcs = new ArrayList<>();
        
        // Primero necesitamos cargar la orientación desde linestops.csv
        // Vamos a asumir orientación 0 (IDA) por defecto
        // Para una implementación completa, deberías cargar la columna ORIENTATION del CSV
        
        try (BufferedReader br = Files.newBufferedReader(Paths.get("./data/linestops.csv"))) {
            String line;
            boolean firstLine = true;
            
            while ((line = br.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue;
                }
                
                String[] parts = line.split(",");
                if (parts.length < 6) continue;
                
                int sequence = Integer.parseInt(parts[1].trim());
                int orientation = Integer.parseInt(parts[2].trim()); // CRITICAL: Leer la orientación
                String lineId = parts[3].trim();
                String stopId = parts[4].trim();
                
                SITM.MIO.LineStop ls = new SITM.MIO.LineStop();
                ls.lineId = lineId;
                ls.stopId = stopId;
                ls.stopSequence = sequence;
                
                stopsByLineAndOrientation
                    .computeIfAbsent(lineId, k -> new HashMap<>())
                    .computeIfAbsent(orientation, k -> new ArrayList<>())
                    .add(ls);
            }
        } catch (Exception e) {
            System.err.println("Error loading orientations: " + e.getMessage());
        }
        
        // Construir arcos con orientación incluida
        for (Map.Entry<String, Map<Integer, List<SITM.MIO.LineStop>>> lineEntry : stopsByLineAndOrientation.entrySet()) {
            String lineId = lineEntry.getKey();
            
            for (Map.Entry<Integer, List<SITM.MIO.LineStop>> orientEntry : lineEntry.getValue().entrySet()) {
                int orientation = orientEntry.getKey();
                List<SITM.MIO.LineStop> lineStopList = orientEntry.getValue();
                lineStopList.sort(Comparator.comparingInt(ls -> ls.stopSequence));
                
                for (int i = 0; i < lineStopList.size() - 1; i++) {
                    SITM.MIO.LineStop start = lineStopList.get(i);
                    SITM.MIO.LineStop end = lineStopList.get(i+1);
                    
                    Arc arc = new Arc();
                    // CRITICAL: Incluir orientación en el arcId
                    String orientStr = orientation == 0 ? "IDA" : "VTA";
                    arc.arcId = String.format("ARC_%s_%s_%d_%d", 
                        lineId, orientStr, start.stopSequence, end.stopSequence);
                    arc.lineId = lineId;
                    arc.startStopId = start.stopId;
                    arc.endStopId = end.stopId;
                    arc.orderIndex = start.stopSequence;
                    
                    Stop startStop = findStop(stops, start.stopId);
                    Stop endStop = findStop(stops, end.stopId);
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
        
        System.out.println("Total arcs built: " + arcs.size());
        
        // DEBUG: Imprimir algunos arcos
        System.out.println("Sample arcs created:");
        for (int i = 0; i < Math.min(5, arcs.size()); i++) {
            Arc arc = arcs.get(i);
            System.out.println("  " + arc.arcId + " (" + arc.startStopId + " -> " + arc.endStopId + ")");
        }
        
        return arcs.toArray(new Arc[0]);
    }

    private static Stop findStop(Stop[] stops, String stopId) {
        for (Stop stop : stops) {
            if (stop.stopId.equals(stopId)) {
                return stop;
            }
        }
        return null;
    }

    private static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000;
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }
}