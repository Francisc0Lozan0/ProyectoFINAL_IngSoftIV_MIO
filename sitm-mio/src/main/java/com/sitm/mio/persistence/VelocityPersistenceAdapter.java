package com.sitm.mio.persistence;

import com.sitm.mio.config.SpringBootConfig;
import com.sitm.mio.service.VelocityDatabaseManager;
import SITM.MIO.VelocityResult;

import java.util.List;

/**
 * Adaptador que facilita la migración del antiguo sistema de archivos CSV
 * al nuevo sistema de base de datos con Spring.
 * 
 * Puede usarse como reemplazo directo de VelocityFileManager.
 */
public class VelocityPersistenceAdapter {
    
    private static VelocityDatabaseManager databaseManager;
    private static boolean useDatabase = true;
    
    static {
        try {
            // Inicializar Spring Context
            SpringBootConfig.initContext();
            databaseManager = SpringBootConfig.getBean(VelocityDatabaseManager.class);
            System.out.println("✅ Sistema de persistencia en base de datos inicializado");
        } catch (Exception e) {
            System.err.println("⚠️ No se pudo inicializar base de datos, usando archivos CSV como fallback");
            System.err.println("   Error: " + e.getMessage());
            useDatabase = false;
        }
    }
    
    /**
     * Guarda resultados de velocidad en la base de datos (o CSV como fallback)
     */
    public static void saveVelocityResults(VelocityResult[] results, String testLabel, 
                                         long datagramCount, long processingTime) {
        if (useDatabase && databaseManager != null) {
            try {
                databaseManager.saveVelocityResults(results, testLabel, datagramCount, processingTime);
            } catch (Exception e) {
                System.err.println("❌ Error guardando en base de datos: " + e.getMessage());
                fallbackToCSV(results, testLabel, datagramCount, processingTime);
            }
        } else {
            fallbackToCSV(results, testLabel, datagramCount, processingTime);
        }
    }
    
    /**
     * Guarda métricas de performance
     */
    public static void savePerformanceMetrics(String testLabel, long datagramCount, 
                                            long processingTime, int batchCount,
                                            int workers, double throughput) {
        if (useDatabase && databaseManager != null) {
            try {
                databaseManager.savePerformanceMetrics(testLabel, datagramCount, 
                    processingTime, batchCount, workers, throughput);
            } catch (Exception e) {
                System.err.println("❌ Error guardando métricas: " + e.getMessage());
                VelocityFileManager.savePerformanceMetrics(testLabel, datagramCount, 
                    processingTime, batchCount, workers, throughput);
            }
        } else {
            VelocityFileManager.savePerformanceMetrics(testLabel, datagramCount, 
                processingTime, batchCount, workers, throughput);
        }
    }
    
    /**
     * Guarda datos para gráfico de punto de corte
     */
    public static void saveCutoffPointData(String scale, int workers, int batches, 
                                         long processingTime, double throughput) {
        if (useDatabase && databaseManager != null) {
            try {
                databaseManager.saveCutoffPointData(scale, workers, batches, 
                    processingTime, throughput);
            } catch (Exception e) {
                System.err.println("❌ Error guardando punto de corte: " + e.getMessage());
                VelocityFileManager.saveCutoffPointData(scale, workers, batches, 
                    processingTime, throughput);
            }
        } else {
            VelocityFileManager.saveCutoffPointData(scale, workers, batches, 
                processingTime, throughput);
        }
    }
    
    /**
     * Guarda estadísticas resumidas del procesamiento
     */
    public static void saveSummaryStats(String testLabel, List<VelocityResult> results, 
                                      long datagramCount, long processingTime) {
        if (useDatabase && databaseManager != null) {
            try {
                databaseManager.saveSummaryStats(testLabel, results, 
                    datagramCount, processingTime);
            } catch (Exception e) {
                System.err.println("❌ Error guardando resumen: " + e.getMessage());
                VelocityFileManager.saveSummaryStats(testLabel, results, 
                    datagramCount, processingTime);
            }
        } else {
            VelocityFileManager.saveSummaryStats(testLabel, results, 
                datagramCount, processingTime);
        }
    }
    
    /**
     * Fallback a archivos CSV en caso de error con la base de datos
     */
    private static void fallbackToCSV(VelocityResult[] results, String testLabel, 
                                     long datagramCount, long processingTime) {
        System.out.println("⚠️ Usando sistema de archivos CSV como fallback");
        VelocityFileManager.saveVelocityResults(results, testLabel, 
            datagramCount, processingTime);
    }
    
    /**
     * Fuerza el uso de archivos CSV en lugar de base de datos
     */
    public static void useCsvOnly() {
        useDatabase = false;
        System.out.println("ℹ️ Modo CSV activado");
    }
    
    /**
     * Reactiva el uso de base de datos
     */
    public static void useDatabaseOnly() {
        useDatabase = true;
        System.out.println("ℹ️ Modo base de datos activado");
    }
}
