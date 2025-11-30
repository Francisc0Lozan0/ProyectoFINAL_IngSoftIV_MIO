package com.sitm.mio.client;

import com.sitm.mio.util.StreamingDatagramReader;

import SITM.MIO.BusDatagram;

public class DatagramDebugClient {
    public static void main(String[] args) {
        try {
            String filePath = "./data/datagrams_1M.csv";
            System.out.println("=== DATAGRAM DEBUG ===");
            
            try (StreamingDatagramReader reader = new StreamingDatagramReader(filePath, 10)) {
                BusDatagram[] batch = reader.readNextBatch();
                if (batch != null) {
                    System.out.printf("First %d datagrams:%n", batch.length);
                    for (int i = 0; i < Math.min(5, batch.length); i++) {
                        BusDatagram d = batch[i];
                        System.out.printf("Datagram %d:%n", i+1);
                        System.out.printf("  eventType: %d%n", d.eventType);
                        System.out.printf("  stopId: '%s'%n", d.stopId);
                        System.out.printf("  lineId: '%s'%n", d.lineId);
                        System.out.printf("  busId: '%s'%n", d.busId);
                        System.out.printf("  tripId: '%s'%n", d.tripId);
                        System.out.printf("  lat/lon: %.6f, %.6f%n", d.latitude, d.longitude);
                        System.out.printf("  date: '%s'%n", d.datagramDate);
                        System.out.printf("  odometer: %.2f%n", d.odometer);
                        System.out.println();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}