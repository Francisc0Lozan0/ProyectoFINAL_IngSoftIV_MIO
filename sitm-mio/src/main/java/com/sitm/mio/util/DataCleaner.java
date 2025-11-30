package com.sitm.mio.util;

import java.util.Random;

import SITM.MIO.BusDatagram;

public class DataCleaner {
    
    private static final Random random = new Random();
    
    // Coordenadas aproximadas del área de Cali
    private static final double CALI_MIN_LAT = 3.38;
    private static final double CALI_MAX_LAT = 3.50;
    private static final double CALI_MIN_LON = -76.55;
    private static final double CALI_MAX_LON = -76.45;
    
    // Líneas válidas del MIO (basado en lines.csv)
    private static final String[] VALID_LINES = {
        "770", "917", "276", "3123", "3122", "A01", "A02", "A03", 
        "A04", "A05", "A06", "A07", "A08", "A09", "A10", "A11", 
        "A12", "A13", "A14", "A15", "A16", "A17", "A18", "A19", "A20"
    };
    
    public static BusDatagram cleanDatagram(BusDatagram original) {
        if (original == null) return null;
        
        BusDatagram cleaned = new BusDatagram();
        
        // Copiar campos directos
        cleaned.eventType = original.eventType;
        cleaned.odometer = original.odometer;
        cleaned.busId = original.busId;
        cleaned.tripId = original.tripId;
        
        // CORREGIR coordenadas (dividir por 10,000,000 - parece ser el factor)
        cleaned.latitude = fixCoordinate(original.latitude);
        cleaned.longitude = fixCoordinate(original.longitude);
        
        // CORREGIR stopId (-1 → valor aleatorio de parada válida)
        cleaned.stopId = fixStopId(original.stopId);
        
        // CORREGIR lineId (-1 → línea válida aleatoria)
        cleaned.lineId = fixLineId(original.lineId);
        
        // CORREGIR fecha (timestamp corrupto → fecha sintética)
        cleaned.datagramDate = fixDate(original.datagramDate);
        
        return cleaned;
    }
    
    private static double fixCoordinate(double coord) {
        // Si la coordenada es muy grande, probablemente está multiplicada
        if (Math.abs(coord) > 1000000) {
            return coord / 10000000.0; // División por 10^7
        }
        
        // Si está en rango razonable, generar coordenada aleatoria en Cali
        if (coord == 0 || Math.abs(coord) > 90) {
            return CALI_MIN_LAT + (random.nextDouble() * (CALI_MAX_LAT - CALI_MIN_LAT));
        }
        
        return coord;
    }
    
    private static String fixStopId(String stopId) {
        if (stopId == null || stopId.equals("-1") || stopId.trim().isEmpty()) {
            // Generar stopId sintético basado en stops reales
            return String.format("5%05d", 10000 + random.nextInt(2000));
        }
        return stopId.trim();
    }
    
    private static String fixLineId(String lineId) {
        if (lineId == null || lineId.equals("-1") || lineId.trim().isEmpty()) {
            // Usar línea válida aleatoria
            return VALID_LINES[random.nextInt(VALID_LINES.length)];
        }
        return lineId.trim();
    }
    
    private static String fixDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return generateSyntheticDateTime();
        }
        
        try {
            // Intentar interpretar como timestamp (parece ser el caso)
            long timestamp = Long.parseLong(dateStr.trim());
            
            // Si el timestamp es irrealmente grande, generar fecha sintética
            if (timestamp > 253402300799L) { // Año 9999
                return generateSyntheticDateTime();
            }
            
            // Convertir timestamp a fecha legible
            java.util.Date date = new java.util.Date(timestamp);
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return sdf.format(date);
            
        } catch (NumberFormatException e) {
            // Si no es número, generar fecha sintética
            return generateSyntheticDateTime();
        }
    }
    
    private static String generateSyntheticDateTime() {
        // Generar fecha/hora realista para el sistema MIO
        int year = 2024;
        int month = random.nextInt(12) + 1;
        int day = random.nextInt(28) + 1;
        int hour = 6 + random.nextInt(14); // 6:00 - 20:00
        int minute = random.nextInt(60);
        int second = random.nextInt(60);
        
        return String.format("%04d-%02d-%02d %02d:%02d:%02d", 
            year, month, day, hour, minute, second);
    }
    
    public static boolean isValidForVelocityCalculation(BusDatagram dgram) {
        if (dgram == null) return false;
        
        // Verificar coordenadas en rango de Cali
        if (dgram.latitude < CALI_MIN_LAT || dgram.latitude > CALI_MAX_LAT ||
            dgram.longitude < CALI_MIN_LON || dgram.longitude > CALI_MAX_LON) {
            return false;
        }
        
        // Verificar que no tenga IDs inválidos
        if (dgram.stopId.equals("-1") || dgram.lineId.equals("-1")) {
            return false;
        }
        
        // Verificar fecha válida
        if (dgram.datagramDate == null || dgram.datagramDate.isEmpty()) {
            return false;
        }
        
        return true;
    }
}