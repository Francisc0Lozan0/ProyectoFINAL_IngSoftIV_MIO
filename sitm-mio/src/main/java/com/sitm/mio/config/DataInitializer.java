package com.sitm.mio.config;

import com.sitm.mio.loader.VelocityDataLoader;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Componente que inicializa la base de datos con los datos de velocidades
 * al arrancar la aplicación Spring Boot
 */
@Component
public class DataInitializer {
    
    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);
    
    @EventListener(ApplicationReadyEvent.class)
    public void loadVelocityData() {
        try {
            logger.info("\n" + "=".repeat(80));
            logger.info("🗄️  Iniciando carga de datos de velocidades...");
            logger.info("=".repeat(80));
            
            // Crear tabla si no existe
            VelocityDataLoader.createTableIfNotExists();
            
            // Contar registros existentes
            int existingCount = VelocityDataLoader.countVelocities();
            
            if (existingCount > 0) {
                logger.info("✓ Base de datos ya contiene {} registros de velocidades", existingCount);
                logger.info("  Si desea recargar los datos, elimine el archivo de base de datos H2 en ./data/");
                logger.info("=".repeat(80) + "\n");
                return;
            }
            
            logger.info("📂 Cargando datos desde archivos CSV en results/...");
            
            // Cargar archivos de velocidades
            int totalRecords = VelocityDataLoader.loadAllVelocityFiles();
            
            logger.info("\n✅ Carga de datos completada:");
            logger.info("   - Total de registros cargados: {}", totalRecords);
            logger.info("   - Registros en la tabla: {}", VelocityDataLoader.countVelocities());
            logger.info("=".repeat(80) + "\n");
            
        } catch (Exception e) {
            logger.error("❌ Error al cargar datos de velocidades: {}", e.getMessage(), e);
            logger.error("   La aplicación continuará funcionando, pero sin datos precargados.");
            logger.error("   Puede cargar los datos manualmente ejecutando VelocityDataLoader.main()");
        }
    }
}
