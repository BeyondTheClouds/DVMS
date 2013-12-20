package org.discovery.akka

import scala.concurrent._
import ExecutionContext.Implicits.global

/**
 * Created by jonathan on 18/12/13.
 */

trait Node {
  def getValue: Int
}

case class GoodNode(val i: Int) extends Node {

  def getValue: Int = i

  override def toString: String = s"GoodNode($i)"
}

case class BadNode(val i: Int) extends Node {

  def getValue: Int = i

  override def toString: String = s"BadNode($i)"
}










object MayFail {
  def protect[T](t: => T): MayFail[T] = MayFailImpl(t)
}

trait MayFail[T] {

//  def get: Future[T]

  def executeInProtectedSpace[R](f: (T => R)): Future[R]

  def watch(failureCallBack: T => Unit)
}

case class CallBack[T](f: (T => Unit), id: Int)

case class MayFailImpl[T](var unsafeRessource: T) extends MayFail[T] {

  var callback: CallBack[T] = CallBack(_ => None, 0)

//  def get: Future[T] = {
//    val future = Future {
//      unsafeRessource
//    }
//    future onFailure {
//      case n =>
//        callback.f(unsafeRessource)
//    }
//    future
//  }

  def watch(fcb: T => Unit) {
    callback = CallBack(fcb, callback.id + 1)
  }

  def executeInProtectedSpace[R](f: (T => R)): Future[R] = {
    val future = Future {
      f(unsafeRessource)
    }
    future onFailure {
      case n =>
        callback.f(unsafeRessource)
    }
    future
  }


  // only for testing purpose
  def destroyRessource() {
    unsafeRessource = null.asInstanceOf[T]
  }

  override def toString: String = s"MayFail($unsafeRessource)"
}







object BadMayFailTest extends App {

  def productNodes(i: Int): Node = {
    if (i == 3) {
      new BadNode(i)
    } else {
      new GoodNode(i)
    }
  }

  def workOnNodes(nodes: List[Node]): Boolean = {
    Thread.sleep(2000)

    nodes.size >= 5
  }

  var i: Int = 1
  var nodes: List[Node] = Nil
  while (!workOnNodes(nodes)) {
    nodes = productNodes(i) :: nodes
  }

  //  println(workOnNodes(productNodes()))

  // wait for asynchronous code
  Thread.sleep(2000)
}

object GoodMayFailTest extends App {


  // simulate node failure
  def simulateFailure(nodes: List[MayFail[Node]]): List[MayFail[Node]] = {

    nodes.foreach( n =>
      n match {
        case m@MayFailImpl(b@BadNode(_)) =>
          // inject null object (eq: failedNode)
          m.destroyRessource()
        case _ =>
      }
    )

    nodes
  }

  // same as Vivaldi.giveSomeCloseNodeOutside()
  def productNodes(i: Int): MayFail[Node] = {
    if (i == 3) {
      MayFail.protect(new BadNode(i))
    } else {
      MayFail.protect(new GoodNode(i))
    }
  }



  // same as enoughRessources(p)
  def workOnNodes(nodes: List[MayFail[Node]]): Boolean = {
    Thread.sleep(1000)


    nodes.map(node => {
      for {
        value <- node.executeInProtectedSpace(n => n.getValue)
      } yield {
        println(value)
      }
    })

    Thread.sleep(1000)

    nodes.size >= 10
  }

  var i: Int = 1
  var nodes: List[MayFail[Node]] = Nil

  while (!workOnNodes(nodes)) {

    val node = productNodes(i)
    nodes = simulateFailure(nodes)

    node.watch(failedNode => {
      println("removing failed node")
      nodes = nodes.filter(failedNode => failedNode != node)
    })

    nodes = node :: nodes
    println(s"nodes: $nodes")

    i = i + 1
  }

  // wait for asynchronous code
  Thread.sleep(20000)
}

object futureTest extends App {

  val future = Future {
    val test: Option[Int] = null
    test.get
  }

  future onFailure {
    case e => println("failed!")
  }

  Thread.sleep(200)

}