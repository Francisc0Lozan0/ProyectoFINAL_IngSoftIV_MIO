package com.sitm.mio.graphs;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Calcula velocidades históricas por arco usando el catálogo de arcos generado
 * por `RouteGraphBuilder` y los datagramas históricos.
 *
 * Salida: CSV con columnas: arcId,totalDistanceMeters,totalSeconds,avg_kmh,count
 */
public class HistoricalSpeedCalculator {

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);

    static class ArcStats {
        double totalDistanceMeters = 0.0;
        long totalSeconds = 0;
        int count = 0;
    }

    public void compute(String dataPath, String datagramFile, String outputCsv) throws IOException {
        // 1) Load graph
        RouteGraphBuilder builder = new RouteGraphBuilder();
        builder.loadDataDirectory(dataPath);
        List<RouteGraphBuilder.Arc> arcs = builder.getArcs();
        Map<String, RouteGraphBuilder.Stop> stops = builder.getStops();

        // Build quick lookup of arcs by start->end stop id
        Map<String, RouteGraphBuilder.Arc> arcByStops = new HashMap<>();
        for (RouteGraphBuilder.Arc a : arcs) {
            String key = a.startStopId + "->" + a.endStopId + "@" + a.lineId;
            arcByStops.put(key, a);
        }

        // 2) Read datagrams and group by busId+lineId
        Map<String, List<String[]>> eventsByVehicle = new HashMap<>();
        try (BufferedReader br = Files.newBufferedReader(Paths.get(datagramFile))) {
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (first) { first = false; continue; }
                String[] parts = line.split(",");
                if (parts.length < 11) continue; // expectation from sample

                String busId = parts[10].trim();
                String lineId = parts[6].trim();
                // keep row as array: stopId, latitude, longitude, datagramDate, busId, lineId
                eventsByVehicle.computeIfAbsent(busId + "@" + lineId, k -> new ArrayList<>()).add(parts);
            }
        }

        // 3) For each vehicle+line, sort by timestamp and accumulate per-arc stats
        Map<String, ArcStats> statsByArcId = new HashMap<>();

        for (Map.Entry<String, List<String[]>> entry : eventsByVehicle.entrySet()) {
            List<String[]> evts = entry.getValue();
            evts.sort((a, b) -> {
                try {
                    LocalDateTime ta = LocalDateTime.parse(a[9].trim(), DATE_TIME_FMT);
                    LocalDateTime tb = LocalDateTime.parse(b[9].trim(), DATE_TIME_FMT);
                    return ta.compareTo(tb);
                } catch (Exception e) { return 0; }
            });

            for (int i = 0; i < evts.size() - 1; i++) {
                String[] cur = evts.get(i);
                String[] next = evts.get(i + 1);

                String startStop = cur[2].trim();
                String endStop = next[2].trim();
                String lineId = cur[6].trim();

                String key = startStop + "->" + endStop + "@" + lineId;
                RouteGraphBuilder.Arc arc = arcByStops.get(key);
                if (arc == null) continue; // only consider pairs that correspond to an arc

                try {
                    LocalDateTime t1 = LocalDateTime.parse(cur[9].trim(), DATE_TIME_FMT);
                    LocalDateTime t2 = LocalDateTime.parse(next[9].trim(), DATE_TIME_FMT);
                    long seconds = Math.max(1, Duration.between(t1, t2).getSeconds());

                    double distance = arc.distance; // meters

                    ArcStats s = statsByArcId.computeIfAbsent(arc.arcId, k -> new ArcStats());
                    s.totalDistanceMeters += distance;
                    s.totalSeconds += seconds;
                    s.count += 1;
                } catch (Exception e) {
                    // ignore parse errors
                }
            }
        }

        // 4) Write CSV output
        try (BufferedWriter bw = Files.newBufferedWriter(Paths.get(outputCsv))) {
            bw.write("arcId,totalDistanceMeters,totalSeconds,avg_kmh,count\n");
            for (Map.Entry<String, ArcStats> e : statsByArcId.entrySet()) {
                String arcId = e.getKey();
                ArcStats s = e.getValue();
                double avgKmh = s.totalSeconds > 0 ? (s.totalDistanceMeters / s.totalSeconds) * 3.6 : 0.0;
                bw.write(String.format(Locale.ENGLISH, "%s,%.2f,%d,%.3f,%d\n", arcId, s.totalDistanceMeters, s.totalSeconds, avgKmh, s.count));
            }
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Uso: HistoricalSpeedCalculator <data_dir> <datagrams.csv> [output.csv]");
            System.out.println("Ejemplo: HistoricalSpeedCalculator ./sitm-mio/data datagrams4streaming.csv output_arc_speeds.csv");
            System.exit(1);
        }

        String dataDir = args[0];
        String datagramFile = args[1];
        String output = args.length > 2 ? args[2] : "arc_speeds.csv";

        HistoricalSpeedCalculator calc = new HistoricalSpeedCalculator();
        calc.compute(dataDir, datagramFile, output);

        System.out.println("Proceso completado. Salida: " + output);
    }
}
