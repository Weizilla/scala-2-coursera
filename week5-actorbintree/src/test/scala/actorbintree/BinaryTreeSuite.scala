/**
 * Copyright (C) 2009-2015 Typesafe Inc. <http://www.typesafe.com>
 */
package actorbintree

import actorbintree.BinaryTreeSet.{Operation, OperationReply}
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike, Matchers}

import scala.concurrent.duration._
import scala.util.Random

class Tester(_system: ActorSystem) extends TestKit(_system) with FunSuiteLike with
Matchers with BeforeAndAfterAll with ImplicitSender {

  def receiveN(requester: TestProbe, ops: Seq[Any], expectedReplies: Seq[Any]): Unit =
    requester.within(10.seconds) {
      val repliesUnsorted = for (i <- 1 to ops.size) yield {
        try { {
          requester.expectMsgType[OperationReply]
        }
        } catch {
          case ex: Throwable if ops.size > 10 => {
            {
              ops.foreach(println)
              fail(s"failure to receive confirmation $i/${ops.size}", ex)
            }
          }
          case ex: Throwable => {
            fail(s"failure to receive confirmation $i/${ops.size}\nRequests:" + ops.mkString("\n    ", "\n     ", ""), ex)
          }
        }
      }
      val replies = repliesUnsorted.sortBy(_.id)
      if (replies != expectedReplies) {
        val zipped = (replies zip expectedReplies).zipWithIndex
//        zipped.foreach(println)
        val pairs = zipped filter (x => x._1._1 != x._1._2)
        fail("unexpected replies:" + pairs.map(x => s"at index ${x._2}: got ${x._1._1}, expected ${x._1._2}").mkString("\n    ", "\n    ", ""))
      }
    }

  def verify(probe: TestProbe, ops: Seq[Operation], expected: Seq[OperationReply]): Unit = {
    val topNode = system.actorOf(Props[BinaryTreeSet])

    ops foreach { op =>
      topNode ! op
    }

    receiveN(probe, ops, expected)
    // the grader also verifies that enough actors are created
  }

  def verify(node: ActorRef, probe: TestProbe, ops: Seq[Any], expected: Seq[Any]): Unit = {

    ops foreach { op =>
      node ! op
    }

    receiveN(probe, ops, expected)
    // the grader also verifies that enough actors are created
  }
}

class BinaryTreeSuite(_system: ActorSystem) extends Tester(_system)
{

//  def this() = this(ActorSystem("BinaryTreeSuite"))
  def this() = this(ActorSystem("BinaryTreeSuite", ConfigFactory.parseString( """akka {
         loglevel = "DEBUG"
         actor {
           debug {
             receive = on
             lifecycle = off
           }
         }
       }""").withFallback(ConfigFactory.load())))

  override def afterAll: Unit = system.shutdown()

  import actorbintree.BinaryTreeSet._

  test("proper inserts and lookups") {
    val topNode = system.actorOf(Props[BinaryTreeSet])

    topNode ! Contains(testActor, id = 1, 1)
    expectMsg(ContainsResult(1, false))

    topNode ! Insert(testActor, id = 2, 1)
    topNode ! Contains(testActor, id = 3, 1)

    expectMsg(OperationFinished(2))
    expectMsg(ContainsResult(3, true))
  }

  test("proper inserts and lookups with GC") {
    val topNode = system.actorOf(Props[BinaryTreeSet])

    topNode ! Contains(testActor, id = 1, 1)
    expectMsg(ContainsResult(1, false))

    topNode ! Insert(testActor, id = 2, 1)
    expectMsg(OperationFinished(2))

    topNode ! Contains(testActor, id = 3, 1)
    expectMsg(ContainsResult(3, true))

    topNode ! GC

    topNode ! Contains(testActor, id = 4, 1)
    expectMsg(ContainsResult(4, true))
  }

  test("proper inserts and lookups with GC 2") {
    val topNode = system.actorOf(Props[BinaryTreeSet])
    topNode ! Insert(testActor, id = 2, 1)
    expectMsg(OperationFinished(2))

    topNode ! Contains(testActor, id = 3, 1)
    expectMsg(ContainsResult(3, true))

    topNode ! Remove(testActor, id = 4, 1)
    expectMsg(OperationFinished(4))

    topNode ! GC

    topNode ! Contains(testActor, id = 5, 1)
    expectMsg(ContainsResult(5, false))
  }

  test("proper inserts and lookups 2") {
    val requester = TestProbe()
    val requesterRef = requester.ref

    val ops = List(
      Insert(requesterRef, 0, 36),
//      Contains(requesterRef, 1, 59),
      Insert(requesterRef, 2, 80),
//      Remove(requesterRef, 3, 19),
//      Remove(requesterRef, 4, 68),
//      Remove(requesterRef, 5, 4),
//      Remove(requesterRef, 6, 91),
//      Contains(requesterRef, 7, 25),
//      Contains(requesterRef, 8, 3),
//      Insert(requesterRef, 9, 16),
//      Insert(requesterRef, 10, 36),
//      Remove(requesterRef, 11, 62),
//      Remove(requesterRef, 12, 26),
//      Remove(requesterRef, 13, 91),
//      Contains(requesterRef, 14, 18),
//      Remove(requesterRef, 15, 91),
      Contains(requesterRef, 16, 80)
    )

    val expected = List(
      OperationFinished(0),
//      ContainsResult(1, false),
      OperationFinished(2),
//      OperationFinished(3),
//      OperationFinished(4),
//      OperationFinished(5),
//      OperationFinished(6),
//      ContainsResult(7, false),
//      ContainsResult(8, false),
//      OperationFinished(9),
//      OperationFinished(10),
//      OperationFinished(11),
//      OperationFinished(12),
//      OperationFinished(13),
//      ContainsResult(14, false),
//      OperationFinished(15),
      ContainsResult(16, true)
    )

    verify(requester, ops, expected)
  }

  test("instruction example") {
    val requester = TestProbe()
    val requesterRef = requester.ref
    val ops = List(
      Insert(requesterRef, id=100, 1),
      Contains(requesterRef, id=50, 2),
      Remove(requesterRef, id=10, 1),
      Insert(requesterRef, id=20, 2),
      Contains(requesterRef, id=80, 1),
      Contains(requesterRef, id=70, 2)
      )

    val expectedReplies = List(
      OperationFinished(id=10),
      OperationFinished(id=20),
      ContainsResult(id=50, false),
      ContainsResult(id=70, true),
      ContainsResult(id=80, false),
      OperationFinished(id=100)
      )

    verify(requester, ops, expectedReplies)
  }

  test("behave identically to built-in set (no GC)") {
    val rnd = new Random()
    def randomOperations(requester: ActorRef, count: Int): Seq[Operation] = {
      def randomElement: Int = rnd.nextInt(100)
      def randomOperation(requester: ActorRef, id: Int): Operation = rnd.nextInt(4) match {
        case 0 => {
          Insert(requester, id, randomElement)
        }
        case 1 => {
          Insert(requester, id, randomElement)
        }
        case 2 => {
          Contains(requester, id, randomElement)
        }
        case 3 => {
          Remove(requester, id, randomElement)
        }
      }

      for (seq <- 0 until count) yield {
        randomOperation(requester, seq)
      }
    }

    def referenceReplies(operations: Seq[Operation]): Seq[OperationReply] = {
      var referenceSet = Set.empty[Int]
      def replyFor(op: Operation): OperationReply = op match {
        case Insert(_, seq, elem) => {
          referenceSet = referenceSet + elem
          OperationFinished(seq)
        }
        case Remove(_, seq, elem) => {
          referenceSet = referenceSet - elem
          OperationFinished(seq)
        }
        case Contains(_, seq, elem) => {
          ContainsResult(seq, referenceSet(elem))
        }
      }

      for (op <- operations) yield {
        replyFor(op)
      }
    }

    val requester = TestProbe()
    val topNode = system.actorOf(Props[BinaryTreeSet])
    val count = 1000

    val ops = randomOperations(requester.ref, count)
    val expectedReplies = referenceReplies(ops)

    ops foreach { op =>
      topNode ! op
      println(op)
    }
    receiveN(requester, ops, expectedReplies)
  }

  test("behave identically to built-in set (includes GC)") {
    val rnd = new Random()
    def randomOperations(requester: ActorRef, count: Int): Seq[Operation] = {
      def randomElement: Int = rnd.nextInt(100)
      def randomOperation(requester: ActorRef, id: Int): Operation = rnd.nextInt(4) match {
        case 0 => {
          Insert(requester, id, randomElement)
        }
        case 1 => {
          Insert(requester, id, randomElement)
        }
        case 2 => {
          Contains(requester, id, randomElement)
        }
        case 3 => {
          Remove(requester, id, randomElement)
        }
      }

      for (seq <- 0 until count) yield {
        randomOperation(requester, seq)
      }
    }

    def referenceReplies(operations: Seq[Operation]): Seq[OperationReply] = {
      var referenceSet = Set.empty[Int]
      def replyFor(op: Operation): OperationReply = op match {
        case Insert(_, seq, elem) => {
          referenceSet = referenceSet + elem
          OperationFinished(seq)
        }
        case Remove(_, seq, elem) => {
          referenceSet = referenceSet - elem
          OperationFinished(seq)
        }
        case Contains(_, seq, elem) => {
          ContainsResult(seq, referenceSet(elem))
        }
      }

      for (op <- operations) yield {
        replyFor(op)
      }
    }

    val requester = TestProbe()
    val topNode = system.actorOf(Props[BinaryTreeSet])
    val count = 1000

    val ops = randomOperations(requester.ref, count)
    val expectedReplies = referenceReplies(ops)

    ops foreach { op =>
      topNode ! op
      if (rnd.nextDouble() < 0.1) {
        topNode ! GC
      }
    }
    receiveN(requester, ops, expectedReplies)
  }
}
