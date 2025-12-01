package com.sitm.mio.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitm.mio.service.IceMasterService;
import com.sitm.mio.util.StreamingDatagramReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import SITM.MIO.BusDatagram;
import SITM.MIO.MasterPrx;
import SITM.MIO.VelocityResult;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket Handler para simular streaming de datos en tiempo real
 */
@Component
public class StreamingWebSocketHandler extends TextWebSocketHandler {
    
    @Autowired
    private IceMasterService masterService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, StreamingSession> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("🔌 WebSocket connected: " + session.getId());
        
        // Enviar mensaje de bienvenida
        Map<String, Object> welcome = new HashMap<>();
        welcome.put("type", "connected");
        welcome.put("sessionId", session.getId());
        welcome.put("message", "Connected to streaming service");
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(welcome)));
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        Map<String, Object> request = objectMapper.readValue(payload, Map.class);
        
        String action = (String) request.get("action");
        
        if ("start".equals(action)) {
            startStreaming(session, request);
        } else if ("stop".equals(action)) {
            stopStreaming(session);
        } else {
            sendError(session, "Unknown action: " + action);
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        System.out.println("🔌 WebSocket disconnected: " + session.getId());
        stopStreaming(session);
    }
    
    /**
     * Inicia el streaming de datos
     */
    private void startStreaming(WebSocketSession session, Map<String, Object> request) {
        try {
            // Verificar que el Master esté corriendo
            if (!masterService.isRunning()) {
                sendError(session, "Ice Master is not running");
                return;
            }
            
            String filePath = (String) request.get("filePath");
            // Intervalo fijo de 30 segundos según enunciado del proyecto
            Integer intervalMs = 30000; // 30 segundos fijo
            Integer windowSize = (Integer) request.getOrDefault("windowSize", 100);
            
            if (filePath == null || filePath.isEmpty()) {
                sendError(session, "filePath is required");
                return;
            }
            
            // Detener streaming previo si existe
            stopStreaming(session);
            
            // Cargar datos
            System.out.println("📖 Loading streaming data from: " + filePath);
            BusDatagram[] allData = StreamingDatagramReader.loadFromCSV(filePath);
            
            // Crear sesión de streaming
            StreamingSession streamSession = new StreamingSession();
            streamSession.session = session;
            streamSession.data = allData;
            streamSession.currentIndex = 0;
            streamSession.windowSize = windowSize;
            streamSession.intervalMs = intervalMs;
            
            sessions.put(session.getId(), streamSession);
            
            // Enviar confirmación
            Map<String, Object> response = new HashMap<>();
            response.put("type", "streaming_started");
            response.put("totalRecords", allData.length);
            response.put("windowSize", windowSize);
            response.put("intervalMs", intervalMs);
            response.put("message", "Streaming iniciado con ventanas cada 30 segundos");
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            
            // Iniciar tarea periódica - 30 segundos fijo
            streamSession.scheduledTask = scheduler.scheduleAtFixedRate(
                () -> processStreamingWindow(session.getId()),
                0,  // Iniciar inmediatamente
                30, // 30 segundos
                TimeUnit.SECONDS
            );
            
            System.out.printf("🚀 Streaming started: %,d records, window=%d, interval=30s%n", 
                allData.length, windowSize);
            
        } catch (Exception e) {
            sendError(session, "Error starting streaming: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Procesa una ventana de streaming
     */
    private void processStreamingWindow(String sessionId) {
        StreamingSession streamSession = sessions.get(sessionId);
        if (streamSession == null) {
            return;
        }
        
        try {
            int start = streamSession.currentIndex;
            int end = Math.min(start + streamSession.windowSize, streamSession.data.length);
            
            if (start >= streamSession.data.length) {
                // Streaming completado
                finishStreaming(sessionId);
                return;
            }
            
            // Extraer ventana
            BusDatagram[] window = Arrays.copyOfRange(streamSession.data, start, end);
            
            // Procesar con Ice Master
            MasterPrx master = masterService.getMasterProxy();
            if (master == null) {
                sendError(streamSession.session, "Master proxy not available");
                stopStreaming(streamSession.session);
                return;
            }
            
            long startTime = System.currentTimeMillis();
            VelocityResult[] results = master.processHistoricalData(window, null, null);
            long processingTime = System.currentTimeMillis() - startTime;
            
            // Enviar resultados por WebSocket
            Map<String, Object> message = new HashMap<>();
            message.put("type", "streaming_data");
            message.put("windowIndex", streamSession.windowCount);
            message.put("recordsProcessed", window.length);
            message.put("currentPosition", end);
            message.put("totalRecords", streamSession.data.length);
            message.put("progress", (end * 100.0 / streamSession.data.length));
            message.put("processingTimeMs", processingTime);
            message.put("resultsCount", results.length);
            message.put("validResults", Arrays.stream(results)
                .filter(r -> r.sampleCount > 0 && r.averageVelocity > 0)
                .count());
            
            streamSession.session.sendMessage(
                new TextMessage(objectMapper.writeValueAsString(message))
            );
            
            streamSession.currentIndex = end;
            streamSession.windowCount++;
            
        } catch (Exception e) {
            System.err.println("❌ Error processing streaming window: " + e.getMessage());
            stopStreaming(streamSession.session);
        }
    }
    
    /**
     * Finaliza el streaming
     */
    private void finishStreaming(String sessionId) {
        StreamingSession streamSession = sessions.get(sessionId);
        if (streamSession == null) {
            return;
        }
        
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "streaming_completed");
            message.put("totalWindows", streamSession.windowCount);
            message.put("totalRecords", streamSession.data.length);
            
            streamSession.session.sendMessage(
                new TextMessage(objectMapper.writeValueAsString(message))
            );
            
            System.out.println("✅ Streaming completed: " + streamSession.windowCount + " windows");
            
        } catch (Exception e) {
            System.err.println("Error finishing streaming: " + e.getMessage());
        } finally {
            stopStreaming(streamSession.session);
        }
    }
    
    /**
     * Detiene el streaming
     */
    private void stopStreaming(WebSocketSession session) {
        StreamingSession streamSession = sessions.remove(session.getId());
        if (streamSession != null && streamSession.scheduledTask != null) {
            streamSession.scheduledTask.cancel(false);
            System.out.println("⏹️ Streaming stopped for session: " + session.getId());
        }
    }
    
    /**
     * Envía un mensaje de error
     */
    private void sendError(WebSocketSession session, String error) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "error");
            message.put("error", error);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
        } catch (Exception e) {
            System.err.println("Error sending error message: " + e.getMessage());
        }
    }
    
    /**
     * Clase interna para sesión de streaming
     */
    private static class StreamingSession {
        WebSocketSession session;
        BusDatagram[] data;
        int currentIndex;
        int windowSize;
        int intervalMs;
        int windowCount;
        ScheduledFuture<?> scheduledTask;
    }
}
