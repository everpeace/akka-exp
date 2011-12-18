package org.everpeace.akka.routing

import akka.routing.Dispatcher
import akka.actor.ForwardableChannel._
import akka.actor.UntypedChannel._
import akka.event.EventHandler
import akka.actor.{Scheduler, ActorRef, Actor}
import akka.transactor.{Coordinated, Transactor}
import akka.util.duration._
import akka.stm.{Ref, TransactionFactory, TransactionalMap, atomic}
import java.util.concurrent.{ConcurrentHashMap, TimeUnit}
import scala.collection.JavaConversions._

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
  def collectLoads: Any => Seq[(ActorRef, Load)]
}

trait MapStorageCollector extends Collector {
  protected val loadMap = new ConcurrentHashMap[ActorRef, Ref[Float]]()
  protected val initialized = Ref(false)
  atomic {
    for (actor <- actors) loadMap.put(actor, Ref[Float]())
  }

  //個々のloadをpollingしてupdateするtransactors
  //トランザクションの境界はCoorinatedオブジェクトで外側から制御する。
  private val updators = for (a <- actors) yield Actor.actorOf(
    new Transactor {
      def atomically = {
        case msg@RequestLoad => (a ? RequestLoad).as[ReportLoad] match {
          case Some(ReportLoad(load)) => loadMap.get(a).alter(_ => load)
          case None => {
            EventHandler.info(this, "actor[uuid=" + a.uuid + "] didn't answer its load.")
          }
        }
      }
    }
  ).start()

  def loadSeq = {
    val _loadSeq: Seq[(ActorRef, Load)] =
      loadMap.entrySet().toSeq.flatMap(entry => atomic {
        entry.getValue.opt match {
          case Some(load) => Seq((entry.getKey, load))
          case None => Seq.empty
        }
      })
    EventHandler.info(this, _loadSeq)
    _loadSeq
  }

  //途中で集める時は一個ずつ集めるのをatomicに
  protected def updateLoads
  = updators foreach {
    _ ! Coordinated(RequestLoad)
  }

  //最初だけは全部集め終わるのをatomicに
  protected def updateLoadsFirst
  = {
    val coordinated = Coordinated()
    updators foreach {
      _ ! coordinated(RequestLoad)
    }
    coordinated atomic {}
  }
}

trait PollingCollector extends MapStorageCollector {
  // these vals should be override as 'lazy val'
  // because polling actor starts itself and polling here
  // using these values.
  val initialDelay: Long
  val betweenPollingDelay: Long
  val delayTimeUnit: TimeUnit

  // Actor which polls updators
  protected val pollingActor = Actor.actorOf(new Actor {
    protected def receive = {
      case msg@RequestLoad
      => if (!initialized.get()) {
        updateLoadsFirst
        atomic {
          initialized.set(true)
        }
      } else {
        updateLoads
      }
    }
  })
  pollingActor.start
  Scheduler.schedule(pollingActor, RequestLoad, initialDelay, betweenPollingDelay, delayTimeUnit)

  // just returned the loads collected.
  override def collectLoads = _ => loadSeq
}

trait OnDemandCollector extends MapStorageCollector {
  override def collectLoads = _ => {
    if (!initialized.get()) {
      updateLoadsFirst
      atomic {
        initialized.set(true)
      }
      loadSeq
    } else {
      updateLoads
      loadSeq
    }
  }
}