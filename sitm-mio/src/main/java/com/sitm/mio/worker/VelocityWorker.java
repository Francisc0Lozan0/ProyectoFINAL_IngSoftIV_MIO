package com.sitm.mio.worker;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import Ice.Current;
import SITM.MIO.Arc;
import SITM.MIO.BusDatagram;
import SITM.MIO.ProcessingTask;
import SITM.MIO.StreamingWindow;
import SITM.MIO.VelocityResult;
import SITM.MIO._WorkerDisp;

public class VelocityWorker extends _WorkerDisp {
    private final String workerId;
    private static final DateTimeFormatter DATE_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public VelocityWorker(String workerId) {
        this.workerId = workerId;
        System.out.println("Velocity Worker initialized: " + workerId);
    }

    @Override
    public VelocityResult processTask(ProcessingTask task, Current current) {
        System.out.println("Worker " + workerId + " processing task " + task.taskId + 
                         " with " + task.datagrams.length + " datagrams");
        
        long startTime = System.currentTimeMillis();
        
        try {
            Map<String, List<BusDatagram>> tripDatagrams = groupDatagramsByTrip(task.datagrams);
            Map<String, List<Double>> arcVelocities = calculateArcVelocities(tripDatagrams, task.arcs);

            // Persistir a DB si está disponible
            try {
                if (com.sitm.mio.persistence.DBConnection.isAvailable()) {
                    com.sitm.mio.persistence.VelocityDao dao = new com.sitm.mio.persistence.VelocityDao();
                    String ym = com.sitm.mio.persistence.VelocityDao.currentYearMonth();
                    
                    for (Map.Entry<String, List<Double>> e : arcVelocities.entrySet()) {
                        String arcId = e.getKey();
                        List<Double> vals = e.getValue();
                        long samples = vals.size();
                        double avg = 0.0;
                        if (samples > 0) {
                            double sum = 0.0;
                            for (Double v : vals) sum += v;
                            avg = sum / samples;
                        }
                        
                        String lineId = extractLineIdFromArc(arcId);
                        dao.upsert(ym, lineId, arcId, avg, samples);
                    }
                    System.out.println("Worker " + workerId + " - Results persisted to database");
                } else {
                    System.out.println("Worker " + workerId + " - DB not available, results computed in-memory only");
                }
            } catch (Exception ex) {
                System.err.println("Worker DB persist error (non-critical): " + ex.getMessage());
            }

            // Serializar los datos de velocidad en periodStart
            // Formato: "arcId1:velocity1:samples1|arcId2:velocity2:samples2|..."
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, List<Double>> entry : arcVelocities.entrySet()) {
                String arcId = entry.getKey();
                List<Double> velocities = entry.getValue();
                
                if (velocities.size() > 0) {
                    double sum = 0.0;
                    for (Double v : velocities) sum += v;
                    double avg = sum / velocities.size();
                    
                    if (sb.length() > 0) sb.append("|");
                    sb.append(arcId).append(":").append(avg).append(":").append(velocities.size());
                }
            }
            
            VelocityResult aggregatedResult = new VelocityResult();
            aggregatedResult.arcId = "aggregated-" + task.taskId;
            aggregatedResult.periodStart = sb.toString();
            aggregatedResult.processingTime = System.currentTimeMillis() - startTime;
            aggregatedResult.sampleCount = arcVelocities.values().stream()
                .mapToInt(List::size).sum();
            
            // Calcular velocidad promedio global
            double totalVelocity = 0.0;
            int totalSamples = 0;
            for (List<Double> velocities : arcVelocities.values()) {
                for (Double v : velocities) {
                    totalVelocity += v;
                    totalSamples++;
                }
            }
            aggregatedResult.averageVelocity = totalSamples > 0 ? totalVelocity / totalSamples : 0.0;

            System.out.println("Worker " + workerId + " completed: " + arcVelocities.size() + 
                             " arcs, " + totalSamples + " samples");

            return aggregatedResult;
            
        } catch (Exception e) {
            System.err.println("Error in worker " + workerId + ": " + e.getMessage());
            e.printStackTrace();
            VelocityResult errorResult = new VelocityResult();
            errorResult.arcId = "error-" + task.taskId;
            errorResult.averageVelocity = 0.0;
            errorResult.sampleCount = 0;
            errorResult.processingTime = System.currentTimeMillis() - startTime;
            errorResult.periodStart = "";
            errorResult.periodEnd = "";
            return errorResult;
        }
    }

    @Override
    public VelocityResult processStreamingWindow(StreamingWindow window, Current current) {
        System.out.println("Worker " + workerId + " processing streaming window " + window.windowId);
        long startTime = System.currentTimeMillis();
        
        try {
            ProcessingTask task = new ProcessingTask();
            task.taskId = window.windowId;
            task.datagrams = window.datagrams;
            
            Map<String, List<BusDatagram>> tripDatagrams = groupDatagramsByTrip(window.datagrams);
            Map<String, List<Double>> arcVelocities = calculateArcVelocities(tripDatagrams, new Arc[0]);
            
            VelocityResult result = new VelocityResult();
            result.arcId = "streaming-" + window.windowId;
            result.processingTime = System.currentTimeMillis() - startTime;
            result.periodStart = "";
            result.periodEnd = "";
            
            double totalVelocity = 0.0;
            int totalSamples = 0;
            for (List<Double> velocities : arcVelocities.values()) {
                for (Double v : velocities) {
                    totalVelocity += v;
                    totalSamples++;
                }
            }
            
            result.sampleCount = totalSamples;
            result.averageVelocity = totalSamples > 0 ? totalVelocity / totalSamples : 0.0;
            
            return result;
        } catch (Exception e) {
            System.err.println("Error in streaming worker " + workerId + ": " + e.getMessage());
            VelocityResult errorResult = new VelocityResult();
            errorResult.arcId = "error-streaming-" + window.windowId;
            errorResult.averageVelocity = 0.0;
            errorResult.sampleCount = 0;
            errorResult.processingTime = System.currentTimeMillis() - startTime;
            errorResult.periodStart = "";
            errorResult.periodEnd = "";
            return errorResult;
        }
    }

    @Override
    public boolean isAlive(Current current) {
        return true;
    }

    private Map<String, List<BusDatagram>> groupDatagramsByTrip(BusDatagram[] datagrams) {
        Map<String, List<BusDatagram>> grouped = new HashMap<>();
        
        for (BusDatagram dgram : datagrams) {
            String tripKey = dgram.busId + "-" + dgram.tripId + "-" + dgram.lineId;
            grouped.computeIfAbsent(tripKey, k -> new ArrayList<>()).add(dgram);
        }
        
        for (List<BusDatagram> tripData : grouped.values()) {
            tripData.sort((d1, d2) -> {
                try {
                    LocalDateTime t1 = LocalDateTime.parse(d1.datagramDate, DATE_FORMATTER);
                    LocalDateTime t2 = LocalDateTime.parse(d2.datagramDate, DATE_FORMATTER);
                    return t1.compareTo(t2);
                } catch (Exception e) {
                    return 0;
                }
            });
        }
        
        return grouped;
    }

    private Map<String, List<Double>> calculateArcVelocities(
            Map<String, List<BusDatagram>> tripDatagrams, Arc[] arcs) {
        
        Map<String, List<Double>> velocitiesByArc = new HashMap<>();
        
        // Crear mapa de arcos por lineId-startStop-endStop para búsqueda rápida
        Map<String, Arc> arcLookup = new HashMap<>();
        for (Arc arc : arcs) {
            String key = arc.lineId + "-" + arc.startStopId + "-" + arc.endStopId;
            arcLookup.put(key, arc);
        }
        
        System.out.println("Worker " + workerId + " - Arc lookup created with " + arcLookup.size() + " entries");
        
        for (List<BusDatagram> tripData : tripDatagrams.values()) {
            if (tripData.size() < 2) continue;
            
            for (int i = 0; i < tripData.size() - 1; i++) {
                BusDatagram d1 = tripData.get(i);
                BusDatagram d2 = tripData.get(i + 1);
                
                // Buscar el arco correspondiente usando el lookup
                String lookupKey = d1.lineId + "-" + d1.stopId + "-" + d2.stopId;
                Arc arc = arcLookup.get(lookupKey);
                
                if (arc != null && arc.distance > 0) {
                    // USAR EL ARCID DEL ARCO DIRECTAMENTE
                    double velocity = calculateVelocity(d1, d2, arc.distance);
                    if (velocity > 0 && velocity < 50) {
                        velocitiesByArc.computeIfAbsent(arc.arcId, k -> new ArrayList<>()).add(velocity);
                        System.out.println("Worker " + workerId + " - Matched arc: " + arc.arcId + 
                                         " velocity: " + String.format("%.2f m/s", velocity));
                    }
                } else {
                    System.out.println("Worker " + workerId + " - No arc found for: " + lookupKey);
                }
            }
        }
        
        System.out.println("Worker " + workerId + " - Calculated velocities for " + 
                         velocitiesByArc.size() + " arcs");
        
        return velocitiesByArc;
    }

    private double calculateVelocity(BusDatagram d1, BusDatagram d2, double arcDistance) {
        try {
            LocalDateTime time1 = LocalDateTime.parse(d1.datagramDate, DATE_FORMATTER);
            LocalDateTime time2 = LocalDateTime.parse(d2.datagramDate, DATE_FORMATTER);
            
            long timeDiff = java.time.Duration.between(time1, time2).getSeconds();
            if (timeDiff <= 0) return 0.0;
            
            double distance;
            if (d1.odometer > 0 && d2.odometer > 0 && d2.odometer > d1.odometer) {
                distance = d2.odometer - d1.odometer;
            } else {
                distance = arcDistance;
            }
            
            return distance / timeDiff;
            
        } catch (Exception e) {
            return 0.0;
        }
    }

    private String extractLineIdFromArc(String arcId) {
        try {
            if (arcId != null && arcId.contains("_")) {
                String[] parts = arcId.split("_");
                if (parts.length >= 2) return parts[1];
            }
        } catch (Exception ex) {}
        return "unknown";
    }
}