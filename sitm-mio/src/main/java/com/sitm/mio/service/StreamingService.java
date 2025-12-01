package com.sitm.mio.service;

import com.sitm.mio.dto.VelocityResponseDTO;
import com.sitm.mio.entity.VelocityRecord;
import com.sitm.mio.repository.VelocityRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio para gestionar datos de streaming en tiempo real
 * Los workers procesan datos del archivo datagrams4streaming.csv cada 30 seg
 * Este servicio mantiene un caché de los últimos datos procesados
 */
@Service
public class StreamingService {
    
    @Autowired
    private VelocityRecordRepository velocityRepository;
    
    @Autowired
    private DataProcessingService processingService;
    
    @Autowired
    private IceMasterService masterService;
    
    // Caché de datos de streaming (últimos 5 minutos)
    private List<VelocityResponseDTO> streamingCache = new ArrayList<>();
    private LocalDateTime lastUpdate = null;
    private static final String STREAMING_TEST_LABEL = "STREAMING_REALTIME";
    private static final String STREAMING_FILE = "./data/datagrams4streaming.csv";
    private static final int BATCH_SIZE = 1000; // Procesar 1000 datagramas cada vez
    
    private int currentOffset = 0; // Offset para leer el archivo incrementalmente
    
    /**
     * Procesa datos de streaming cada 30 segundos
     * Lee incrementalmente el archivo datagrams4streaming.csv
     */
    @Scheduled(fixedRate = 30000, initialDelay = 10000) // Cada 30 seg, inicia después de 10 seg
    public void processStreamingData() {
        try {
            if (!masterService.isRunning()) {
                System.out.println("[STREAMING] ⏸️  Master no disponible, esperando...");
                return;
            }
            
            System.out.println("\n[STREAMING] 🔄 Procesando nuevos datagramas...");
            System.out.println("[STREAMING] Offset actual: " + currentOffset);
            
            // Procesar siguiente lote de datagramas con Ice Master + Workers
            // Usar offset para lectura incremental
            DataProcessingService.ProcessingResult result = processingService.processHistorical(
                STREAMING_FILE,
                STREAMING_TEST_LABEL,
                BATCH_SIZE,
                BATCH_SIZE,  // Solo procesar BATCH_SIZE registros
                currentOffset  // Comenzar desde el offset actual
            );
            
            if (result.isSuccess()) {
                // Incrementar offset para próxima lectura
                currentOffset += BATCH_SIZE;
                
                System.out.printf("[STREAMING] ✅ Procesados %d datagramas en %.2f segundos%n",
                    result.getTotalRecords(), result.getElapsedTimeMs() / 1000.0);
                
                // Actualizar caché con los nuevos datos
                updateStreamingCache();
                
                lastUpdate = LocalDateTime.now();
                System.out.printf("[STREAMING] 📊 Cache actualizado: %d registros de velocidad%n", 
                    streamingCache.size());
            } else {
                System.err.println("[STREAMING] ❌ Error en procesamiento: " + result.getError());
            }
            
        } catch (Exception e) {
            System.err.println("[STREAMING] ❌ Error procesando datos: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Actualiza el caché con los datos más recientes de la BD
     */
    private void updateStreamingCache() {
        try {
            // Obtener velocidades calculadas de los últimos 5 minutos
            LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
            
            List<VelocityRecord> recentRecords = velocityRepository.findAll()
                .stream()
                .filter(r -> STREAMING_TEST_LABEL.equals(r.getTestLabel()))
                .filter(r -> r.getCreatedAt() != null && r.getCreatedAt().isAfter(fiveMinutesAgo))
                .collect(Collectors.toList());
            
            streamingCache = recentRecords.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
            
        } catch (Exception e) {
            System.err.println("[STREAMING] Error actualizando caché: " + e.getMessage());
        }
    }
    
    /**
     * Obtiene los datos de streaming actuales
     */
    public List<VelocityResponseDTO> getStreamingData() {
        return new ArrayList<>(streamingCache);
    }
    
    /**
     * Obtiene la última actualización
     */
    public LocalDateTime getLastUpdate() {
        return lastUpdate;
    }
    
    /**
     * Obtiene el número de registros en caché
     */
    public int getCacheSize() {
        return streamingCache.size();
    }
    
    /**
     * Reinicia el offset para volver a leer desde el inicio
     */
    public void resetOffset() {
        currentOffset = 0;
        streamingCache.clear();
        System.out.println("[STREAMING] 🔄 Offset reiniciado");
    }
    
    private VelocityResponseDTO toDTO(VelocityRecord record) {
        return VelocityResponseDTO.builder()
            .arcId(record.getArcId())
            .lineId(record.getLineId())
            .velocityMs(record.getVelocityMs())
            .velocityKmh(record.getVelocityKmh())
            .sampleCount(record.getSampleCount())
            .testLabel(record.getTestLabel())
            .timestamp(record.getTimestamp())
            .build();
    }
}
