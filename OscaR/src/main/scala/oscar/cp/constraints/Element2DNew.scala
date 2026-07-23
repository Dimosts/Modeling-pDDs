package oscar.cp.constraints

import oscar.algo.Inconsistency
import oscar.cp.core.CPPropagStrength
import oscar.cp.core.Constraint
import oscar.cp.core.variables.CPIntVar
import oscar.cp.core.variables.CPVar

/**
 * Element constraint: matrix[x][y] = z
 */
class Element2DNew(matrix: Array[Array[Int]], x: CPIntVar, y: CPIntVar, z: CPIntVar)
  extends Constraint(x.store, "Element2DNew") {

  private val n: Int = matrix.length
  private val m: Int = if (n == 0) 0 else matrix(0).length
  private val domx: Array[Int] = Array.ofDim[Int](math.max(n, 1))
  private val domy: Array[Int] = Array.ofDim[Int](math.max(m, 1))

  override def associatedVars(): Iterable[CPVar] = Array[CPVar](x, y, z)

  override def setup(l: CPPropagStrength): Unit = {
    x.updateMin(0)
    x.updateMax(n - 1)
    y.updateMin(0)
    y.updateMax(m - 1)
    x.callPropagateWhenDomainChanges(this)
    y.callPropagateWhenDomainChanges(this)
    z.callPropagateWhenBoundsChange(this)
    propagate()
  }

  override def propagate(): Unit = {
    val xs = x.fillArray(domx)
    val ys = y.fillArray(domy)
    val zMin = z.min
    val zMax = z.max

    var min = Int.MaxValue
    var max = Int.MinValue

    var i = 0
    while (i < xs) {
      val xi = domx(i)
      var xHasSupport = false
      var j = 0
      while (j < ys) {
        val yj = domy(j)
        val value = matrix(xi)(yj)
        if (value >= zMin && value <= zMax) {
          xHasSupport = true
          if (value < min) min = value
          if (value > max) max = value
        }
        j += 1
      }
      if (!xHasSupport) {
        x.removeValue(xi)
      }
      i += 1
    }

    var j = 0
    while (j < ys) {
      val yj = domy(j)
      var yHasSupport = false
      i = 0
      while (i < xs) {
        val xi = domx(i)
        val value = matrix(xi)(yj)
        if (value >= zMin && value <= zMax) {
          yHasSupport = true
        }
        i += 1
      }
      if (!yHasSupport) {
        y.removeValue(yj)
      }
      j += 1
    }

    if (min == Int.MaxValue) {
      throw Inconsistency.get
    }
    z.updateMin(min)
    z.updateMax(max)
  }
}
