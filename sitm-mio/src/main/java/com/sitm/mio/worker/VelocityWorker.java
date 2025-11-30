package com.sitm.mio.worker;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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

    // FORMATO DE FECHA REAL: "2019-05-27 20:14:43"
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
            // Agrupar datagramas por viaje (trip)
            Map<String, List<BusDatagram>> tripDatagrams = groupDatagramsByTrip(task.datagrams);

            // Calcular velocidades por arco usando ODÓMETRO
            Map<String, List<Double>> arcVelocities = calculateArcVelocitiesWithOdometer(tripDatagrams);

            // Persistir a DB si está disponible
            persistToDatabase(arcVelocities);

            // Retornar resultado agregado
            VelocityResult aggregatedResult = buildAggregatedResult(
                    task.taskId, arcVelocities, startTime);

            System.out.println("Worker " + workerId + " completed: " +
                    arcVelocities.size() + " arcs processed");

            return aggregatedResult;

        } catch (Exception e) {
            System.err.println("Error in worker " + workerId + ": " + e.getMessage());
            e.printStackTrace();
            return createErrorResult(task.taskId, startTime);
        }
    }

    @Override
    public VelocityResult processStreamingWindow(StreamingWindow window, Current current) {
        System.out.println("Worker " + workerId + " processing streaming window " + window.windowId);
        long startTime = System.currentTimeMillis();

        try {
            Map<String, List<BusDatagram>> tripDatagrams = groupDatagramsByTrip(window.datagrams);
            Map<String, List<Double>> arcVelocities = calculateArcVelocitiesWithOdometer(tripDatagrams);

            return buildAggregatedResult(window.windowId, arcVelocities, startTime);
        } catch (Exception e) {
            System.err.println("Error in streaming worker " + workerId + ": " + e.getMessage());
            return createErrorResult("streaming-" + window.windowId, startTime);
        }
    }

    @Override
    public boolean isAlive(Current current) {
        return true;
    }

    /**
     * Agrupa datagramas por viaje único (busId + tripId + lineId)
     * y los ordena cronológicamente por datagramDate
     */
    private Map<String, List<BusDatagram>> groupDatagramsByTrip(BusDatagram[] datagrams) {
        Map<String, List<BusDatagram>> grouped = new HashMap<>();

        for (BusDatagram dgram : datagrams) {
            // Clave única por viaje
            String tripKey = dgram.busId + "-" + dgram.tripId + "-" + dgram.lineId;
            grouped.computeIfAbsent(tripKey, k -> new ArrayList<>()).add(dgram);
        }

        // Ordenar cada viaje por timestamp
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

    /**
     * CÁLCULO DE VELOCIDAD USANDO ODÓMETRO REAL
     * 
     * Fórmula: velocidad = (odometer2 - odometer1) / (tiempo2 - tiempo1)
     * 
     * Donde:
     * - odometer está en METROS (distancia acumulada del bus)
     * - tiempo en SEGUNDOS (diferencia entre datagramDate)
     */
    private Map<String, List<Double>> calculateArcVelocitiesWithOdometer(
            Map<String, List<BusDatagram>> tripDatagrams) {

        Map<String, List<Double>> velocitiesByArc = new HashMap<>();

        for (List<BusDatagram> tripData : tripDatagrams.values()) {
            if (tripData.size() < 2)
                continue;

            // Procesar cada par consecutivo de datagramas en el viaje
            for (int i = 0; i < tripData.size() - 1; i++) {
                BusDatagram d1 = tripData.get(i);
                BusDatagram d2 = tripData.get(i + 1);

                try {
                    // CALCULAR VELOCIDAD CON ODÓMETRO
                    double velocity = calculateVelocityUsingOdometer(d1, d2);

                    if (velocity > 0 && velocity < 50) { // Filtro: 0-50 m/s (~0-180 km/h)
                        // Crear arcId basado en la secuencia de paradas
                        String arcId = createArcId(d1, d2);
                        velocitiesByArc.computeIfAbsent(arcId, k -> new ArrayList<>()).add(velocity);
                    }

                } catch (Exception e) {
                    // Ignorar pares con errores en timestamps o odómetro
                    continue;
                }
            }
        }

        return velocitiesByArc;
    }

    /**
     * MÉTODO CLAVE: Calcular velocidad usando odómetro
     * 
     * @param d1 Datagrama inicial
     * @param d2 Datagrama final
     * @return Velocidad en m/s
     */
    private double calculateVelocityUsingOdometer(BusDatagram d1, BusDatagram d2)
            throws Exception {

        // 1. CALCULAR DISTANCIA usando odómetro (en metros)
        double distance = d2.odometer - d1.odometer;

        // Validar que el odómetro aumentó (no retrocedió)
        if (distance <= 0) {
            throw new Exception("Invalid odometer: distance <= 0");
        }

        // 2. CALCULAR TIEMPO en segundos
        LocalDateTime time1 = LocalDateTime.parse(d1.datagramDate, DATE_FORMATTER);
        LocalDateTime time2 = LocalDateTime.parse(d2.datagramDate, DATE_FORMATTER);

        long timeDiffSeconds = ChronoUnit.SECONDS.between(time1, time2);

        // Validar que el tiempo avanzó
        if (timeDiffSeconds <= 0) {
            throw new Exception("Invalid time: timeDiff <= 0");
        }

        // 3. VELOCIDAD = DISTANCIA / TIEMPO
        double velocity = distance / timeDiffSeconds;

        // DEBUG (opcional - comentar en producción)
        if (Math.random() < 0.001) { // Log 0.1% de muestras
            System.out.printf("DEBUG: d=%.2fm, t=%ds, v=%.2fm/s (%.1fkm/h)%n",
                    distance, timeDiffSeconds, velocity, velocity * 3.6);
        }

        return velocity;
    }

    /**
     * Crea un ID de arco basado en las paradas consecutivas
     * Formato: ARC_{lineId}_{stopId1}_{stopId2}
     */
    private String createArcId(BusDatagram d1, BusDatagram d2) {
        return String.format("ARC_%s_%s_%s", d1.lineId, d1.stopId, d2.stopId);
    }

    /**
     * Persistir resultados a base de datos (si está disponible)
     */
    private void persistToDatabase(Map<String, List<Double>> arcVelocities) {
        try {
            if (!com.sitm.mio.persistence.DBConnection.isAvailable()) {
                return; // DB no disponible - solo procesamiento en memoria
            }

            com.sitm.mio.persistence.VelocityDao dao = new com.sitm.mio.persistence.VelocityDao();
            String yearMonth = com.sitm.mio.persistence.VelocityDao.currentYearMonth();

            for (Map.Entry<String, List<Double>> entry : arcVelocities.entrySet()) {
                String arcId = entry.getKey();
                List<Double> velocities = entry.getValue();

                if (velocities.isEmpty())
                    continue;

                // Calcular promedio
                double sum = 0.0;
                for (Double v : velocities)
                    sum += v;
                double avg = sum / velocities.size();

                // Extraer lineId del arcId
                String lineId = extractLineIdFromArc(arcId);

                // Guardar en DB
                dao.upsert(yearMonth, lineId, arcId, avg, velocities.size());
            }

            System.out.println("Worker " + workerId + " - Results persisted to database");

        } catch (Exception ex) {
            System.err.println("Worker DB persist error (non-critical): " + ex.getMessage());
        }
    }

    /**
     * Construye el resultado agregado para retornar al Master
     */
    private VelocityResult buildAggregatedResult(
            String taskId,
            Map<String, List<Double>> arcVelocities,
            long startTime) {

        VelocityResult result = new VelocityResult();
        result.arcId = "aggregated-" + taskId;
        result.processingTime = System.currentTimeMillis() - startTime;

        // Serializar velocidades en periodStart
        // Formato: "arcId1:velocity1:samples1|arcId2:velocity2:samples2|..."
        StringBuilder sb = new StringBuilder();

        for (Map.Entry<String, List<Double>> entry : arcVelocities.entrySet()) {
            String arcId = entry.getKey();
            List<Double> velocities = entry.getValue();

            if (velocities.isEmpty())
                continue;

            double sum = 0.0;
            for (Double v : velocities)
                sum += v;
            double avg = sum / velocities.size();

            if (sb.length() > 0)
                sb.append("|");
            sb.append(arcId).append(":").append(avg).append(":").append(velocities.size());
        }

        result.periodStart = sb.toString();

        // Calcular totales
        int totalSamples = 0;
        double totalVelocity = 0.0;

        for (List<Double> velocities : arcVelocities.values()) {
            for (Double v : velocities) {
                totalVelocity += v;
                totalSamples++;
            }
        }

        result.sampleCount = totalSamples;
        result.averageVelocity = totalSamples > 0 ? totalVelocity / totalSamples : 0.0;

        return result;
    }

    /**
     * Crea un resultado de error
     */
    private VelocityResult createErrorResult(String taskId, long startTime) {
        VelocityResult errorResult = new VelocityResult();
        errorResult.arcId = "error-" + taskId;
        errorResult.averageVelocity = 0.0;
        errorResult.sampleCount = 0;
        errorResult.processingTime = System.currentTimeMillis() - startTime;
        errorResult.periodStart = "";
        errorResult.periodEnd = "";
        return errorResult;
    }

    /**
     * Extrae el lineId del arcId
     */
    private String extractLineIdFromArc(String arcId) {
        try {
            if (arcId != null && arcId.contains("_")) {
                String[] parts = arcId.split("_");
                if (parts.length >= 2)
                    return parts[1];
            }
        } catch (Exception ex) {
        }
        return "unknown";
    }
}