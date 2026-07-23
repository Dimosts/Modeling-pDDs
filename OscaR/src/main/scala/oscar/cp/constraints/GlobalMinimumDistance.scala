package oscar.cp.constraints

import oscar.algo.Inconsistency
import oscar.cp.core.CPPropagStrength
import oscar.cp.core.Constraint
import oscar.cp.core.variables.CPIntVar
import oscar.cp.core.variables.CPVar

/**
 * Global MinimumDistance constraint for pDD (Element fusion).
 *
 * Enforces for every facility pair (i, j):
 *   distance[x(i)][x(j)] >= max(minDist.min, dLb(i)(j) + 1)
 *
 * and tightens minDist.max to
 *   min over pairs of max feasible distance over Dom(x(i)) x Dom(x(j)).
 *
 * Builds one shared sorted-neighbor table from the distance matrix (once).
 * Per pair keeps only a low threshold (d_lb + 1); no FF / Element auxiliaries.
 */
class GlobalMinimumDistance(
  x: Array[CPIntVar],
  minDist: CPIntVar,
  distance: Array[Array[Int]],
  dLb: Array[Array[Int]]
) extends Constraint(x(0).store, "GlobalMinimumDistance") {

  private val P: Int = distance.length
  private val F: Int = x.length

  // Shared sorted neighbor table: neighbors[a] sorted by distance[a][_] descending
  private val neighbors: Array[Array[Int]] = Array.ofDim[Array[Int]](P)
  private val dists: Array[Array[Int]] = Array.ofDim[Array[Int]](P)

  // Compact pair list: (i, j, low = d_lb + 1)
  private val nPairs: Int = F * (F - 1) / 2
  private val pairI: Array[Int] = Array.ofDim[Int](nPairs)
  private val pairJ: Array[Int] = Array.ofDim[Int](nPairs)
  private val pairLow: Array[Int] = Array.ofDim[Int](nPairs)

  // Scratch buffers (stamped membership for Dom(right), no O(P) clear)
  private val domBuf: Array[Int] = Array.ofDim[Int](math.max(P, 1))
  private val marked: Array[Int] = Array.ofDim[Int](math.max(P, 1))
  private var markStamp: Int = 0

  buildSortedNeighbors()
  buildPairs()

  override def associatedVars(): Iterable[CPVar] = x ++ Array[CPVar](minDist)

  override def setup(l: CPPropagStrength): Unit = {
    var k = 0
    while (k < F) {
      x(k).updateMin(0)
      x(k).updateMax(P - 1)
      x(k).callPropagateWhenDomainChanges(this)
      k += 1
    }
    minDist.callPropagateWhenBoundsChange(this)
    propagate()
  }

  override def propagate(): Unit = {
    var changed = true
    while (changed) {
      changed = false
      val objMin = minDist.min

      var p = 0
      while (p < nPairs) {
        val i = pairI(p)
        val j = pairJ(p)
        val thr = math.max(objMin, pairLow(p))
        changed |= revisePair(x(i), x(j), thr)
        changed |= revisePair(x(j), x(i), thr)
        p += 1
      }

      var ub = Int.MaxValue
      p = 0
      while (p < nPairs) {
        val pairUb = maxDistanceOverDomains(x(pairI(p)), x(pairJ(p)))
        if (pairUb == Int.MinValue) throw Inconsistency.get
        if (pairUb < ub) ub = pairUb
        p += 1
      }
      if (ub == Int.MaxValue) throw Inconsistency.get
      val oldMax = minDist.max
      minDist.updateMax(ub)
      if (minDist.max < oldMax) changed = true
    }
  }

  /** Revise Dom(left) so every value has a support in Dom(right) at distance >= thr. */
  private def revisePair(left: CPIntVar, right: CPIntVar, thr: Int): Boolean = {
    fillInDom(right)
    val n = left.fillArray(domBuf)
    var removed = false
    var i = 0
    while (i < n) {
      val a = domBuf(i)
      if (!hasSupport(a, thr)) {
        left.removeValue(a)
        removed = true
      }
      i += 1
    }
    removed
  }

  private def hasSupport(a: Int, thr: Int): Boolean = {
    val pref = prefixLen(a, thr)
    val nb = neighbors(a)
    var k = 0
    while (k < pref) {
      if (marked(nb(k)) == markStamp) return true
      k += 1
    }
    false
  }

  /** Max distance over Dom(left) x Dom(right), using sorted neighbors. */
  private def maxDistanceOverDomains(left: CPIntVar, right: CPIntVar): Int = {
    fillInDom(right)
    val n = left.fillArray(domBuf)
    var best = Int.MinValue
    var i = 0
    while (i < n) {
      val a = domBuf(i)
      val nb = neighbors(a)
      val ds = dists(a)
      var k = 0
      val len = nb.length
      while (k < len) {
        if (marked(nb(k)) == markStamp) {
          val d = ds(k)
          if (d > best) best = d
          k = len // first hit is the max for this a (sorted descending)
        } else {
          k += 1
        }
      }
      i += 1
    }
    best
  }

  /** Number of neighbors of a with distance >= thr (prefix of sorted list). */
  private def prefixLen(a: Int, thr: Int): Int = {
    val ds = dists(a)
    var lo = 0
    var hi = ds.length
    while (lo < hi) {
      val mid = (lo + hi) >>> 1
      if (ds(mid) >= thr) lo = mid + 1
      else hi = mid
    }
    lo
  }

  private def fillInDom(v: CPIntVar): Unit = {
    markStamp += 1
    if (markStamp == Int.MaxValue) {
      var t = 0
      while (t < P) {
        marked(t) = 0
        t += 1
      }
      markStamp = 1
    }
    val n = v.fillArray(domBuf)
    var i = 0
    while (i < n) {
      marked(domBuf(i)) = markStamp
      i += 1
    }
  }

  private def buildSortedNeighbors(): Unit = {
    var a = 0
    while (a < P) {
      val tmp = new Array[(Int, Int)](math.max(P - 1, 0))
      var k = 0
      var b = 0
      while (b < P) {
        if (a != b) {
          tmp(k) = (b, distance(a)(b))
          k += 1
        }
        b += 1
      }
      java.util.Arrays.sort(tmp, new java.util.Comparator[(Int, Int)] {
        override def compare(u: (Int, Int), v: (Int, Int)): Int = {
          val c = java.lang.Integer.compare(v._2, u._2) // descending distance
          if (c != 0) c else java.lang.Integer.compare(u._1, v._1)
        }
      })
      val nb = Array.ofDim[Int](tmp.length)
      val ds = Array.ofDim[Int](tmp.length)
      k = 0
      while (k < tmp.length) {
        nb(k) = tmp(k)._1
        ds(k) = tmp(k)._2
        k += 1
      }
      neighbors(a) = nb
      dists(a) = ds
      a += 1
    }
  }

  private def buildPairs(): Unit = {
    var p = 0
    var i = 0
    while (i < F) {
      var j = i + 1
      while (j < F) {
        pairI(p) = i
        pairJ(p) = j
        pairLow(p) = dLb(i)(j) + 1
        p += 1
        j += 1
      }
      i += 1
    }
  }
}
