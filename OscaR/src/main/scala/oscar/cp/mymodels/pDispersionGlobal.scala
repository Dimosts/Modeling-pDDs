package oscar.cp.mymodels

import oscar.cp._
import oscar.cp.constraints.GlobalMinimumDistance

import scala.io.Source

/**
 * Global MinimumDistance pDD model.
 * One shared objective minDist; one GlobalMinimumDistance over all facility pairs
 * (shared sorted neighbor table, per-pair lows, no FF auxiliaries).
 */
object pDispersionGlobal extends CPModel with App {

  val filename = if (args.length > 0) args(0) else "data/input.txt"
  val searchHeuristic = if (args.length > 1) args(1) else ""
  val lines = Source.fromFile(filename).getLines().flatMap(_.split("\\s+")).filter(_.nonEmpty)
  println(s"OscaR with Global MinimumDistance constraint")
  println(s"Filename: ${filename}")

  def nextInt: Int = lines.next().toInt
  def nextDouble: Double = lines.next().toDouble

  val nLocations = nextInt
  val nFacilities = nextInt

  val distance = Array.ofDim[Int](nLocations, nLocations)
  for (i <- 0 until nLocations) {
    for (j <- i + 1 until nLocations) {
      val i_ = nextInt
      val j_ = nextInt
      distance(i)(j) = nextDouble.toInt
      distance(j)(i) = distance(i)(j)
    }
  }

  val minDistance = Array.ofDim[Int](nFacilities, nFacilities)
  for (i <- 0 until nFacilities) {
    for (j <- i + 1 until nFacilities) {
      val i_ = nextInt
      val j_ = nextInt
      minDistance(i)(j) = nextDouble.toInt
      minDistance(j)(i) = minDistance(i)(j)
    }
  }

  val maxDist = distance.map(_.max).max

  val x = Array.fill(nFacilities)(CPIntVar(0 until nLocations))
  val minDist = CPIntVar(0 to maxDist)

  add(new GlobalMinimumDistance(x, minDist, distance, minDistance))

  maximize(minDist)

  if (searchHeuristic == "domwdeg") {
    println("Using dom/wdeg")
    search {
      binaryMinDomOnWeightedDegree(x) ++ binaryStaticIdx(IndexedSeq(minDist), _ => minDist.max)
    }
  } else if (searchHeuristic == "conflict") {
    println("Using conflict ordering search with first-fail (minDom)")
    search {
      conflictOrderingSearch(x, i => x(i).size, i => x(i).min) ++ binaryStaticIdx(IndexedSeq(minDist), _ => minDist.max)
    }
  } else if (searchHeuristic == "lexico") {
    println("Using lexico var/val ordering")
    search {
      binaryStatic(x.toIndexedSeq) ++ binaryStaticIdx(IndexedSeq(minDist), _ => minDist.max)
    }
  } else {
    search {
      println("Using first-fail")
      binaryFirstFail(x.toIndexedSeq) ++ binaryStaticIdx(IndexedSeq(minDist), _ => minDist.max)
    }
  }

  val t0 = System.currentTimeMillis()
  onSolution {
    val t1 = System.currentTimeMillis()
    val elapsedTime = (t1 - t0) / 1000.0
    println(s"Solution found! Objective: ${minDist.value}")
    println(s"Locations: ${x.map(_.value).mkString(", ")}")
    println(s"Time (s): ${elapsedTime}")
  }

  val stats = start(timeLimit = 200)

  println(stats)
  println(s"Total Time (s): ${stats.time / 1000.0}")
  println(s"Nodes: ${stats.nNodes}")
}
