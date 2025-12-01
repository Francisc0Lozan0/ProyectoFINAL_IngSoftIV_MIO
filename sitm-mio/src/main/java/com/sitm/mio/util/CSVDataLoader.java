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

/**
 * Cargador de datos CSV en FORMATO REAL de SITM-MIO
 */
public class CSVDataLoader {
    
    /**
     * Cargar datagramas del CSV REAL
     * Formato: eventType,date,stopId,odometer,lat,lon,taskId,lineId,tripId,unknown,timestamp,busId
     */
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

    /**
     * PARSEAR DATAGRAMA CON FORMATO REAL
     * 
     * Columnas:
     * 0: eventType
     * 1: registerdate
     * 2: stopId
     * 3: odometer (metros)
     * 4: latitude (dividir por 10,000,000)
     * 5: longitude (dividir por 10,000,000)
     * 6: taskId
     * 7: lineId
     * 8: tripId
     * 9: unknown1
     * 10: datagramDate ("2019-05-27 20:14:43")
     * 11: busId
     */
    private static BusDatagram parseDatagram(String line) {
        try {
            line = line.replace("\"", "");
            String[] parts = line.split(",");
            if (parts.length < 12) return null;
            
            BusDatagram dgram = new BusDatagram();
            dgram.eventType = Integer.parseInt(parts[0].trim());
            dgram.stopId = parts[2].trim();
            dgram.odometer = parseDouble(parts[3].trim());
            dgram.latitude = parseDouble(parts[4].trim()) / 10000000.0;
            dgram.longitude = parseDouble(parts[5].trim()) / 10000000.0;
            dgram.lineId = parts[7].trim();
            dgram.tripId = parts[8].trim();
            dgram.datagramDate = parts[10].trim();
            dgram.busId = parts[11].trim();
            
            // Validar coordenadas de Cali
            if (dgram.latitude < 3.0 || dgram.latitude > 4.0 ||
                dgram.longitude > -76.0 || dgram.longitude < -77.0) {
                return null;
            }
            
            return dgram;
        } catch (Exception e) {
            return null;
        }
    }

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * Cargar paradas
     * Formato: LONGNAME,GPS_X,GPS_Y,STOPID,PLANVERSIONID,SHORTNAME,DECIMALLONG,DECIMALLAT
     */
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

    /**
     * PARSEAR STOP
     * Columnas: LONGNAME,GPS_X,GPS_Y,STOPID,PLANVERSIONID,SHORTNAME,DECIMALLONG,DECIMALLAT
     */
    private static Stop parseStop(String line) {
        try {
            String cleaned = line.replaceAll("\"", "");
            String[] parts = cleaned.split(",");
            
            if (parts.length < 8) return null;
            
            Stop stop = new Stop();
            stop.stopId = parts[3].trim();        // STOPID
            stop.shortName = parts[5].trim();     // SHORTNAME
            stop.longName = parts[0].trim();      // LONGNAME
            stop.longitude = Double.parseDouble(parts[6].trim()); // DECIMALLONG
            stop.latitude = Double.parseDouble(parts[7].trim());  // DECIMALLAT
            
            return stop;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Cargar LineStops
     * Formato: LINESTOPID,STOPSEQUENCE,ORIENTATION,LINEID,STOPID,PLANVERSIONID,LINEVARIANT,LINEVARIANTTYPE
     */
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

    /**
     * PARSEAR LINESTOP
     * Columnas: LINESTOPID,STOPSEQUENCE,ORIENTATION,LINEID,STOPID,PLANVERSIONID,LINEVARIANT,LINEVARIANTTYPE
     */
    private static SITM.MIO.LineStop parseLineStop(String line) {
        try {
            String cleaned = line.replaceAll("\"", "");
            String[] parts = cleaned.split(",");
            
            if (parts.length < 5) return null;
            
            SITM.MIO.LineStop lineStop = new SITM.MIO.LineStop();
            lineStop.stopSequence = Integer.parseInt(parts[1].trim()); // STOPSEQUENCE
            lineStop.lineId = parts[3].trim();                         // LINEID
            lineStop.stopId = parts[4].trim();                         // STOPID
            
            return lineStop;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Construir arcos desde LineStops
     */
    public static Arc[] buildArcs(SITM.MIO.LineStop[] lineStops, Stop[] stops) {
        Map<String, Map<Integer, List<SITM.MIO.LineStop>>> stopsByLineAndOrientation = new HashMap<>();
        List<Arc> arcs = new ArrayList<>();
        
        // Leer orientación desde linestops.csv
        try (BufferedReader br = Files.newBufferedReader(Paths.get("./data/linestops.csv"))) {
            String line;
            boolean firstLine = true;
            
            while ((line = br.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue;
                }
                
                String[] parts = line.split(",");
                if (parts.length < 5) continue;
                
                int sequence = Integer.parseInt(parts[1].trim());
                int orientation = Integer.parseInt(parts[2].trim());
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
        
        // Construir arcos
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