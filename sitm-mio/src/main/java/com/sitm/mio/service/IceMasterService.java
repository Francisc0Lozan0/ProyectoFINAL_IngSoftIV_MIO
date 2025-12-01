package com.sitm.mio.service;

import com.sitm.mio.master.DistributedMaster;
import org.springframework.stereotype.Service;
import Ice.Communicator;
import Ice.ObjectAdapter;
import Ice.Util;
import SITM.MIO.MasterPrx;
import SITM.MIO.MasterPrxHelper;

/**
 * Servicio que gestiona la conexión con el Master de Ice
 * Permite a Spring interactuar con el sistema distribuido
 */
@Service
public class IceMasterService {
    
    private Communicator communicator;
    private ObjectAdapter adapter;
    private DistributedMaster master;
    private MasterPrx masterProxy;
    private boolean isRunning = false;
    
    /**
     * Inicia el servidor Master de Ice
     */
    public synchronized void startMaster(int port, String dataPath) throws Exception {
        if (isRunning) {
            System.out.println("⚠️ Master already running");
            return;
        }
        
        System.out.println("🚀 Starting Ice Master on port " + port);
        
        // Inicializar Ice
        communicator = Util.initialize(new String[]{
            "--Ice.ThreadPool.Server.Size=10",
            "--Ice.ThreadPool.Server.SizeMax=100"
        });
        
        // Crear adaptador
        adapter = communicator.createObjectAdapterWithEndpoints(
            "MasterAdapter",
            "tcp -p " + port
        );
        
        // Crear e instalar el Master
        master = new DistributedMaster(dataPath);
        Ice.Object masterServant = master;
        adapter.add(masterServant, Util.stringToIdentity("Master"));
        adapter.activate();
        
        // Obtener proxy para uso local
        masterProxy = MasterPrxHelper.checkedCast(
            adapter.createProxy(Util.stringToIdentity("Master"))
        );
        
        isRunning = true;
        System.out.println("✅ Ice Master started successfully on port " + port);
    }
    
    /**
     * Detiene el servidor Master
     */
    public synchronized void stopMaster() {
        if (!isRunning) {
            return;
        }
        
        System.out.println("⏹️ Stopping Ice Master...");
        
        if (adapter != null) {
            adapter.destroy();
        }
        
        if (communicator != null) {
            communicator.destroy();
        }
        
        isRunning = false;
        System.out.println("✅ Ice Master stopped");
    }
    
    /**
     * Obtiene el estado del Master
     */
    public String getMasterStatus() {
        if (!isRunning) {
            return "STOPPED";
        }
        
        try {
            return master != null ? master.getSystemStatus(null) : "UNKNOWN";
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
    
    /**
     * Obtiene el proxy del Master para operaciones Ice
     */
    public MasterPrx getMasterProxy() {
        return masterProxy;
    }
    
    /**
     * Obtiene la instancia del Master
     */
    public DistributedMaster getMaster() {
        return master;
    }
    
    /**
     * Verifica si el Master está corriendo
     */
    public boolean isRunning() {
        return isRunning;
    }
}
