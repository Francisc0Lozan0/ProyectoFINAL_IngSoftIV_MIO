package com.sitm.mio.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import SITM.MIO.BusDatagram;

/**
 * Lector de datagramas con procesamiento por lotes (streaming)
 * para manejar archivos enormes (36GB+) sin cargar todo en memoria.
 * 
 * Patrón: Iterator + Batch Processing
 */
public class StreamingDatagramReader implements AutoCloseable {
    
    private final BufferedReader reader;
    private final int batchSize;
    private boolean headerSkipped = false;
    private long totalRead = 0;
    
    /**
     * @param filePath Ruta al archivo CSV
     * @param batchSize Tamaño del lote (ej: 50000 para ~5MB por lote)
     */
    public StreamingDatagramReader(String filePath, int batchSize) throws IOException {
        this.reader = Files.newBufferedReader(Paths.get(filePath));
        this.batchSize = batchSize;
    }
    
    /**
     * Lee el siguiente lote de datagramas sin cargar todo el archivo
     * @return Lista de datagramas del lote, o null si no hay más datos
     */
    public BusDatagram[] readNextBatch() throws IOException {
        List<BusDatagram> batch = new ArrayList<>(batchSize);
        
        // Saltar header en primera lectura
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
            
            // Progreso cada 100K líneas
            if (totalRead % 100000 == 0) {
                System.out.println("  Leídas " + totalRead + " líneas...");
            }
        }
        
        return batch.isEmpty() ? null : batch.toArray(new BusDatagram[0]);
    }
    
    /**
     * Cuenta el total de líneas sin cargar datos (para planificación)
     */
    public static long countLines(String filePath) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath))) {
            return reader.lines().count() - 1; // -1 por el header
        }
    }
    
    /**
     * Estima el tamaño del archivo en bytes
     */
    public static long getFileSize(String filePath) throws IOException {
        return Files.size(Paths.get(filePath));
    }
    
// En StreamingDatagramReader.java, modificar el método parseDatagram:
private BusDatagram parseDatagram(String line) {
    try {
        String[] parts = line.split(",");
        if (parts.length < 11) return null;
        
        BusDatagram rawDatagram = new BusDatagram();
        rawDatagram.eventType = Integer.parseInt(parts[0].trim());
        rawDatagram.stopId = parts[2].trim();
        rawDatagram.odometer = parseDouble(parts[3].trim());
        rawDatagram.latitude = parseCoordinate(parts[4].trim());
        rawDatagram.longitude = parseCoordinate(parts[5].trim());
        rawDatagram.lineId = parts[6].trim();
        rawDatagram.tripId = parts[7].trim();
        rawDatagram.datagramDate = parts[9].trim();
        rawDatagram.busId = parts[10].trim();
        
        // LIMPIAR el datagrama
        BusDatagram cleanedDatagram = DataCleaner.cleanDatagram(rawDatagram);
        
        // Solo devolver si es válido para cálculo de velocidades
        if (DataCleaner.isValidForVelocityCalculation(cleanedDatagram)) {
            return cleanedDatagram;
        }
        
        return null;
        
    } catch (Exception e) {
        return null;
    }
}
    
    private double parseCoordinate(String coord) {
        try {
            return Double.parseDouble(coord.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
    
    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
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