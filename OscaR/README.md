# OscaR models for pDD

How to build and run the OscaR solvers in this folder.

## Prerequisites

- **Java 21+** (Temurin / OpenJDK) — required for all models
- **sbt** — required only for the custom propagators below

### Install on Windows (winget)

```powershell
winget install EclipseAdoptium.Temurin.21.JDK
winget install sbt.sbt
```

Then open a **new** terminal, or refresh `PATH`:

```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
```

Check:

```powershell
java -version
sbt --version
```

All commands below assume you are in the `OscaR` directory:

```powershell
cd OscaR
```

### Heap size for sbt (PowerShell)

On PowerShell, `sbt -J-Xmx8g ...` fails (`Not a valid command: -`). Set the heap with `SBT_OPTS` instead:

```powershell
$env:SBT_OPTS="-Xmx8g"
```

Run that once per terminal session before any `sbt` command that needs more memory. Small GRID instances often work with the default heap (no `SBT_OPTS`).

---

## Prebuilt JARs (original Element / Table models)

These use OscaR’s built-in constraints (no sbt compile needed). Only Java is required.

### Element model (M<sub>el</sub>)

```powershell
java -Xms1g -Xmx8g -jar .\oscar-element.jar [problem_filepath] [heuristic]
```

### Table model (M<sub>tb</sub>)

```powershell
java -Xms1g -Xmx8g -jar .\oscar-table.jar [problem_filepath] [heuristic]
```

### Example

```powershell
java -Xms1g -Xmx4g -jar .\oscar-element.jar ..\Benchmarks\GRID\10-30-05\0.txt domwdeg
```

---

## Custom propagators (sbt)

Sources live under `src/main/scala/`. The project depends on the local `oscar-element.jar`.

### Build

```powershell
sbt compile
```

If another sbt session is already open and locks the server, either close it or compile with:

```powershell
sbt -Dsbt.server.autostart=false compile
```

### Element2DNew model (custom `matrix[x][y] = z` propagator)

```powershell
$env:SBT_OPTS="-Xmx8g"
sbt "runMain oscar.cp.mymodels.pDispersionElement2DNew [problem_filepath] [heuristic]"
```

Example:

```powershell
$env:SBT_OPTS="-Xmx8g"
sbt "runMain oscar.cp.mymodels.pDispersionElement2DNew ..\Benchmarks\GRID\10-30-05\0.txt domwdeg"
```

Constraint source: `src/main/scala/oscar/cp/constraints/Element2DNew.scala`  
Model source: `src/main/scala/oscar/cp/mymodels/pDispersionElement2DNew.scala`

### Ternary model (M<sub>t</sub>) with DistanceGT

```powershell
$env:SBT_OPTS="-Xmx8g"
sbt "runMain oscar.cp.mymodels.pDispersionTernary [problem_filepath] [heuristic]"
```

Example:

```powershell
$env:SBT_OPTS="-Xmx8g"
sbt "runMain oscar.cp.mymodels.pDispersionTernary ..\Benchmarks\GRID\10-30-05\0.txt domwdeg"
```

Constraint source: `src/main/scala/oscar/cp/constraints/DistanceGT.scala`  
Model source: `src/main/scala/oscar/cp/mymodels/pDispersionTernary.scala`

### Run without sbt (after compile)

```powershell
java -Xms1g -Xmx8g -cp "target\scala-2.13\classes;oscar-element.jar" oscar.cp.mymodels.pDispersionElement2DNew "..\Benchmarks\GRID\10-30-05\0.txt" domwdeg

java -Xms1g -Xmx8g -cp "target\scala-2.13\classes;oscar-element.jar" oscar.cp.mymodels.pDispersionTernary "..\Benchmarks\GRID\10-30-05\0.txt" domwdeg
```

---

## Search heuristics

Pass as the second argument (optional; default is first-fail):

| Argument     | Meaning                                      |
|--------------|----------------------------------------------|
| `domwdeg`    | Dom / weighted degree                        |
| `lexico`     | Static / lexicographic order (ternary model) |
| `conflict`   | Conflict ordering search                     |
| *(omitted)*  | First-fail                                   |

---

## Useful benchmark paths

Paths are relative to `OscaR/`:

| Size (points × facilities) | Example path |
|----------------------------|--------------|
| Small GRID                 | `..\Benchmarks\GRID\10-30-05\0.txt` |
| Small BINS                 | `..\Benchmarks\BINS\bins_500_50_div8\0.txt` |
| Large BINS                 | `..\Benchmarks\BINS\bins_2000_100_div8\0.txt` |
| Large MDPLIB               | `..\Benchmarks\MDPLIB\MDG-b_40_n2000_m100_div8\MDG-b_40_n2000_m100_new1.txt` |
| Heavy GRID                 | `..\Benchmarks\GRID\60_1000_150_div8\0.txt` |

Default time limit in the Scala models is **3600 seconds**.

For large instances, prefer a larger heap (`-Xmx8g` for `java`, or `$env:SBT_OPTS="-Xmx8g"` for sbt).

---

## Comparing models on the same instance

```powershell
# Built-in Element
java -Xms1g -Xmx8g -jar .\oscar-element.jar ..\Benchmarks\GRID\10-30-05\0.txt domwdeg

# Custom Element2DNew
$env:SBT_OPTS="-Xmx8g"
sbt "runMain oscar.cp.mymodels.pDispersionElement2DNew ..\Benchmarks\GRID\10-30-05\0.txt domwdeg"

# Ternary DistanceGT
$env:SBT_OPTS="-Xmx8g"
sbt "runMain oscar.cp.mymodels.pDispersionTernary ..\Benchmarks\GRID\10-30-05\0.txt domwdeg"
```

Compare **objective**, **nNodes**, **nFails**, and **time** in the printed stats.
