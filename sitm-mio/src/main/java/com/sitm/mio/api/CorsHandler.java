package com.sitm.mio.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.Headers;

import java.io.IOException;

/**
 * Wrapper que agrega CORS automáticamente a todos los handlers
 */
public class CorsHandler implements HttpHandler {
    private final HttpHandler delegate;
    
    public CorsHandler(HttpHandler delegate) {
        this.delegate = delegate;
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Agregar headers CORS
        Headers headers = exchange.getResponseHeaders();
        headers.add("Access-Control-Allow-Origin", "*");
        headers.add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.add("Access-Control-Allow-Headers", "Content-Type, Authorization");
        headers.add("Access-Control-Max-Age", "3600");
        
        // Si es OPTIONS, responder inmediatamente
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }
        
        // Delegar al handler real
        try {
            delegate.handle(exchange);
        } catch (Exception e) {
            e.printStackTrace();
            // Enviar error
            String error = "{\"error\":\"" + e.getMessage() + "\"}";
            byte[] bytes = error.getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }
}