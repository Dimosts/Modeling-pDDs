package org.mysearch;

import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.search.SearchStatistics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import static org.maxicp.cp.CPFactory.*;
import static org.mysearch.PDispersionElement.selectBranching;

/**
 * Table model (M_tb) for the p-dispersion problem with distance constraints (pDD), using MaxiCP.
 *
 * Usage: java -jar maxicp-table.jar &lt;instance&gt; [firstfail|conflict|lexico|domwdeg] [timeLimitSec]
 */
public class PDispersionTable {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java -jar maxicp-table.jar <instance> [firstfail|conflict|lexico|domwdeg] [timeLimitSec]");
            return;
        }

        String filename = args[0];
        String heuristic = args.length > 1 ? args[1] : "firstfail";
        int timeLimitSec = args.length > 2 ? Integer.parseInt(args[2]) : 3600;

        System.out.println("MaxiCP with Table constraints");
        System.out.println("Filename: " + filename);

        DataReader.Instance inst = DataReader.read(filename);
        int nLocations = inst.nLocations;
        int nFacilities = inst.nFacilities;
        int[][] distance = inst.distance;
        int[][] minDistance = inst.minDistance;
        int maxDist = DataReader.maxDistance(distance);

        System.out.println("Locations: " + nLocations + ", Facilities: " + nFacilities);

        CPSolver cp = makeSolver();
        CPIntVar[] x = makeIntVarArray(cp, nFacilities, nLocations);

        List<CPIntVar> FF = new ArrayList<>();
        for (int i = 0; i < nFacilities; i++) {
            for (int j = i + 1; j < nFacilities; j++) {
                CPIntVar ff = makeIntVar(cp, 0, maxDist);
                int dlb = minDistance[i][j];

                List<int[]> allowed = new ArrayList<>();
                for (int loc1 = 0; loc1 < nLocations; loc1++) {
                    for (int loc2 = 0; loc2 < nLocations; loc2++) {
                        if (loc1 == loc2) continue;
                        int dist = distance[loc1][loc2];
                        if (dist > dlb) {
                            allowed.add(new int[]{loc1, loc2, dist});
                        }
                    }
                }
                cp.post(table(new CPIntVar[]{x[i], x[j], ff}, allowed.toArray(new int[0][])));
                FF.add(ff);
            }
        }

        CPIntVar objectiveVar = minimum(FF.toArray(new CPIntVar[0]));
        Objective obj = cp.maximize(objectiveVar);

        Supplier<Runnable[]> branching = selectBranching(heuristic, x);

        DFSearch dfs = makeDfs(cp, branching);
        long t0 = System.currentTimeMillis();
        dfs.onSolution(() -> {
            double elapsed = (System.currentTimeMillis() - t0) / 1000.0;
            System.out.println("Solution found! Objective: " + objectiveVar.min());
            System.out.println("Locations: " + Arrays.toString(Arrays.stream(x).mapToInt(CPIntVar::min).toArray()));
            System.out.println("Time (s): " + elapsed);
        });

        long deadline = t0 + timeLimitSec * 1000L;
        SearchStatistics stats = dfs.optimize(obj, s -> System.currentTimeMillis() >= deadline);

        System.out.println(stats);
        System.out.println("Total Time (s): " + ((System.currentTimeMillis() - t0) / 1000.0));
        System.out.println("Nodes: " + stats.numberOfNodes());
    }
}
