package com.sitm.mio.config;

import com.sitm.mio.service.IceMasterService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@SpringBootApplication
@ComponentScan(basePackages = "com.sitm.mio")
@EntityScan(basePackages = "com.sitm.mio.entity")
@EnableJpaRepositories(basePackages = "com.sitm.mio.repository")
public class SpringBootConfig {
    
    private static ConfigurableApplicationContext context;
    
    public static void main(String[] args) {
        context = SpringApplication.run(SpringBootConfig.class, args);
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("🚀 SITM-MIO Application Started Successfully");
        System.out.println("=".repeat(80));
        System.out.println("📡 API Server: http://localhost:8080");
        System.out.println("📊 Dashboard: http://localhost:8080/dashboard.html");
        System.out.println("🔌 WebSocket: ws://localhost:8080/ws/streaming");
        System.out.println("📚 API Docs:");
        System.out.println("   - GET  /api/data/stats              - System statistics");
        System.out.println("   - GET  /api/data/velocities         - Query velocity data");
        System.out.println("   - GET  /api/data/velocities/top     - Top velocities");
        System.out.println("   - POST /api/historical/process      - Process historical data");
        System.out.println("   - GET  /api/historical/status       - Master status");
        System.out.println("=".repeat(80) + "\n");
    }
    
    /**
     * Inicializa el contexto de Spring sin arrancar el servidor web
     * Esto permite usar Spring con Ice sin conflictos
     */
    public static ConfigurableApplicationContext initContext() {
        if (context == null || !context.isActive()) {
            context = SpringApplication.run(SpringBootConfig.class);
        }
        return context;
    }
    
    /**
     * Obtiene un bean del contexto de Spring
     */
    public static <T> T getBean(Class<T> beanClass) {
        if (context == null) {
            initContext();
        }
        return context.getBean(beanClass);
    }
    
    /**
     * Cierra el contexto de Spring
     */
    public static void closeContext() {
        if (context != null) {
            context.close();
        }
    }
    
    /**
     * Inicializador automático del Ice Master al arrancar Spring Boot
     */
    @Component
    public static class IceMasterInitializer {
        
        @Autowired
        private IceMasterService masterService;
        
        @Value("${ice.master.port:10000}")
        private int masterPort;
        
        @Value("${ice.master.data.path:./data}")
        private String dataPath;
        
        @EventListener(ApplicationReadyEvent.class)
        public void initializeMaster() {
            try {
                System.out.println("\n🔧 Initializing Ice Master...");
                masterService.startMaster(masterPort, dataPath);
                System.out.println("✅ Ice Master initialized on port " + masterPort);
            } catch (Exception e) {
                System.err.println("❌ Failed to initialize Ice Master: " + e.getMessage());
                System.err.println("   The API server will still work, but processing features will be unavailable.");
                System.err.println("   You can start the Master manually later.");
            }
        }
    }
}
