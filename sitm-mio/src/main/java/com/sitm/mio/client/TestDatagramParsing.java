package com.sitm.mio.client;

import com.sitm.mio.util.StreamingDatagramReader;

import SITM.MIO.BusDatagram;

// Archivo temporal: TestDatagramParsing.java
public class TestDatagramParsing {
    public static void main(String[] args) throws Exception {
        String filePath = "./data/datagrams_1M.csv";
        
        try (StreamingDatagramReader reader = new StreamingDatagramReader(filePath, 10)) {
            BusDatagram[] batch = reader.readNextBatch();
            
            System.out.println("Total read: " + (batch != null ? batch.length : 0));
            
            if (batch != null) {
                int valid = 0;
                for (BusDatagram d : batch) {
                    if (d.latitude > 2 && d.latitude < 5 && 
                        d.longitude < -73 && d.longitude > -78) {
                        valid++;
                    }
                    System.out.printf("Datagram: lat=%.6f, lon=%.6f, line=%s, stop=%s%n",
                        d.latitude, d.longitude, d.lineId, d.stopId);
                }
                System.out.println("Valid datagrams: " + valid + "/" + batch.length);
            }
        }
    }
}