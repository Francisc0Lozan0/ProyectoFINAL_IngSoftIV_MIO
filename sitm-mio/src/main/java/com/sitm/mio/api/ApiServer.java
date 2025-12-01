package com.sitm.mio.api;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sitm.mio.persistence.DBConnection;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/**
 * Servidor API REST para el sistema MIO
 * Proporciona endpoints para consultar datos de transporte y servir archivos estáticos
 * @version 1.0
 */
public class ApiServer {
    private HttpServer server;

    /**
     * Inicia el servidor HTTP en el puerto especificado
     * @param port Puerto en el que escuchar
     * @throws Exception Si hay error al iniciar el servidor
     */
    public void start(int port) throws Exception {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // Endpoints de archivos estáticos
        server.createContext("/", new MapViewHandler());
        server.createContext("/map.html", new MapFileHandler());
        server.createContext("/dashboard.html", new StaticFileHandler("dashboard.html"));
        
        // Endpoints de API
        server.createContext("/api/data/stats", new StatsHandler());
        server.createContext("/api/data/velocities/top", new TopVelocitiesHandler());
        server.createContext("/api/data/tests", new TestsHandler());
        server.createContext("/api/data/stops", new StopsHandler());
        server.createContext("/api/data/lines", new LinesHandler());
        server.createContext("/api/data/linestops", new LineStopsHandler());
        server.createContext("/api/data/velocities/line/", new VelocitiesByLineHandler());
        server.createContext("/api/data/streaming", new StreamingHandler());
        
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("API Server started on http://localhost:" + port);
        System.out.println("Mapa disponible en: http://localhost:" + port + "/");
    }

    /**
     * Detiene el servidor HTTP
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
            System.out.println("Server stopped");
        }
    }

    /**
     * Handler para servir el mapa en la raíz
     */
    static class MapViewHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            try {
                InputStream is = getClass().getClassLoader().getResourceAsStream("static/map.html");
                if (is == null) {
                    Path filePath = Paths.get("src/main/resources/static/map.html");
                    if (Files.exists(filePath)) {
                        is = Files.newInputStream(filePath);
                    }
                }

                if (is == null) {
                    String notFound = "<html><body><h1>404 - map.html not found</h1></body></html>";
                    byte[] bytes = notFound.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                    exchange.sendResponseHeaders(404, bytes.length);
                    exchange.getResponseBody().write(bytes);
                    exchange.getResponseBody().close();
                    return;
                }

                byte[] bytes = is.readAllBytes();
                is.close();

                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.getResponseBody().close();
            } catch (Exception e) {
                e.printStackTrace();
                exchange.sendResponseHeaders(500, -1);
            }
        }
    }

    /**
     * Handler para servir map.html específicamente
     */
    static class MapFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            new MapViewHandler().handle(exchange);
        }
    }

    /**
     * Handler genérico para archivos estáticos
     */
    static class StaticFileHandler implements HttpHandler {
        private final String filename;

        public StaticFileHandler(String filename) {
            this.filename = filename;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                InputStream is = getClass().getClassLoader().getResourceAsStream("static/" + filename);
                if (is == null) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }

                byte[] bytes = is.readAllBytes();
                is.close();

                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.getResponseBody().close();
            } catch (Exception e) {
                e.printStackTrace();
                exchange.sendResponseHeaders(500, -1);
            }
        }
    }

    /**
     * Handler para estadísticas generales
     */
    static class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try (Connection conn = DBConnection.getConnection()) {
                if (conn == null) {
                    sendJsonResponse(exchange, 200, "{\"success\":false,\"error\":\"Database not available\"}");
                    return;
                }

                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) as total, " +
                    "AVG(CAST(velocity_kmh AS DOUBLE)) as avg_vel, " +
                    "MAX(CAST(velocity_kmh AS DOUBLE)) as max_vel " +
                    "FROM velocities");
                
                if (rs.next()) {
                    String json = String.format(
                        "{\"success\":true,\"data\":{\"total\":%d,\"avgVelocity\":%.2f,\"maxVelocity\":%.2f}}",
                        rs.getInt("total"),
                        rs.getDouble("avg_vel"),
                        rs.getDouble("max_vel")
                    );
                    sendJsonResponse(exchange, 200, json);
                } else {
                    sendJsonResponse(exchange, 200, "{\"success\":false,\"error\":\"No data\"}");
                }
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    /**
     * Handler para top velocidades
     */
    static class TopVelocitiesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try (Connection conn = DBConnection.getConnection()) {
                if (conn == null) {
                    sendJsonResponse(exchange, 200, "{\"success\":false,\"data\":[]}");
                    return;
                }

                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                    "SELECT line_id, arc_id, velocity_km_h " +
                    "FROM velocities ORDER BY CAST(velocity_km_h AS DOUBLE) DESC LIMIT 10");
                
                StringBuilder json = new StringBuilder("{\"success\":true,\"data\":[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    json.append(String.format(java.util.Locale.US, "{\"lineId\":\"%s\",\"arcId\":\"%s\",\"velocityKmh\":%.2f}",
                        escapeJson(rs.getString("line_id")),
                        escapeJson(rs.getString("arc_id")),
                        rs.getDouble("velocity_km_h")));
                    first = false;
                }
                json.append("]}");
                sendJsonResponse(exchange, 200, json.toString());
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    /**
     * Handler para listar tests disponibles
     */
    static class TestsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try (Connection conn = DBConnection.getConnection()) {
                if (conn == null) {
                    sendJsonResponse(exchange, 200, "{\"success\":false,\"data\":[]}");
                    return;
                }

                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT DISTINCT test_label FROM velocities ORDER BY test_label");
                
                StringBuilder json = new StringBuilder("{\"success\":true,\"data\":[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    json.append("\"").append(escapeJson(rs.getString("test_label"))).append("\"");
                    first = false;
                }
                json.append("]}");
                sendJsonResponse(exchange, 200, json.toString());
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    /**
     * Handler para cargar paradas desde CSV
     */
    static class StopsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                Path csvPath = Paths.get("data/stops.csv");
                if (!Files.exists(csvPath)) {
                    sendJsonResponse(exchange, 404, "{\"success\":false,\"error\":\"stops.csv not found\"}");
                    return;
                }

                List<String> lines = Files.readAllLines(csvPath);
                StringBuilder json = new StringBuilder("{\"success\":true,\"data\":[");
                boolean first = true;
                
                for (int i = 1; i < lines.size(); i++) {
                    String[] parts = parseCsvLine(lines.get(i));
                    if (parts.length < 8) continue;
                    
                    if (!first) json.append(",");
                    json.append("{");
                    json.append("\"STOPID\":\"").append(escapeJson(parts[0])).append("\",");
                    json.append("\"SHORTNAME\":\"").append(escapeJson(parts[2])).append("\",");
                    json.append("\"LONGNAME\":\"").append(escapeJson(parts[3])).append("\",");
                    json.append("\"DECIMALLONGITUDE\":").append(parts[6]).append(",");
                    json.append("\"DECIMALLATITUDE\":").append(parts[7]);
                    json.append("}");
                    first = false;
                }
                json.append("]}");
                
                sendJsonResponse(exchange, 200, json.toString());
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    /**
     * Handler para cargar líneas desde CSV
     */
    static class LinesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                Path csvPath = Paths.get("data/lines.csv");
                if (!Files.exists(csvPath)) {
                    sendJsonResponse(exchange, 404, "{\"success\":false,\"error\":\"lines.csv not found\"}");
                    return;
                }

                List<String> lines = Files.readAllLines(csvPath);
                StringBuilder json = new StringBuilder("{\"success\":true,\"data\":[");
                boolean first = true;
                
                for (int i = 1; i < lines.size(); i++) {
                    String[] parts = parseCsvLine(lines.get(i));
                    if (parts.length < 4) continue;
                    
                    if (!first) json.append(",");
                    json.append("{\"LINEID\":\"").append(escapeJson(parts[0])).append("\",");
                    json.append("\"SHORTNAME\":\"").append(escapeJson(parts[2])).append("\",");
                    json.append("\"DESCRIPTION\":\"").append(escapeJson(parts[3])).append("\"}");
                    first = false;
                }
                json.append("]}");
                
                sendJsonResponse(exchange, 200, json.toString());
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    /**
     * Handler para cargar relaciones línea-parada desde CSV
     */
    static class LineStopsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                Path csvPath = Paths.get("data/linestops.csv");
                if (!Files.exists(csvPath)) {
                    sendJsonResponse(exchange, 404, "{\"success\":false,\"error\":\"linestops.csv not found\"}");
                    return;
                }

                List<String> lines = Files.readAllLines(csvPath);
                StringBuilder json = new StringBuilder("{\"success\":true,\"data\":[");
                boolean first = true;
                
                for (int i = 1; i < lines.size(); i++) {
                    String[] parts = parseCsvLine(lines.get(i));
                    if (parts.length < 5) continue;
                    
                    if (!first) json.append(",");
                    json.append("{");
                    json.append("\"LINESTOPID\":\"").append(escapeJson(parts[0])).append("\",");
                    json.append("\"STOPSEQUENCE\":").append(parts[1]).append(",");
                    json.append("\"ORIENTATION\":\"").append(escapeJson(parts[2])).append("\",");
                    json.append("\"LINEID\":\"").append(escapeJson(parts[3])).append("\",");
                    json.append("\"STOPID\":\"").append(escapeJson(parts[4])).append("\"");
                    json.append("}");
                    first = false;
                }
                json.append("]}");
                
                sendJsonResponse(exchange, 200, json.toString());
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    /**
     * Handler para velocidades por línea
     */
    static class VelocitiesByLineHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String lineId = path.substring(path.lastIndexOf('/') + 1);
            
            try (Connection conn = DBConnection.getConnection()) {
                if (conn == null) {
                    sendJsonResponse(exchange, 200, "{\"success\":false,\"data\":[]}");
                    return;
                }

                PreparedStatement stmt = conn.prepareStatement(
                    "SELECT arc_id, velocity_km_h FROM velocity_records WHERE line_id = ? LIMIT 1000");
                stmt.setString(1, lineId);
                ResultSet rs = stmt.executeQuery();
                
                StringBuilder json = new StringBuilder("{\"success\":true,\"data\":[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    json.append(String.format(java.util.Locale.US, "{\"arcId\":\"%s\",\"velocityKmh\":%.2f}",
                        escapeJson(rs.getString("arc_id")),
                        rs.getDouble("velocity_km_h")));
                    first = false;
                }
                json.append("]}");
                sendJsonResponse(exchange, 200, json.toString());
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    /**
     * Handler para datos de streaming
     * Consulta datos recientes de la base de datos H2
     */
    static class StreamingHandler implements HttpHandler {
        
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            System.out.println("StreamingHandler: Request received");
            try {
                Connection conn = DBConnection.getConnection();
                if (conn == null) {
                    sendJsonResponse(exchange, 500, "{\"success\":false,\"error\":\"Database not available\"}");
                    return;
                }
                
                // Consultar datos recientes (últimos 1000 registros)
                PreparedStatement stmt = conn.prepareStatement(
                    "SELECT arc_id, velocity_km_h, line_id FROM velocity_records " +
                    "ORDER BY timestamp DESC LIMIT 1000");
                ResultSet rs = stmt.executeQuery();
                
                StringBuilder json = new StringBuilder("{\"success\":true,\"timestamp\":\"");
                json.append(java.time.LocalDateTime.now().toString());
                json.append("\",\"data\":[");
                
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    json.append(String.format(java.util.Locale.US, 
                        "{\"arcId\":\"%s\",\"velocityKmh\":%.2f,\"lineId\":\"%s\"}",
                        escapeJson(rs.getString("arc_id")),
                        rs.getDouble("velocity_km_h"),
                        escapeJson(rs.getString("line_id"))));
                    first = false;
                }
                json.append("]}");
                
                rs.close();
                stmt.close();
                conn.close();
                
                System.out.println("StreamingHandler: Sending response");
                sendJsonResponse(exchange, 200, json.toString());
            } catch (Exception e) {
                System.err.println("StreamingHandler ERROR: " + e.getMessage());
                e.printStackTrace();
                sendJsonResponse(exchange, 500, "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    /**
     * Parsea una línea CSV respetando comillas
     * @param line Línea CSV a parsear
     * @return Array de valores
     */
    private static String[] parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        
        return result.toArray(new String[0]);
    }

    /**
     * Escapa caracteres especiales para JSON
     * @param value Valor a escapar
     * @return Valor escapado
     */
    private static String escapeJson(String value) {
        if (value == null) return "";
        return escapeJsonString(value);
    }

    /**
     * Escapa string para JSON
     * @param value String a escapar
     * @return String escapado
     */
    private static String escapeJsonString(String value) {
        return value.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    /**
     * Envía respuesta JSON
     * @param exchange HttpExchange
     * @param statusCode Código de estado HTTP
     * @param jsonBody Cuerpo JSON
     */
    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String jsonBody) throws IOException {
        byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }

    /**
     * Parsea query string
     * @param q Query string
     * @return Mapa de parámetros
     */
    private static Map<String, String> parseQuery(String q) {
        Map<String, String> map = new HashMap<>();
        if (q == null || q.isEmpty()) return map;
        
        String[] pairs = q.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                map.put(kv[0], java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
        }
        return map;
    }

    /**
     * Punto de entrada principal
     * @param args Argumentos de línea de comandos
     * @throws Exception Si hay error al iniciar
     */
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        
        ApiServer server = new ApiServer();
        server.start(port);
        
        System.out.println("\n=== MIO API Server ===");
        System.out.println("Mapa: http://localhost:" + port + "/");
        System.out.println("Dashboard: http://localhost:" + port + "/dashboard.html");
        System.out.println("\nPresiona Ctrl+C para detener el servidor");
    }
}
