/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */
package actorbintree

import actorbintree.BinaryTreeNode.{CopyFinished, CopyTo}
import actorbintree.BinaryTreeSet.OperationReply
import akka.actor._
import akka.event.LoggingReceive

import scala.collection.immutable.Queue

object BinaryTreeSet {

  trait Operation {
    def requester: ActorRef
    def id: Int
    def elem: Int
  }

  trait OperationReply {
    def id: Int
  }

  /** Request with identifier `id` to insert an element `elem` into the tree.
    * The actor at reference `requester` should be notified when this operation
    * is completed.
    */
  case class Insert(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request with identifier `id` to check whether an element `elem` is present
    * in the tree. The actor at reference `requester` should be notified when
    * this operation is completed.
    */
  case class Contains(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request with identifier `id` to remove the element `elem` from the tree.
    * The actor at reference `requester` should be notified when this operation
    * is completed.
    */
  case class Remove(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request to perform garbage collection*/
  case object GC

  /** Holds the answer to the Contains request with identifier `id`.
    * `result` is true if and only if the element is present in the tree.
    */
  case class ContainsResult(id: Int, result: Boolean) extends OperationReply

  /** Message to signal successful completion of an insert or remove operation. */
  case class OperationFinished(id: Int) extends OperationReply

}


class BinaryTreeSet extends Actor {
  import BinaryTreeSet._

  def createRoot: ActorRef = context.actorOf(BinaryTreeNode.props(0, initiallyRemoved = true),
    "root-" + System.currentTimeMillis())

  var root = createRoot

  // optional
  var pendingQueue = Queue.empty[Operation]

  // optional
  def receive = normal

  // optional
  /** Accepts `Operation` and `GC` messages. */
  val normal: Receive = {
    case GC =>  {
      val newRoot = createRoot
      root ! CopyTo(self, (math.random * 1000).toInt, newRoot)
      context.become(garbageCollecting(newRoot))
    }
    case m => root forward m
  }

  // optional
  /** Handles messages while garbage collection is performed.
    * `newRoot` is the root of the new binary tree where we want to copy
    * all non-removed elements into.
    */
  def garbageCollecting(newRoot: ActorRef): Receive = {
    case m:Operation => pendingQueue = pendingQueue.enqueue(m)
    case CopyFinished(_) => {
      root = newRoot
      pendingQueue.foreach(self ! _)
      context.become(normal)
    }
  }

}

object BinaryTreeNode {
  trait Position

  case object Left extends Position
  case object Right extends Position

  case class CopyTo(requester: ActorRef, id: Int, treeNode: ActorRef)
  case class CopyFinished(id: Int) extends OperationReply

  def props(elem: Int, initiallyRemoved: Boolean) = Props(classOf[BinaryTreeNode],  elem, initiallyRemoved)
}

class BinaryTreeNode(val elem: Int, initiallyRemoved: Boolean) extends Actor with ActorLogging {
  import BinaryTreeNode._
  import BinaryTreeSet._

  var subtrees = Map[Position, ActorRef]()
  var removed = initiallyRemoved

  // optional
  def receive = normal

  // optional
  /** Handles `Operation` messages and `CopyTo` requests. */
  val normal: Receive = LoggingReceive {
    case Remove(actor, id, e) => handleRemove(actor, id, e)
    case Contains(actor, id, e) => handleContains(actor, id, e)
    case Insert(actor, id, e) => handleInsert(actor, id, e)
    case CopyTo(r, id,d) => handleCopyTo(r, id, d)
  }

  def handleCopyTo(r: ActorRef, id: Int, d: ActorRef): Unit = {
    if (!removed) {
      log.debug(s"Insert $elem to $d")
      d ! Insert(self, (math.random * 1000).toInt, elem)
    }
    val newId = (math.random * 1000).toInt
    subtrees.values.foreach {
      a => {
        log.debug(s"Copy $a to $d, id $newId")
        a ! CopyTo(self, newId, d)
      }
    }

    if (removed && subtrees.isEmpty) {
      log.debug(s"Copy finished due to removed and empty")
      r ! CopyFinished(id)
    } else {
      log.debug(s"Entering copying state $r")
      context.become(copying(subtrees.values.toSet, removed, r, id))
    }
  }

  def handleInsert(actor: ActorRef, id: Int, e: Int): Unit = {
    if (e < elem) {
      if (subtrees.contains(Left)) {
        log.debug("Pass onto left actor " + subtrees(Left))
        subtrees(Left) ! Insert(actor, id, e)
      } else {
        val newActor: ActorRef = context.actorOf(props(e, false), e.toString)
        log.debug(s"Insert new left actor. $e < $elem $newActor")
        subtrees += Left -> newActor
        actor ! OperationFinished(id)
      }
    } else if (e > elem) {
      if (subtrees.contains(Right)) {
        log.debug("Pass onto right actor " + subtrees(Right))
        subtrees(Right) ! Insert(actor, id, e)
      } else {
        val newActor: ActorRef = context.actorOf(props(e, false), e.toString)
        log.debug(s"Insert new right actor $e > $elem $newActor")
        subtrees += Right -> newActor
        actor ! OperationFinished(id)
      }
    } else {
      removed = false
      actor ! OperationFinished(id)
    }
  }

  def handleContains(actor: ActorRef, id: Int, e: Int): Unit = {
    if (e < elem && subtrees.contains(Left)) {
      log.debug("Pass contains to left actor: " + subtrees(Left))
      subtrees(Left) ! Contains(actor, id, e)
    } else if (e > elem && subtrees.contains(Right)) {
      log.debug("Pass contains to right actor: " + subtrees(Right))
      subtrees(Right) ! Contains(actor, id, e)
    } else {
      val result = e == elem && !removed
      log.debug("Processing contains myself: " + result)
      actor ! ContainsResult(id, result)
    }
  }

  def handleRemove(actor: ActorRef, id: Int, e: Int): Unit = {
    if (e < elem && subtrees.contains(Left)) {
      subtrees(Left) ! Remove(actor, id, e)
    } else if (e > elem && subtrees.contains(Right)) {
      subtrees(Right) ! Remove(actor, id, e)
    } else {
      if (e == elem) {
        removed = true
      }
      actor ! OperationFinished(id)
    }
  }

  /** `expected` is the set of ActorRefs whose replies we are waiting for,
    * `insertConfirmed` tracks whether the copy of this node to the new tree has been confirmed.
    */
  def copying(expected: Set[ActorRef], insertConfirmed: Boolean, requestor: ActorRef, id: Int): Receive = {
    case OperationFinished(_) => {
      log.debug("Insert confirmed")
      if (expected.isEmpty) {
        requestor ! CopyFinished(id)
        log.debug(s"Entering normal state. Copy finished to $requestor $id")
        context.become(normal)
      } else {
        log.debug(s"Continue copy state. Waiting for $expected")
        context.become(copying(expected, true, requestor, id))
      }
    }
    case CopyFinished(_) => {
      val newExpected = expected - sender
      log.debug(s"Copying finished from $sender. Remaining $newExpected")
      if (newExpected.isEmpty && insertConfirmed) {
        requestor ! CopyFinished(id)
        log.debug(s"Entering normal state. Copy finished to $requestor $id")
        context.become(normal)
      } else {
        log.debug(s"Continue copy state. Waiting for ${newExpected.isEmpty} $insertConfirmed")
        context.become(copying(newExpected, insertConfirmed, requestor, id))
      }
    }
  }



}
