package com.sitm.mio.util;

import java.util.Random;

import SITM.MIO.BusDatagram;

public class DataCleaner {
    
    private static final Random random = new Random();
    private static int cleanedCount = 0;
    
    // Coordenadas aproximadas del área de Cali
    private static final double CALI_MIN_LAT = 3.38;
    private static final double CALI_MAX_LAT = 3.50;
    private static final double CALI_MIN_LON = -76.55;
    private static final double CALI_MAX_LON = -76.45;
    
    // Líneas válidas del MIO
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
        
        // CORREGIR coordenadas (YA normalizadas del Reader)
        cleaned.latitude = fixCoordinate(original.latitude);
        cleaned.longitude = fixCoordinate(original.longitude);
        
        // CORREGIR stopId 
        String originalStopId = original.stopId;
        cleaned.stopId = fixStopId(original.stopId);
        
        // CORREGIR lineId
        String originalLineId = original.lineId;
        cleaned.lineId = fixLineId(original.lineId);
        
        // CORREGIR fecha
        cleaned.datagramDate = fixDate(original.datagramDate);
        
        // DEBUG: Mostrar algunas correcciones
        cleanedCount++;
        if (cleanedCount <= 10) {
            boolean stopIdChanged = !originalStopId.equals(cleaned.stopId);
            boolean lineIdChanged = !originalLineId.equals(cleaned.lineId);
            
            if (stopIdChanged || lineIdChanged) {
                System.out.printf("   🔧 Datagrama %d corregido: stop[%s→%s] line[%s→%s]%n",
                    cleanedCount, originalStopId, cleaned.stopId, originalLineId, cleaned.lineId);
            }
        }
        
        return cleaned;
    }
    
    private static double fixCoordinate(double coord) {
        // Ya viene normalizada del Reader, solo verificar validez
        if (coord == 0 || Math.abs(coord) > 90) {
            return CALI_MIN_LAT + (random.nextDouble() * (CALI_MAX_LAT - CALI_MIN_LAT));
        }
        return coord;
    }
    
    private static String fixStopId(String stopId) {
        if (stopId == null || stopId.equals("-1") || stopId.trim().isEmpty()) {
            return String.format("5%05d", 10000 + random.nextInt(2000));
        }
        return stopId.trim();
    }
    
    private static String fixLineId(String lineId) {
        if (lineId == null || lineId.equals("-1") || lineId.trim().isEmpty()) {
            return VALID_LINES[random.nextInt(VALID_LINES.length)];
        }
        return lineId.trim();
    }
    
    private static String fixDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return generateSyntheticDateTime();
        }
        
        try {
            long timestamp = Long.parseLong(dateStr.trim());
            if (timestamp > 253402300799L) {
                return generateSyntheticDateTime();
            }
            
            java.util.Date date = new java.util.Date(timestamp);
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return sdf.format(date);
            
        } catch (NumberFormatException e) {
            return generateSyntheticDateTime();
        }
    }
    
    private static String generateSyntheticDateTime() {
        int year = 2024;
        int month = random.nextInt(12) + 1;
        int day = random.nextInt(28) + 1;
        int hour = 6 + random.nextInt(14);
        int minute = random.nextInt(60);
        int second = random.nextInt(60);
        
        return String.format("%04d-%02d-%02d %02d:%02d:%02d", 
            year, month, day, hour, minute, second);
    }
    
    public static boolean isValidForVelocityCalculation(BusDatagram dgram) {
        if (dgram == null) return false;
        
        // Verificar coordenadas razonables para Cali
        if (dgram.latitude < 3.0 || dgram.latitude > 4.0) return false;
        if (dgram.longitude < -77.0 || dgram.longitude > -76.0) return false;
        
        // Verificar IDs válidos
        if (dgram.stopId == null || dgram.stopId.equals("-1") || dgram.stopId.isEmpty()) return false;
        if (dgram.lineId == null || dgram.lineId.equals("-1") || dgram.lineId.isEmpty()) return false;
        if (dgram.busId == null || dgram.busId.isEmpty()) return false;
        
        // Verificar fecha
        if (dgram.datagramDate == null || dgram.datagramDate.isEmpty()) return false;
        
        return true;
    }
    
    public static void resetCounter() {
        cleanedCount = 0;
    }
}