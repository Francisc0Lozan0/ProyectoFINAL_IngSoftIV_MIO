package com.sitm.mio.controller;

import com.sitm.mio.dto.ApiResponse;
import com.sitm.mio.dto.VelocityResponseDTO;
import com.sitm.mio.service.StreamingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller para servir datos estáticos del sistema (stops, lines, linestops)
 * Estos endpoints son necesarios para que el mapa funcione correctamente
 */
@RestController
@RequestMapping("/api/data")
@CrossOrigin(origins = "*")
public class StaticDataController {
    
    private static final String DATA_PATH = "./data";
    
    @Autowired
    private StreamingService streamingService;
    
    /**
     * GET /api/data/stops
     * Retorna todas las paradas del sistema
     * Formato CSV: LONGNAME,GPS_X,GPS_Y,STOPID,PLANVERSIONID,SHORTNAME,DECIMALLONG,DECIMALLAT
     */
    @GetMapping("/stops")
    public ApiResponse<List<Map<String, Object>>> getStops() {
        try {
            String csvPath = DATA_PATH + "/stops.csv";
            Path path = Paths.get(csvPath);
            
            if (!Files.exists(path)) {
                return ApiResponse.error("stops.csv not found in data directory");
            }
            
            List<Map<String, Object>> stopsList = new ArrayList<>();
            
            try (BufferedReader br = Files.newBufferedReader(path)) {
                String line;
                boolean firstLine = true;
                
                while ((line = br.readLine()) != null) {
                    if (firstLine) {
                        firstLine = false;
                        continue;
                    }
                    
                    String cleaned = line.replaceAll("\"", "");
                    String[] parts = cleaned.split(",");
                    
                    if (parts.length < 8) continue;
                    
                    Map<String, Object> stopMap = new HashMap<>();
                    stopMap.put("STOPID", parts[3].trim());
                    stopMap.put("LONGNAME", parts[0].trim());
                    stopMap.put("SHORTNAME", parts[5].trim());
                    stopMap.put("DECIMALLONGITUDE", Double.parseDouble(parts[6].trim()));
                    stopMap.put("DECIMALLATITUDE", Double.parseDouble(parts[7].trim()));
                    stopsList.add(stopMap);
                }
            }
            
            return ApiResponse.success(stopsList);
        } catch (Exception e) {
            return ApiResponse.error("Error loading stops: " + e.getMessage());
        }
    }
    
    /**
     * GET /api/data/lines
     * Retorna todas las líneas del sistema
     * Formato CSV: LINEID,PLANVERSIONID,SHORTNAME,DESCRIPTION
     */
    @GetMapping("/lines")
    public ApiResponse<List<Map<String, Object>>> getLines() {
        try {
            String csvPath = DATA_PATH + "/lines.csv";
            Path path = Paths.get(csvPath);
            
            if (!Files.exists(path)) {
                return ApiResponse.error("lines.csv not found in data directory");
            }
            
            List<Map<String, Object>> linesList = new ArrayList<>();
            
            try (BufferedReader br = Files.newBufferedReader(path)) {
                String line;
                boolean firstLine = true;
                
                while ((line = br.readLine()) != null) {
                    if (firstLine) {
                        firstLine = false;
                        continue;
                    }
                    
                    String cleaned = line.replaceAll("\"", "");
                    String[] parts = cleaned.split(",");
                    
                    if (parts.length < 4) continue;
                    
                    Map<String, Object> lineMap = new HashMap<>();
                    lineMap.put("LINEID", parts[0].trim());
                    lineMap.put("SHORTNAME", parts[2].trim());
                    lineMap.put("DESCRIPTION", parts[3].trim());
                    linesList.add(lineMap);
                }
            }
            
            return ApiResponse.success(linesList);
        } catch (Exception e) {
            return ApiResponse.error("Error loading lines: " + e.getMessage());
        }
    }
    
    /**
     * GET /api/data/linestops
     * Retorna todas las relaciones línea-parada del sistema
     * Formato CSV: LINESTOPID,STOPSEQUENCE,ORIENTATION,LINEID,STOPID,PLANVERSIONID,LINEVARIANT,LINEVARIANTTYPE
     */
    @GetMapping("/linestops")
    public ApiResponse<List<Map<String, Object>>> getLineStops() {
        try {
            String csvPath = DATA_PATH + "/linestops.csv";
            Path path = Paths.get(csvPath);
            
            if (!Files.exists(path)) {
                return ApiResponse.error("linestops.csv not found in data directory");
            }
            
            List<Map<String, Object>> lineStopsList = new ArrayList<>();
            
            try (BufferedReader br = Files.newBufferedReader(path)) {
                String line;
                boolean firstLine = true;
                
                while ((line = br.readLine()) != null) {
                    if (firstLine) {
                        firstLine = false;
                        continue;
                    }
                    
                    String cleaned = line.replaceAll("\"", "");
                    String[] parts = cleaned.split(",");
                    
                    if (parts.length < 5) continue;
                    
                    Map<String, Object> lsMap = new HashMap<>();
                    lsMap.put("LINEID", parts[3].trim());
                    lsMap.put("STOPID", parts[4].trim());
                    lsMap.put("STOPSEQUENCE", Integer.parseInt(parts[1].trim()));
                    lineStopsList.add(lsMap);
                }
            }
            
            return ApiResponse.success(lineStopsList);
        } catch (Exception e) {
            return ApiResponse.error("Error loading line stops: " + e.getMessage());
        }
    }
    
    /**
     * GET /api/data/streaming
     * Endpoint para datos de streaming (datos de velocidad en tiempo real)
     * Los workers procesan cada 30 seg datos del archivo datagrams4streaming.csv
     * y los guardan en la BD, este endpoint retorna esos datos desde el caché
     */
    @GetMapping("/streaming")
    public ApiResponse<Map<String, Object>> getStreamingData() {
        try {
            List<VelocityResponseDTO> data = streamingService.getStreamingData();
            LocalDateTime lastUpdate = streamingService.getLastUpdate();
            int cacheSize = streamingService.getCacheSize();
            
            Map<String, Object> response = new HashMap<>();
            response.put("data", data);
            response.put("lastUpdate", lastUpdate);
            response.put("cacheSize", cacheSize);
            response.put("updateInterval", "30 seconds");
            response.put("source", "datagrams4streaming.csv processed by workers");
            
            return ApiResponse.success(response);
        } catch (Exception e) {
            return ApiResponse.error("Error getting streaming data: " + e.getMessage());
        }
    }
    
    /**
     * POST /api/data/streaming/reset
     * Reinicia el offset de lectura del archivo de streaming
     */
    @PostMapping("/streaming/reset")
    public ApiResponse<String> resetStreaming() {
        try {
            streamingService.resetOffset();
            return ApiResponse.success("Streaming offset reset successfully");
        } catch (Exception e) {
            return ApiResponse.error("Error resetting streaming: " + e.getMessage());
        }
    }
}
