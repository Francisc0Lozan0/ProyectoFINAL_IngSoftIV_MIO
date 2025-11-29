package com.sitm.mio.master;

import java.util.List;
import java.util.Random;

import SITM.MIO.Worker;

public class LoadBalancer {
    
    public enum Strategy {
        ROUND_ROBIN,
        RANDOM,
        LEAST_LOADED
    }
    
    private Strategy strategy;
    private int currentIndex = 0;
    private Random random = new Random();
    
    public LoadBalancer(Strategy strategy) {
        this.strategy = strategy;
    }
    
    public Worker selectWorker(List<Worker> workers) {
        if (workers.isEmpty()) {
            return null;
        }

        switch (strategy) {
            case ROUND_ROBIN:
                return roundRobin(workers);
            case RANDOM:
                return random(workers);
            case LEAST_LOADED:
                return leastLoaded(workers);
            default:
                return roundRobin(workers);
        }
    }

    private Worker roundRobin(List<Worker> workers) {
        Worker selected = workers.get(currentIndex);
        currentIndex = (currentIndex + 1) % workers.size();
        return selected;
    }

    private Worker random(List<Worker> workers) {
        return workers.get(random.nextInt(workers.size()));
    }

    private Worker leastLoaded(List<Worker> workers) {
        return roundRobin(workers);
    }
}