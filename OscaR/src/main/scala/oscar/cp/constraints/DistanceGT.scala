package oscar.cp.constraints

import oscar.algo.Inconsistency
import oscar.cp.core.CPPropagStrength
import oscar.cp.core.Constraint
import oscar.cp.core.variables.CPIntVar
import oscar.cp.core.variables.CPVar

/**
 * Ternary DistanceGT constraint from the pDD paper.
 *
 * Enforces: distanceMatrix[F1][F2] >= max(minDist.min, d_lb + 1)
 * (integer strict inequality: dist > d_lb  <=>  dist >= d_lb + 1)
 *
 * Also tightens minDist.max to the maximum feasible pairwise distance
 * over Dom(F1) x Dom(F2).
 *
 * Note: supports are checked against the distance matrix directly (no per-constraint
 * BitSet tables). That keeps memory O(1) per pair instead of O(P^2), which is
 * required for large instances (e.g. P=2000, F=100).
 */
class DistanceGT(
  F1: CPIntVar,
  F2: CPIntVar,
  minDist: CPIntVar,
  distanceMatrix: Array[Array[Int]],
  d_lb: Int
) extends Constraint(F1.store, "DistanceGT") {

  private val P: Int = distanceMatrix.length
  private val baseGE: Int = d_lb + 1

  private val domF1: Array[Int] = Array.ofDim[Int](math.max(P, 1))
  private val domF2: Array[Int] = Array.ofDim[Int](math.max(P, 1))

  override def associatedVars(): Iterable[CPVar] = Array[CPVar](F1, F2, minDist)

  override def setup(l: CPPropagStrength): Unit = {
    F1.updateMin(0)
    F1.updateMax(P - 1)
    F2.updateMin(0)
    F2.updateMax(P - 1)
    F1.callPropagateWhenDomainChanges(this)
    F2.callPropagateWhenDomainChanges(this)
    minDist.callPropagateWhenBoundsChange(this)
    propagate()
  }

  override def propagate(): Unit = {
    var changed = true
    while (changed) {
      changed = false
      val thrGE = math.max(minDist.min, baseGE)

      changed |= reviseF1wrtF2(thrGE)
      changed |= reviseF2wrtF1(thrGE)

      if (F1.isBound && F2.isBound) {
        val d = distanceMatrix(F1.min)(F2.min)
        val oldMax = minDist.max
        minDist.updateMax(d)
        if (minDist.max < oldMax) changed = true
      } else {
        val ubPair = maxDistanceOverDomains()
        if (ubPair == Int.MinValue) throw Inconsistency.get
        val oldMax = minDist.max
        minDist.updateMax(ubPair)
        if (minDist.max < oldMax) changed = true
      }
    }
  }

  private def reviseF1wrtF2(thrGE: Int): Boolean = {
    val xs = F1.fillArray(domF1)
    val ys = F2.fillArray(domF2)
    var removed = false
    var i = 0
    while (i < xs) {
      val a = domF1(i)
      if (!hasSupportAinF2(a, thrGE, ys)) {
        F1.removeValue(a)
        removed = true
      }
      i += 1
    }
    removed
  }

  private def reviseF2wrtF1(thrGE: Int): Boolean = {
    val ys = F2.fillArray(domF2)
    val xs = F1.fillArray(domF1)
    var removed = false
    var j = 0
    while (j < ys) {
      val b = domF2(j)
      if (!hasSupportBinF1(b, thrGE, xs)) {
        F2.removeValue(b)
        removed = true
      }
      j += 1
    }
    removed
  }

  private def hasSupportAinF2(a: Int, thrGE: Int, ys: Int): Boolean = {
    var j = 0
    while (j < ys) {
      if (distanceMatrix(a)(domF2(j)) >= thrGE) return true
      j += 1
    }
    false
  }

  private def hasSupportBinF1(b: Int, thrGE: Int, xs: Int): Boolean = {
    var i = 0
    while (i < xs) {
      if (distanceMatrix(domF1(i))(b) >= thrGE) return true
      i += 1
    }
    false
  }

  private def maxDistanceOverDomains(): Int = {
    val xs = F1.fillArray(domF1)
    val ys = F2.fillArray(domF2)
    var best = Int.MinValue
    var i = 0
    while (i < xs) {
      val a = domF1(i)
      var j = 0
      while (j < ys) {
        val d = distanceMatrix(a)(domF2(j))
        if (d > best) best = d
        j += 1
      }
      i += 1
    }
    best
  }
}
