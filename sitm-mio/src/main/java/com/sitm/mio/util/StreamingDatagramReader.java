package com.sitm.mio.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import SITM.MIO.BusDatagram;

/**
 * Lector de datagramas con el FORMATO CSV REAL de SITM-MIO
 * 
 * Formato del CSV real:
 * eventType,registerdate,stopId,odometer,latitude,longitude,taskId,lineId,tripId,unknown1,datagramDate,busId
 * 0,28-MAY-19,513327,70,34761183,-764873683,757,2241,159,1365,2019-05-27 20:14:43,1069
 */
public class StreamingDatagramReader implements AutoCloseable {
    
    private final BufferedReader reader;
    private final int batchSize;
    private boolean headerSkipped = false;
    private long totalRead = 0;
    
    /**
     * Constructor principal
     * @param filePath Ruta al archivo CSV
     * @param batchSize Tamaño del lote
     */
    public StreamingDatagramReader(String filePath, int batchSize) throws IOException {
        this.reader = Files.newBufferedReader(Paths.get(filePath));
        this.batchSize = batchSize;
    }
    
    /**
     * Constructor legacy (compatible con código antiguo)
     * @deprecated Use StreamingDatagramReader(String, int) instead
     */
    @Deprecated
    public StreamingDatagramReader(String filePath, int batchSize, boolean unused) throws IOException {
        this(filePath, batchSize);
    }
    
    /**
     * Lee el siguiente lote de datagramas
     */
    public BusDatagram[] readNextBatch() throws IOException {
        List<BusDatagram> batch = new ArrayList<>(batchSize);
        
        // Saltar header
        if (!headerSkipped) {
            reader.readLine();
            headerSkipped = true;
        }
        
        String line;
        int count = 0;
        
        while (count < batchSize && (line = reader.readLine()) != null) {
            BusDatagram dgram = parseDatagram(line);
            if (dgram != null) {
                batch.add(dgram);
                count++;
            }
            totalRead++;
            
            if (totalRead % 100000 == 0) {
                System.out.println("  Leídas " + totalRead + " líneas...");
            }
        }
        
        return batch.isEmpty() ? null : batch.toArray(new BusDatagram[0]);
    }
    
    /**
     * PARSEAR DATAGRAMAS CON EL FORMATO REAL
     * 
     * Campos del CSV:
     * 0: eventType
     * 1: registerdate (no usado)
     * 2: stopId
     * 3: odometer (METROS - distancia acumulada)
     * 4: latitude (DIVIDIR por 10,000,000)
     * 5: longitude (DIVIDIR por 10,000,000)
     * 6: taskId (no usado)
     * 7: lineId
     * 8: tripId
     * 9: unknown1 (no usado)
     * 10: datagramDate (timestamp formato: "2019-05-27 20:14:43")
     * 11: busId
     */
    private BusDatagram parseDatagram(String line) {
        try {
            // Limpiar comillas si existen
            line = line.replace("\"", "");
            
            String[] parts = line.split(",");
            if (parts.length < 12) {
                return null; // Línea incompleta
            }
            
            BusDatagram dgram = new BusDatagram();
            
            // Campos básicos
            dgram.eventType = parseInt(parts[0], 0);
            dgram.stopId = parts[2].trim();
            dgram.lineId = parts[7].trim();
            dgram.tripId = parts[8].trim();
            dgram.busId = parts[11].trim();
            
            // ODÓMETRO (ya está en metros)
            dgram.odometer = parseDouble(parts[3], 0.0);
            
            // COORDENADAS (dividir por 10,000,000 para obtener grados decimales)
            double rawLat = parseDouble(parts[4], 0.0);
            double rawLon = parseDouble(parts[5], 0.0);
            
            dgram.latitude = rawLat / 10000000.0;
            dgram.longitude = rawLon / 10000000.0;
            
            // TIMESTAMP (formato: "2019-05-27 20:14:43")
            dgram.datagramDate = parts[10].trim();
            
            // Validar datos básicos
            if (!isValidDatagram(dgram)) {
                return null;
            }
            
            return dgram;
            
        } catch (Exception e) {
            // Ignorar líneas con errores
            return null;
        }
    }
    
    /**
     * Valida que el datagrama tenga datos mínimos correctos
     */
    private boolean isValidDatagram(BusDatagram dgram) {
        // Verificar campos obligatorios no vacíos
        if (dgram.stopId.isEmpty() || dgram.lineId.isEmpty() || 
            dgram.tripId.isEmpty() || dgram.busId.isEmpty() ||
            dgram.datagramDate.isEmpty()) {
            return false;
        }
        
        // Verificar coordenadas en rango de Cali (Colombia)
        // Cali: ~3.3-3.5°N, ~76.4-76.6°W
        if (dgram.latitude < 3.0 || dgram.latitude > 4.0 ||
            dgram.longitude > -76.0 || dgram.longitude < -77.0) {
            return false;
        }
        
        // Verificar odómetro positivo
        if (dgram.odometer < 0) {
            return false;
        }
        
        // Verificar formato de timestamp (básico)
        if (!dgram.datagramDate.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
            return false;
        }
        
        return true;
    }
    
    private int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    private double parseDouble(String value, double defaultValue) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    public static long countLines(String filePath) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath))) {
            return reader.lines().count() - 1; // -1 por el header
        }
    }
    
    public static long getFileSize(String filePath) throws IOException {
        return Files.size(Paths.get(filePath));
    }
    
    public long getTotalRead() {
        return totalRead;
    }
    
    @Override
    public void close() throws IOException {
        if (reader != null) {
            reader.close();
        }
    }
}