package org.everpeace.akka.routing

import akka.routing.Dispatcher
import akka.actor.{ActorRef, Actor}
import akka.actor.ForwardableChannel._
import akka.actor.UntypedChannel._
import akka.event.EventHandler
import akka.stm.TransactionalMap
import akka.stm.atomic

/**
 *
 * User: Shingo Omura <everpeace _at_ gmail _dot_ com>
 * Date: 11/12/16
 */

trait SelectiveRouter extends Dispatcher {
  this: Actor with Selector =>

  protected val actors: Seq[ActorRef]

  override def broadcast(message: Any) =
    actors.foreach {
      a => if (isSenderDefined) a.forward(message)(someSelf)
      else a.!(message)(None)
    }

  protected def routes = {
    case x => select(x) match {
      case Some(ref) => ref
      // selectが何らかの理由でNoneを返した場合はランダムに選ぶ
      case None => actors(scala.util.Random.nextInt(actors.length))
    }
  }
}

// Selectロジックを別に定義出来るようにtraitとして定義
trait Selector {
  def select: Any => Option[ActorRef]
}

// loadの集合からminimumをもつActorを選択するSelector
trait MinLoadSelector extends Selector {
  protected val actors: Seq[ActorRef]
  private[this] val loads = TransactionalMap[ActorRef, Float]()

  // TODO loadを集めるのはpollingするようにしたい。
  override def select = _ => {
    if (loads.isEmpty) {
      updateFirst
      minLoadActor
    } else {
      updateLoads
      minLoadActor
    }
  }

  // 最小負荷actorを探すのはatomicに。
  // TODO minLoadとminActorを保持してupdateの時にこの値を変更して、選ぶ時はそいつをただ返すだけにする。
  private def minLoadActor = atomic {
    val target = loads.reduce[(ActorRef, Float)] {
      (min, candidate) =>
        if (min._2 > candidate._2) candidate
        else min
    }
    Some(target._1)
  }

  //途中で集める時は一個ずつ集めるのをatomicに
  private def updateLoads = actors.foreach {
    a =>
      atomic {
        (a ? RequestLoad()).as[ReportLoad] match {
          case Some(ReportLoad(load)) => loads +=(a, load)
          case None =>
        }
      }
  }

  //最初だけは全部集め終わるのをatomicに
  private def updateFirst = atomic {
    actors.foreach {
      a =>
        (a ? RequestLoad()).as[ReportLoad] match {
          case Some(ReportLoad(load)) => loads +=(a, load)
          case None =>
        }
    }
  }

}