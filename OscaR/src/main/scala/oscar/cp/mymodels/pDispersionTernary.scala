package oscar.cp.mymodels

import oscar.cp._
import oscar.cp.constraints.DistanceGT

import scala.io.Source

/**
 * Ternary pDD model (M_t) using DistanceGT constraints.
 * One shared objective variable minDist; each facility pair posts DistanceGT.
 */
object pDispersionTernary extends CPModel with App {

  val filename = if (args.length > 0) args(0) else "data/input.txt"
  val searchHeuristic = if (args.length > 1) args(1) else ""
  val lines = Source.fromFile(filename).getLines().flatMap(_.split("\\s+")).filter(_.nonEmpty)
  println(s"OscaR with Ternary DistanceGT constraints")
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

  for {
    i <- 0 until nFacilities
    j <- i + 1 until nFacilities
  } {
    val d_lb = minDistance(i)(j)
    add(new DistanceGT(x(i), x(j), minDist, distance, d_lb))
  }

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

  val stats = start(timeLimit = 3600)

  println(stats)
  println(s"Total Time (s): ${stats.time / 1000.0}")
  println(s"Nodes: ${stats.nNodes}")
}
