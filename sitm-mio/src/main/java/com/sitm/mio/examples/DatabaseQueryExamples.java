package com.sitm.mio.examples;

import com.sitm.mio.config.SpringBootConfig;
import com.sitm.mio.entity.VelocityRecord;
import com.sitm.mio.entity.PerformanceMetric;
import com.sitm.mio.entity.SummaryStats;
import com.sitm.mio.repository.VelocityRecordRepository;
import com.sitm.mio.repository.PerformanceMetricRepository;
import com.sitm.mio.repository.SummaryStatsRepository;
import com.sitm.mio.service.VelocityDatabaseManager;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Ejemplos de consultas a la base de datos usando Spring Data JPA
 * 
 * Este archivo muestra cómo:
 * 1. Inicializar el contexto de Spring
 * 2. Obtener beans de repositorios
 * 3. Realizar consultas comunes
 * 4. Usar el servicio VelocityDatabaseManager
 */
public class DatabaseQueryExamples {
    
    public static void main(String[] args) {
        // Inicializar Spring Context
        SpringBootConfig.initContext();
        
        // Obtener repositorios
        VelocityRecordRepository velocityRepo = 
            SpringBootConfig.getBean(VelocityRecordRepository.class);
        PerformanceMetricRepository perfRepo = 
            SpringBootConfig.getBean(PerformanceMetricRepository.class);
        SummaryStatsRepository summaryRepo = 
            SpringBootConfig.getBean(SummaryStatsRepository.class);
        VelocityDatabaseManager manager = 
            SpringBootConfig.getBean(VelocityDatabaseManager.class);
        
        System.out.println("================================================================================");
        System.out.println("EJEMPLOS DE CONSULTAS A LA BASE DE DATOS");
        System.out.println("================================================================================");
        
        // Ejemplo 1: Obtener todos los tests disponibles
        ejemplo1_TestsDisponibles(velocityRepo);
        
        // Ejemplo 2: Top 10 arcos más rápidos
        ejemplo2_TopVelocidades(manager);
        
        // Ejemplo 3: Velocidades por línea
        ejemplo3_VelocidadesPorLinea(velocityRepo);
        
        // Ejemplo 4: Métricas de performance
        ejemplo4_MetricasPerformance(perfRepo);
        
        // Ejemplo 5: Resumen de últimos tests
        ejemplo5_ResumenTests(summaryRepo);
        
        // Ejemplo 6: Consultas por rango de fechas
        ejemplo6_ConsultasPorFecha(velocityRepo);
        
        // Cerrar contexto
        SpringBootConfig.closeContext();
        System.out.println("\n✅ Ejemplos completados");
    }
    
    /**
     * Ejemplo 1: Obtener todos los tests disponibles
     */
    private static void ejemplo1_TestsDisponibles(VelocityRecordRepository repo) {
        System.out.println("\n📊 EJEMPLO 1: Tests Disponibles");
        System.out.println("-".repeat(80));
        
        List<VelocityRecord> allRecords = repo.findAll();
        long totalRecords = allRecords.size();
        
        // Obtener tests únicos
        List<String> uniqueTests = allRecords.stream()
            .map(VelocityRecord::getTestLabel)
            .distinct()
            .toList();
        
        System.out.printf("Total de registros: %,d%n", totalRecords);
        System.out.printf("Tests únicos: %d%n", uniqueTests.size());
        System.out.println("Tests:");
        uniqueTests.forEach(test -> System.out.println("  - " + test));
    }
    
    /**
     * Ejemplo 2: Top 10 arcos más rápidos de un test específico
     */
    private static void ejemplo2_TopVelocidades(VelocityDatabaseManager manager) {
        System.out.println("\n🏎️ EJEMPLO 2: Top 10 Arcos Más Rápidos");
        System.out.println("-".repeat(80));
        
        // Cambiar "1_MIL" por el test que quieras consultar
        String testLabel = "1_MIL";
        List<VelocityRecord> topVelocities = manager.getTopVelocitiesByTest(testLabel, 10);
        
        if (topVelocities.isEmpty()) {
            System.out.println("⚠️ No hay datos para el test: " + testLabel);
            System.out.println("   Ejecuta primero un test para generar datos");
            return;
        }
        
        System.out.printf("Test: %s%n%n", testLabel);
        System.out.printf("%-30s %10s %10s %10s%n", "Arco ID", "Velocidad", "km/h", "Muestras");
        System.out.println("-".repeat(80));
        
        for (int i = 0; i < topVelocities.size(); i++) {
            VelocityRecord record = topVelocities.get(i);
            System.out.printf("%2d. %-27s %9.2f m/s %8.1f %9d%n",
                i + 1,
                record.getArcId(),
                record.getVelocityMs(),
                record.getVelocityKmh(),
                record.getSampleCount()
            );
        }
    }
    
    /**
     * Ejemplo 3: Velocidades promedio por línea
     */
    private static void ejemplo3_VelocidadesPorLinea(VelocityRecordRepository repo) {
        System.out.println("\n🚌 EJEMPLO 3: Velocidades Promedio por Línea");
        System.out.println("-".repeat(80));
        
        List<String> lines = repo.findDistinctLineIds();
        
        if (lines.isEmpty()) {
            System.out.println("⚠️ No hay datos de líneas disponibles");
            return;
        }
        
        System.out.printf("%-10s %15s %15s %15s%n", "Línea", "Vel. Prom", "km/h", "Registros");
        System.out.println("-".repeat(80));
        
        for (String line : lines) {
            List<VelocityRecord> lineRecords = repo.findByLineId(line);
            
            if (!lineRecords.isEmpty()) {
                double avgVelocity = lineRecords.stream()
                    .mapToDouble(VelocityRecord::getVelocityMs)
                    .average()
                    .orElse(0.0);
                
                System.out.printf("%-10s %14.2f m/s %13.1f %15d%n",
                    line,
                    avgVelocity,
                    avgVelocity * 3.6,
                    lineRecords.size()
                );
            }
        }
    }
    
    /**
     * Ejemplo 4: Métricas de performance de los últimos tests
     */
    private static void ejemplo4_MetricasPerformance(PerformanceMetricRepository repo) {
        System.out.println("\n⚡ EJEMPLO 4: Métricas de Performance");
        System.out.println("-".repeat(80));
        
        List<PerformanceMetric> metrics = repo.findAllOrderedByThroughput();
        
        if (metrics.isEmpty()) {
            System.out.println("⚠️ No hay métricas de performance disponibles");
            return;
        }
        
        System.out.printf("%-20s %15s %15s %10s %15s%n", 
            "Test", "Datagramas", "Tiempo (ms)", "Workers", "Throughput");
        System.out.println("-".repeat(80));
        
        metrics.stream()
            .limit(10)
            .forEach(metric -> {
                System.out.printf("%-20s %,15d %,15d %10d %14.2f dps%n",
                    metric.getTestLabel(),
                    metric.getDatagramCount(),
                    metric.getProcessingTimeMs(),
                    metric.getWorkers(),
                    metric.getThroughputDps()
                );
            });
    }
    
    /**
     * Ejemplo 5: Resumen de los últimos tests ejecutados
     */
    private static void ejemplo5_ResumenTests(SummaryStatsRepository repo) {
        System.out.println("\n📈 EJEMPLO 5: Resumen de Últimos Tests");
        System.out.println("-".repeat(80));
        
        List<SummaryStats> summaries = repo.findAll();
        
        if (summaries.isEmpty()) {
            System.out.println("⚠️ No hay resúmenes disponibles");
            return;
        }
        
        summaries.stream()
            .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
            .limit(5)
            .forEach(summary -> {
                System.out.println("\nTest: " + summary.getTestLabel());
                System.out.printf("  Fecha: %s%n", summary.getTimestamp());
                System.out.printf("  Datagramas: %,d%n", summary.getDatagramCount());
                System.out.printf("  Tiempo: %,d ms (%.2f min)%n", 
                    summary.getProcessingTimeMs(),
                    summary.getProcessingTimeMs() / 60000.0);
                System.out.printf("  Resultados válidos: %d/%d%n", 
                    summary.getValidResults(), 
                    summary.getTotalResults());
                System.out.printf("  Vel. promedio: %.2f m/s (%.1f km/h)%n",
                    summary.getAvgVelocityMs(),
                    summary.getAvgVelocityMs() * 3.6);
                System.out.printf("  Vel. máxima: %.2f m/s (%.1f km/h)%n",
                    summary.getMaxVelocityMs(),
                    summary.getMaxVelocityMs() * 3.6);
            });
    }
    
    /**
     * Ejemplo 6: Consultas por rango de fechas
     */
    private static void ejemplo6_ConsultasPorFecha(VelocityRecordRepository repo) {
        System.out.println("\n📅 EJEMPLO 6: Consultas por Rango de Fechas");
        System.out.println("-".repeat(80));
        
        // Últimas 24 horas
        LocalDateTime hace24h = LocalDateTime.now().minusDays(1);
        LocalDateTime ahora = LocalDateTime.now();
        
        List<VelocityRecord> recentRecords = repo.findByTimestampBetween(hace24h, ahora);
        
        System.out.printf("Registros de las últimas 24 horas: %,d%n", recentRecords.size());
        
        if (!recentRecords.isEmpty()) {
            double avgSpeed = recentRecords.stream()
                .mapToDouble(VelocityRecord::getVelocityKmh)
                .average()
                .orElse(0.0);
            
            System.out.printf("Velocidad promedio: %.1f km/h%n", avgSpeed);
            
            // Agrupar por test
            var byTest = recentRecords.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    VelocityRecord::getTestLabel,
                    java.util.stream.Collectors.counting()
                ));
            
            System.out.println("\nRegistros por test:");
            byTest.forEach((test, count) -> 
                System.out.printf("  %-20s: %,d registros%n", test, count)
            );
        }
    }
}
