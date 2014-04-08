package org.discovery.akka.example

import scala.concurrent._
import ExecutionContext.Implicits.global

/**
 * Created by jonathan on 18/12/13.
 */

trait Node {
  def getValue: Int
}

case class StableNode(val i: Int) extends Node {

  def getValue: Int = i

  override def toString: String = s"StableNode($i)"
}

case class UnstableNode(val i: Int) extends Node {

  def getValue: Int = i

  override def toString: String = s"UnstableNode($i)"
}










object MayFail {
  def protect[T](t: => T): MayFail[T] = MayFailImpl(t)
}

trait MayFail[T] {

  def executeInProtectedSpace[R](f: (T => R)): Future[R]

  def watch(failureCallBack: T => Unit)
}

case class CallBack[T](f: (T => Unit), id: Int)

case class MayFailImpl[T](var unsafeResource: T) extends MayFail[T] {

  var callback: CallBack[T] = CallBack(_ => None, 0)

  def watch(fcb: T => Unit) {
    callback = CallBack(fcb, callback.id + 1)
  }

  def executeInProtectedSpace[R](f: (T => R)): Future[R] = {
    val future = Future {
      f(unsafeResource)
    }
    future onFailure {
      case n =>
        callback.f(unsafeResource)
    }
    future
  }

  // only for testing purpose
  def destroyResource() {
    unsafeResource = null.asInstanceOf[T]
  }

  override def toString: String = s"MayFail($unsafeResource)"
}






object GoodMayFailTest extends App {


  // simulate node failure
  def simulateFailure(nodes: List[MayFail[Node]]): List[MayFail[Node]] = {

    nodes.foreach( n =>
      n match {
        case m@MayFailImpl(b@UnstableNode(_)) =>
          // inject null object (eq: failedNode)
          m.destroyResource()
        case _ =>
      }
    )

    nodes
  }

  // same as Vivaldi.giveSomeCloseNodeOutside()
  object Overlay {
    var nextId: Int = 0
    def giveOneNeighbour(nodes: List[MayFail[Node]]): MayFail[Node] = {
      nextId += 1
      if (nextId == 3) {
        MayFail.protect(new UnstableNode(nextId))
      } else {
        MayFail.protect(new StableNode(nextId))
      }
    }
  }

  def applyReconfigurationPlan(nodes: List[MayFail[Node]) {

  }

  // same as enoughResources(p)
  def enoughResources(nodes: List[MayFail[Node]]): Boolean = {
    Thread.sleep(1000)


    nodes.map(node => {
      for {
        value <- node.executeInProtectedSpace(n => n.getValue)
      } yield {
        println(value)
      }
    })

    Thread.sleep(1000)

    println("calcul")


    nodes.size >= 10
  }




  var nodes: List[MayFail[Node]] = Nil

  while (!enoughResources(nodes)) {

    val node = Overlay.giveOneNeighbour(nodes)
    nodes = simulateFailure(nodes)

    node.watch(failedNode => {
      println("removing failed node")
      nodes = nodes.filter(failedNode => failedNode != node)
    })

    nodes = node :: nodes
    println(s"nodes: $nodes")
  }

  applyReconfigurationPlan(nodes)
  // wait for asynchronous code
  Thread.sleep(20000)
}