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
        int sequence;
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
    
    struct ProcessingTask {
        string taskId;
        sequence<BusDatagram> datagrams;
        sequence<Arc> arcs;
        sequence<Stop> stops;
        int totalWorkers;
        int workerId;
    };
    
    struct StreamingWindow {
        string windowId;
        sequence<BusDatagram> datagrams;
        long startTimestamp;
        long endTimestamp;
    };
    
    interface Worker {
        idempotent VelocityResult processTask(ProcessingTask task);
        idempotent bool isAlive();
        idempotent VelocityResult processStreamingWindow(StreamingWindow window);
    };
    
    interface Master {
        void registerWorker(Worker worker);
        void unregisterWorker(Worker worker);
        sequence<VelocityResult> processHistoricalData(sequence<BusDatagram> data, 
                                                      sequence<Arc> arcs,
                                                      sequence<Stop> stops);
        sequence<VelocityResult> processStreamingData(StreamingWindow window);
        string getSystemStatus();
    };

};
};