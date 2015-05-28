package actorbintree

import actorbintree.BinaryTreeNode.{CopyFinished, CopyTo}
import actorbintree.BinaryTreeSet.{Remove, Contains, ContainsResult, Insert, OperationFinished}
import akka.actor.{ActorSystem, Props}
import akka.testkit.TestProbe
import com.typesafe.config.ConfigFactory

class BinaryTreeNodeSuite(_system: ActorSystem) extends Tester (_system)
{
//  def this() = this(ActorSystem("BinaryTreeNodeSuite"))
    def this() = this(ActorSystem("BinaryTreeNodeSuite",
  ConfigFactory.parseString( """akka {
           loglevel = "DEBUG"
           actor {
             debug {
               receive = on
               lifecycle = off
             }
           }
         }""").withFallback(ConfigFactory.load())))

  test("contains") {
    val node = system.actorOf(Props(classOf[BinaryTreeNode], 1, false))
    val probe = TestProbe()

    val ops = List(
      Contains(probe.ref, id=2, 1)
    )

    val expected = List(
      ContainsResult(id=2, true)
    )

    verify(node, probe, ops, expected)
  }

  test("insert") {
    val node = system.actorOf(Props(classOf[BinaryTreeNode], 1, false))
    val probe = TestProbe()

    val newElement = 42

    val ops = List(
      Insert(probe.ref, id = 1, newElement),
      Contains(probe.ref, id = 2, newElement)
    )

    val expected = List(
      OperationFinished(1),
      ContainsResult(id = 2, true)
    )

    verify(node, probe, ops, expected)
  }

  test("remove") {
    val node = system.actorOf(Props(classOf[BinaryTreeNode], 1, false))
    val probe = TestProbe()

    val newElement = 2

    val ops = List(
      Insert(probe.ref, 1, newElement),
      Contains(probe.ref, 2, newElement),
      Remove(probe.ref, 3, newElement),
      Contains(probe.ref, 4, newElement)
    )

    val expected = List(
      OperationFinished(1),
      ContainsResult(2, true),
      OperationFinished(3),
      ContainsResult(4, false)
    )

    verify(node, probe, ops, expected)
  }

  test("copy") {
    val oldNode = system.actorOf(Props(classOf[BinaryTreeNode], 1, false), "old")
    val newNode = system.actorOf(Props(classOf[BinaryTreeNode], 1, false), "new")
    val probe = TestProbe()

    val newElement = 42

    val oldNodeOps = List(
      Insert(probe.ref, 1, newElement),
      Contains(probe.ref, 2, newElement),
      CopyTo(probe.ref, 3,newNode)
    )

    val oldNodeExpected = List(
      OperationFinished(1),
      ContainsResult(2, true),
      CopyFinished(3)
    )

    val newNodeOps = List(
      Contains(probe.ref, 4, newElement)
    )

    val newNodeExpected = List(
      ContainsResult(4, true)
    )

    verify(oldNode, probe, oldNodeOps, oldNodeExpected)
    verify(newNode, probe, newNodeOps, newNodeExpected)
  }
}
