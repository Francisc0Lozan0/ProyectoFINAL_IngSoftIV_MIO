module SITM {
module MIO {

    struct BusDatagram {
        string busId;
        string lineId;
        string tripId;
        string stopId;
        double odometer;
        double latitude;
        double longitude;
        string datagramDate;
        int eventType;
    };
    
    struct Stop {
        string stopId;
        double longitude;
        double latitude;
        string shortName;
        string longName;
    };
    
    struct Arc {
        string arcId;
        string lineId;
        string startStopId;
        string endStopId;
        int orderIndex;     // Cambiado de "sequence"
        double distance;
    };

    struct VelocityResult {
        string arcId;
        double averageVelocity;
        int sampleCount;
        long processingTime;
        string periodStart;
        string periodEnd;
    };

    // --- Ahora SÍ declaramos las secuencias, después de los structs ---
    sequence<BusDatagram> BusDatagramSeq;
    sequence<Arc> ArcSeq;
    sequence<Stop> StopSeq;
    sequence<VelocityResult> VelocityResultSeq;

    struct ProcessingTask {
        string taskId;
        BusDatagramSeq datagrams;
        ArcSeq arcs;
        StopSeq stops;
        int totalWorkers;
        int workerId;
    };
    
    struct StreamingWindow {
        string windowId;
        BusDatagramSeq datagrams;
        long startTimestamp;
        long endTimestamp;
    };

    interface Worker {
        idempotent VelocityResult processTask(ProcessingTask task);
        idempotent bool isAlive();
        idempotent VelocityResult processStreamingWindow(StreamingWindow window);
    };

    interface Master {
        void registerWorker(WorkerPrx worker);
        void unregisterWorker(WorkerPrx worker);

        VelocityResultSeq processHistoricalData(
            BusDatagramSeq data,
            ArcSeq arcs,
            StopSeq stops
        );

        VelocityResultSeq processStreamingData(StreamingWindow window);

        string getSystemStatus();
    };

};
};
