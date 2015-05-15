package quickcheck

import org.scalacheck.Arbitrary._
import org.scalacheck.Prop._
import org.scalacheck._

abstract class QuickCheckHeap extends Properties("Heap") with IntHeap {

  property("min1") = forAll { a: Int =>
    val h = insert(a, empty)
    findMin(h) == a
  }

  property("min2") = forAll { (a: Int, b:Int) =>
    val h1 = insert(a, empty)
    val h2 = insert(b, h1)
    findMin(h2) == math.min(a, b)
  }

  property("insertThenDelete") = forAll { a: Int =>
    val h1 = insert(a, empty)
    val h2 = deleteMin(h1)
    isEmpty(h2)
  }

  property("meld") = forAll (genHeap, genHeap) { (h1: H, h2: H) =>
    val minI = math.min(findMin(h1), findMin(h2))
    val h3 = meld(h1, h2)
    minI == findMin(h3)
  }

  property("meld2") = forAll {
    (i1: Int, i2: Int) =>
      val minI = math.min(i1, i2)
      val h1b = insert(i1, empty)
      val h2b = insert(i2, empty)
      val m = meld(h1b, h2b)
      minI == findMin(m)
  }

  property("list") = forAll {
    (in: List[Int]) => {
      checkMin2(in)
    }
  }

  def checkMin2(in: List[Int]): Boolean = {
    var h1 = empty
    for (i <- in) {
      h1 = insert(i, h1)
    }

    val sortedIn = in.sorted
    for (i <- sortedIn) {
      val minI = findMin(h1)
      if (i != minI) {
        return false
      } else {
        h1 = deleteMin(h1)
      }
    }
    true
  }

  property("sortedDelete") = forAll(genHeap) { (h: H) =>
    checkMin(h)
  }

  def checkMin(h: H): Boolean = {
    if (isEmpty(h)) {
      return true
    }

    val newMin = findMin(h)
    val newHeap = deleteMin(h)
    if (! isEmpty(newHeap)) {
      val nextMin = findMin(h)
      nextMin >= newMin && checkMin(newHeap)
    } else {
      true
    }
  }

  lazy val genHeap: Gen[H] = {
    for {
      x <- arbitrary[Int]
      h <- Gen.oneOf(Gen.const(empty), genHeap)
    } yield insert(x, h)
  }

  implicit lazy val arbHeap: Arbitrary[H] = Arbitrary(genHeap)
}
