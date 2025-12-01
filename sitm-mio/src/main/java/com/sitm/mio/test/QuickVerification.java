package com.sitm.mio.test;

import com.sitm.mio.loader.VelocityDataLoader;
import com.sitm.mio.persistence.DBConnection;

import java.sql.Connection;

/**
 * Script de verificación rápida para comprobar:
 * 1. Conexión a base de datos H2
 * 2. Creación de tablas y vistas
 * 3. Carga de datos desde CSV
 * 4. Consulta de datos cargados
 */
public class QuickVerification {
    
    public static void main(String[] args) {
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("  SITM-MIO Quick Verification");
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println();
        
        try {
            // 1. Verificar conexión
            System.out.println("1️⃣  Verificando conexión a base de datos...");
            Connection conn = DBConnection.getConnection();
            if (conn == null) {
                System.err.println("   ❌ Error: No se pudo conectar a la base de datos");
                return;
            }
            System.out.println("   ✅ Conexión exitosa");
            conn.close();
            
            // 2. Crear tabla y vista
            System.out.println();
            System.out.println("2️⃣  Creando tabla y vista...");
            VelocityDataLoader.createTableIfNotExists();
            System.out.println("   ✅ Tabla y vista creadas");
            
            // 3. Verificar estado inicial
            System.out.println();
            System.out.println("3️⃣  Verificando estado de datos...");
            int existingCount = VelocityDataLoader.countVelocities();
            System.out.println("   📊 Registros existentes: " + existingCount);
            
            // 4. Cargar datos si es necesario
            if (existingCount == 0) {
                System.out.println();
                System.out.println("4️⃣  Cargando datos desde CSV...");
                int totalRecords = VelocityDataLoader.loadAllVelocityFiles();
                System.out.println("   ✅ Registros cargados: " + totalRecords);
            } else {
                System.out.println();
                System.out.println("4️⃣  Datos ya existen, omitiendo carga");
            }
            
            // 5. Verificar resultado final
            System.out.println();
            System.out.println("5️⃣  Verificando resultado final...");
            int finalCount = VelocityDataLoader.countVelocities();
            System.out.println("   📊 Total de registros en BD: " + finalCount);
            
            // 6. Probar consulta de vista
            System.out.println();
            System.out.println("6️⃣  Probando vista 'velocities'...");
            try (Connection testConn = DBConnection.getConnection()) {
                var rs = testConn.createStatement().executeQuery(
                    "SELECT COUNT(*) as total FROM velocities"
                );
                if (rs.next()) {
                    int viewCount = rs.getInt("total");
                    System.out.println("   📊 Registros visibles en vista: " + viewCount);
                    
                    if (viewCount == finalCount) {
                        System.out.println("   ✅ Vista funciona correctamente");
                    } else {
                        System.err.println("   ⚠️  Advertencia: La vista no muestra todos los registros");
                    }
                }
            }
            
            System.out.println();
            System.out.println("═══════════════════════════════════════════════════════════");
            System.out.println("  ✅ Verificación completada exitosamente");
            System.out.println("═══════════════════════════════════════════════════════════");
            System.out.println();
            System.out.println("🚀 Ahora puedes iniciar la aplicación con:");
            System.out.println("   mvn spring-boot:run");
            System.out.println("   o");
            System.out.println("   .\\scripts\\start_springboot_app.ps1");
            System.out.println();
            
        } catch (Exception e) {
            System.err.println();
            System.err.println("❌ Error durante la verificación:");
            System.err.println("   " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
