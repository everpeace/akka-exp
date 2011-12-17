package org.everpeace.akka.routing

import akka.routing.Dispatcher
import akka.actor.{ActorRef, Actor}
import akka.actor.ForwardableChannel._
import akka.actor.UntypedChannel._
import akka.event.EventHandler
import akka.stm.TransactionalMap
import akka.stm.atomic
import java.util.concurrent.TimeUnit

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
  // Note: this has an argument of Any type
  // so that message dependent selection can be implemented.
  protected def select: Any => Option[ActorRef]
}

// Selectロジックを別に実装してmixinできるようにtrait化
trait Selector {
  protected def select: Any => Option[ActorRef]

  //standard collection strategy is implemented by Collector
  // Note: this has an argument of Any type
  // so that message dependent selection can be implemented.
  protected def collectLoads: Any => Seq[(ActorRef, Load)]
}

// 集めてきたloadの集合からminimumをもつActorを選択するSelector
trait MinLoadSelector extends Selector {
  override def select = msg => minLoadActor(collectLoads(msg))

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
  def collectLoads: Any => Seq[(ActorRef, Float)]
}

trait PollingCollector extends Collector {
  protected val initialDelay: Long
  protected val betweenPollingDelay: Long
  protected val delayTimeUnit: TimeUnit
  private[this] val loadMap = TransactionalMap[ActorRef, Float]()

  // Actor polling and update loads.
  private val pollingActor = Actor.actorOf(new Actor {
    protected def receive = {
      case msg@RequestLoad
      => if (loadMap.isEmpty) {
        updateLoadsFirst
      } else {
        updateLoads
      }
    }
  })

  override def collectLoads = _ => {
    val loadSeq = atomic {
      loadMap.toSeq
    }
    EventHandler.info(this, loadSeq)
    loadSeq
  }

  // call this method when Actor's preStart()
  def startPolling
  = {
    pollingActor.start
    akka.actor.Scheduler.schedule(pollingActor, RequestLoad, initialDelay, betweenPollingDelay, delayTimeUnit)
  }

  //途中で集める時は一個ずつ集めるのをatomicに
  private def updateLoads
  = actors.foreach {
    a => atomic {
      (a ? RequestLoad).as[ReportLoad] match {
        case Some(ReportLoad(load)) => loadMap +=(a, load)
        case None =>
      }
    }
  }

  //最初だけは全部集め終わるのをatomicに
  private def updateLoadsFirst
  = atomic {
    actors.foreach {
      a => (a ? RequestLoad).as[ReportLoad] match {
        case Some(ReportLoad(load)) => loadMap +=(a, load)
        case None =>
      }
    }
  }

}

trait OnDemandCollector extends Collector {
  private[this] val loads = TransactionalMap[ActorRef, Load]()

  override def collectLoads = _ => {
    if (loads.isEmpty) {
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