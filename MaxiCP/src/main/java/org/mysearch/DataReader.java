package org.mysearch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * Reads a pDD instance: nLocations nFacilities, pairwise distances, then pairwise min-distance constraints.
 * Distances are truncated to integers (same as OscaR / Choco with decimalPoints=0).
 */
public final class DataReader {

    public static final class Instance {
        public final int nLocations;
        public final int nFacilities;
        public final int[][] distance;
        public final int[][] minDistance;

        public Instance(int nLocations, int nFacilities, int[][] distance, int[][] minDistance) {
            this.nLocations = nLocations;
            this.nFacilities = nFacilities;
            this.distance = distance;
            this.minDistance = minDistance;
        }
    }

    private DataReader() {}

    public static Instance read(String filePath) throws IOException {
        return read(filePath, 0);
    }

    public static Instance read(String filePath, int decimalPoints) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(filePath)).stream()
                .map(String::trim)
                .filter(l -> !l.isEmpty())
                .toList();

        String[] header = lines.get(0).split("\\s+");
        int nLocations = Integer.parseInt(header[0]);
        int nFacilities = Integer.parseInt(header[1]);

        double scale = Math.pow(10, decimalPoints);
        int[][] distance = new int[nLocations][nLocations];
        int idx = 1;
        for (int i = 0; i < nLocations - 1; i++) {
            for (int j = i + 1; j < nLocations; j++) {
                String[] parts = lines.get(idx++).split("\\s+");
                int val = (int) (Double.parseDouble(parts[2]) * scale);
                distance[i][j] = val;
                distance[j][i] = val;
            }
        }

        int[][] minDistance = new int[nFacilities][nFacilities];
        int consLen = nFacilities * (nFacilities - 1) / 2;
        for (int k = 0; k < consLen; k++) {
            String[] parts = lines.get(idx++).split("\\s+");
            int a = Integer.parseInt(parts[0]);
            int b = Integer.parseInt(parts[1]);
            int val = (int) (Double.parseDouble(parts[2]) * scale);
            minDistance[a][b] = val;
            minDistance[b][a] = val;
        }

        return new Instance(nLocations, nFacilities, distance, minDistance);
    }

    public static int maxDistance(int[][] distance) {
        return Arrays.stream(distance).flatMapToInt(Arrays::stream).max().orElse(0);
    }
}
