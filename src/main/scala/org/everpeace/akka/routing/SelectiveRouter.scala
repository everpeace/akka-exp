package org.everpeace.akka.routing

import akka.routing.Dispatcher
import akka.actor.ForwardableChannel._
import akka.actor.UntypedChannel._
import akka.actor.{Scheduler, ActorRef, Actor}
import akka.transactor.{Coordinated, Transactor}
import akka.util.duration._
import akka.stm.{Ref, TransactionFactory, TransactionalMap, atomic}
import java.util.concurrent.{ConcurrentHashMap, TimeUnit}
import scala.collection.JavaConversions._
import akka.util.Duration
import akka.event.EventHandler

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
      case None => {
        EventHandler.info(this, "minimum load actor cannot be selected.")
        EventHandler.info(this, "message [" + x.toString + "] will be routed random target.")
        actors(scala.util.Random.nextInt(actors.length))
      }
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
  protected def collect: Any => Seq[(ActorRef, Load)]
}

// 集めてきたloadの集合からminimumをもつActorを選択するSelector
trait MinLoadSelector extends Selector {
  override def select = msg => minLoadActor(collect(msg))

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

  // 保持しているactor,loadの組を返す
  // override it yourself!
  def collect: Any => Seq[(ActorRef, Load)]
}

trait MapStorageCollector extends Collector {
  protected val loadRequestTimeout: Duration
  protected var loadMap = Map[ActorRef, Ref[Float]]()

  atomic {
    for (actor <- actors) loadMap += actor -> Ref[Float]
  }

  def storedLoads = loadMap.toSeq.flatMap(entry => atomic {
    entry._2.opt match {
      case Some(load) => Seq((entry._1, load))
      case None => Seq.empty
    }
  })

  protected def updateLoads(actor: ActorRef)
  = (actor.?(RequestLoad)(timeout = loadRequestTimeout)).as[ReportLoad] match {
    case Some(ReportLoad(load)) => {
      atomic {
        loadMap(actor).swap(load)
      }
      EventHandler.info(this, "[uuid=" + actor.uuid + "]'s load is updated to " + loadMap(actor).get + ".")
    }
    case None => {
      atomic {
        loadMap(actor).swap(null.asInstanceOf[Float])
      }
      EventHandler.info(this, "[uuid=" + actor.uuid + "] didn't answer. So the load is reset.")
    }
  }
}

trait PollingCollector extends MapStorageCollector {
  // these vals should be override as 'lazy val'
  // because polling starts below (in initial block) using these values.
  val initialDelay: Long
  val betweenPollingDelay: Long
  val delayTimeUnit: TimeUnit

  // schedule polling to each actors
  actors foreach {
    actor => Scheduler.schedule(() => updateLoads(actor), initialDelay, betweenPollingDelay, delayTimeUnit)
  }

  // just returned stored loads
  override def collect = _ => storedLoads
}

trait OnDemandCollector extends MapStorageCollector {
  override def collect = _ => {
    actors foreach {
      a => updateLoads(a)
    }
    storedLoads
  }
}