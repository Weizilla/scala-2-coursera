package suggestions



import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner
import rx.lang.scala._
import suggestions.gui._
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.language.postfixOps
import scala.util.{Failure, Success}


@RunWith(classOf[JUnitRunner])
class WikipediaApiTest extends FunSuite {

  object mockApi extends WikipediaApi {
    def wikipediaSuggestion(term: String) = Future {
      if (term.head.isLetter) {
        for (suffix <- List(" (Computer Scientist)", " (Footballer)")) yield term + suffix
      } else {
        List(term)
      }
    }
    def wikipediaPage(term: String) = Future {
      "Title: " + term
    }
  }

  import mockApi._

  test("WikipediaApi should make the stream valid using sanitized") {
    val notvalid = Observable.just("erik", "erik meijer", "martin")
    val valid = notvalid.sanitized

    var count = 0
    var completed = false

    val sub = valid.subscribe(
      term => {
        assert(term.forall(_ != ' '))
        count += 1
      },
      t => assert(false, s"stream error $t"),
      () => completed = true
    )
    assert(completed && count == 3, "completed: " + completed + ", event count: " + count)
  }

  test("recovered") {
    val results: Observable[Int] = Observable.apply({
      s => {
        s.onNext(throw new RuntimeException)
      }
    })
    val recovered = results.recovered

    val sub = recovered.subscribe(
      t => println(t)
    )
  }

  test("timeout") {
    val results: Observable[Long] = Observable.interval(1.seconds)
    val timeout = results.timedOut(3)
    timeout.subscribe(println(_))
//    readLine()
  }

  test("WikipediaApi should correctly use concatRecovered") {
    val requests = Observable.just(1, 2, 3)
    val remoteComputation = (n: Int) => Observable.just(0 to n : _*)
    val responses = requests concatRecovered remoteComputation
    val sum = responses.foldLeft(0) { (acc, tn) =>
      tn match {
        case Success(n) => acc + n
        case Failure(t) => throw t
      }
    }
    var total = -1
    val sub = sum.subscribe {
      s => total = s
    }
    assert(total == (1 + 1 + 2 + 1 + 2 + 3), s"Sum: $total")
  }

}
