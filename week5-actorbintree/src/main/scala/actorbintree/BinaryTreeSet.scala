/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */
package actorbintree

import actorbintree.BinaryTreeNode.{CopyFinished, CopyTo}
import akka.actor._

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

  def createRoot: ActorRef = context.actorOf(BinaryTreeNode.props(0, initiallyRemoved = true))

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
      root ! CopyTo(self, newRoot)
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
    case CopyFinished => {
      root = newRoot
      context.become(normal)
    }
  }

}

object BinaryTreeNode {
  trait Position

  case object Left extends Position
  case object Right extends Position

  case class CopyTo(requester: ActorRef, treeNode: ActorRef)
  case class CopyFinished(requester: ActorRef)

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
  val normal: Receive = {
    case c:Contains if c.elem < elem && subtrees.contains(Left) => subtrees(Left) ! c
    case c:Contains if c.elem > elem && subtrees.contains(Right) => subtrees(Right) ! c
    case r:Remove if r.elem < elem && subtrees.contains(Left) => subtrees(Left) ! r
    case r:Remove if r.elem > elem && subtrees.contains(Right) => subtrees(Right) ! r
    case Contains(actor, id, e) =>  actor ! ContainsResult(id, e == elem && ! removed)
    case Insert(actor, id, e) => {
      if (e < elem) {
        val newActor: ActorRef = context.actorOf(props(e, false))
        subtrees += Left -> newActor
      } else if (e > elem) {
        val newActor: ActorRef = context.actorOf(props(e, false))
        subtrees += Right -> newActor
      }
      actor ! OperationFinished(id)
    }
    case Remove(actor, id, e) => {
      removed = e == elem
      actor ! OperationFinished(id)
    }
    case CopyTo(r, d) => {
      log.info("Copy to " + d + " sender: " + sender)
      if (! removed) {
        d ! Insert(self, 0, elem)
        context.become(copying(subtrees.values.toSet, false, r))
      } else {
        r ! CopyFinished
      }
    }
  }

  // optional
  /** `expected` is the set of ActorRefs whose replies we are waiting for,
    * `insertConfirmed` tracks whether the copy of this node to the new tree has been confirmed.
    */
  def copying(expected: Set[ActorRef], insertConfirmed: Boolean, requester: ActorRef): Receive = {
    case OperationFinished(_) => {
      log.info("Copying op finished. " + sender)
      if (expected.nonEmpty) {
        expected.foreach(a => a ! CopyTo(self, sender))
        context.become(copying(expected, true, requester))
      } else {
        requester ! CopyFinished
      }
    }
    case CopyFinished => {
      val newExpected = expected - sender
      log.info("Copying copy finished: " + newExpected)
      if (newExpected.isEmpty) {
        log.info("Copying sending finished: " + requester)
        requester ! CopyFinished
      } else {
        context.become(copying(newExpected, insertConfirmed, requester))
      }
    }
  }


}
