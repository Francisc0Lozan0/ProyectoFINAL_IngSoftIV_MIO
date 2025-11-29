package com.sitm.mio.api;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sitm.mio.persistence.VelocityDao;
import com.sitm.mio.persistence.DBConnection;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class ApiServer {
    private HttpServer server;

    public void start(int port) throws Exception {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/velocidad", new VelocityHandler());
        server.createContext("/api/buses", new BusesHandler());
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("API server started on port " + port);
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    static class VelocityHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try {
                String query = exchange.getRequestURI().getQuery();
                java.util.Map<String, String> params = parseQuery(query);
                String ym = params.getOrDefault("year_month", "");
                String line = params.getOrDefault("line_id", "");
                String arc = params.getOrDefault("arc_id", "");

                VelocityDao dao = new VelocityDao();
                String json = dao.queryVelocities(ym, line, arc);

                byte[] resp = json.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(200, resp.length);
                OutputStream os = exchange.getResponseBody();
                os.write(resp);
                os.close();
            } catch (Exception e) {
                try {
                    byte[] resp = ("{\"error\":\"" + e.getMessage() + "\"}").getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(500, resp.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(resp);
                    os.close();
                } catch (Exception ex) {}
            }
        }
    }

    static class BusesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try (Connection c = DBConnection.getConnection()) {
                PreparedStatement ps = c.prepareStatement("SELECT bus_id, last_update, line_id, trip_id, latitude, longitude FROM bus_status");
                ResultSet rs = ps.executeQuery();
                StringBuilder sb = new StringBuilder();
                sb.append("[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) sb.append(",");
                    first = false;
                    sb.append("{");
                    sb.append("\"bus_id\":\"").append(rs.getString("bus_id")).append("\"");
                    sb.append(",\"last_update\":\"").append(rs.getTimestamp("last_update")).append("\"");
                    sb.append(",\"line_id\":\"").append(rs.getString("line_id")).append("\"");
                    sb.append(",\"trip_id\":\"").append(rs.getString("trip_id")).append("\"");
                    sb.append(",\"latitude\":").append(rs.getDouble("latitude"));
                    sb.append(",\"longitude\":").append(rs.getDouble("longitude"));
                    sb.append("}");
                }
                sb.append("]");
                byte[] resp = sb.toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(200, resp.length);
                OutputStream os = exchange.getResponseBody();
                os.write(resp);
                os.close();
            } catch (Exception e) {
                try {
                    byte[] resp = ("{\"error\":\"" + e.getMessage() + "\"}").getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(500, resp.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(resp);
                    os.close();
                } catch (Exception ex) {}
            }
        }
    }

    private static java.util.Map<String, String> parseQuery(String q) {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        if (q == null || q.isEmpty()) return map;
        String[] parts = q.split("&");
        for (String p : parts) {
            String[] kv = p.split("=", 2);
            if (kv.length == 2) map.put(kv[0], java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        return map;
    }

    public static void main(String[] args) throws Exception {
        int port = 8080;
        if (args.length > 0) port = Integer.parseInt(args[0]);
        ApiServer s = new ApiServer();
        s.start(port);
    }
}
