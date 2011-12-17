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
  this: Actor =>
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

  // standard select strategies is implemented by Selector trait
  // Note: this have an argument of Any type so that
  // message dependent selection can be implemented.
  protected def select: Any => Option[ActorRef]
}

// Selectロジックを別に実装してmixinできるようにtrait化
trait Selector {
  protected def select: Any => Option[ActorRef]

  //standard collection strategy is implemented by Collector
  protected def collectedLoads: Seq[(ActorRef, Load)]
}

// loadの集合からminimumをもつActorを選択するSelector
trait MinLoadSelector extends Selector {
  override def select = _ => minLoadActor(collectedLoads)

  private def minLoadActor(loads: Seq[(ActorRef, Load)]): Option[ActorRef]
  = if (!loads.isEmpty) {
    val target = loads.reduce[(ActorRef, Float)] {
      (min, candidate) =>
        if (min._2 > candidate._2) candidate
        else min
    }
    Option(target._1)
  } else {
    None
  }

}

// Loadを集めるロジックを別に実装してmixinできるようにtrait化
trait Collector {
  // load collection targets
  protected val actors: Seq[ActorRef]

  //保持しているactor,loadの組を返す
  // override it yourself!
  def collectedLoads: Seq[(ActorRef, Float)]
}

trait PollingCollector extends Collector {
  private[this] val loads = TransactionalMap[ActorRef, Float]()

  override def collectedLoads = loads.toSeq

  //TODO how to polling ??
}

trait OnDemandCollector extends Collector {
  private[this] val loads = TransactionalMap[ActorRef, Load]()

  override def collectedLoads
  = if (loads.isEmpty) {
    updateLoadsFirst
    atomic {
      loads.toSeq
    }
  } else {
    updateLoads
    atomic {
      loads.toSeq
    }
  }

  //途中で集める時は一個ずつ集めるのをatomicに
  private def updateLoads
  = actors.foreach {
    a => atomic {
      (a ? RequestLoad).as[ReportLoad] match {
        case Some(ReportLoad(load)) => loads +=(a, load)
        case None =>
      }
    }
  }

  //最初だけは全部集め終わるのをatomicに
  private def updateLoadsFirst
  = atomic {
    actors.foreach {
      a => (a ? RequestLoad).as[ReportLoad] match {
        case Some(ReportLoad(load)) => loads +=(a, load)
        case None =>
      }
    }
  }

}