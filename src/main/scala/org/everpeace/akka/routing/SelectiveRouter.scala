package org.everpeace.akka.routing

import akka.routing.Dispatcher
import akka.actor.ForwardableChannel._
import akka.actor.UntypedChannel._
import akka.actor.{Scheduler, ActorRef, Actor}
import akka.stm.{Ref, atomic}
import java.util.concurrent.TimeUnit
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
      case None => {
        // selectが何らかの理由でNoneを返した場合はランダムに選ぶ
        // これが起きるのは過負荷状態（ルート先が全部非常に重くなっている)ので、
        // たぶん投げても帰ってこないが一応ルーターとしてどこかに投げておく。
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

//上位N個の中からランダムに選ぶ
trait RandomTopNSelector extends Selector {
  val N: Int
  assert(N > 0, "N must be positive")

  implicit val ord = new Ordering[(ActorRef, Load)] {
    def compare(x: (ActorRef, Load), y: (ActorRef, Load)) = if (x._2 != y._2) {
      (x._2 - y._2) toInt
    } else {
      -1 + scala.util.Random.nextInt(3)
    }
  }

  override def select = msg => selectRandomTopN(collect(msg))

  private def selectRandomTopN(loads: Seq[(ActorRef, Load)]): Option[ActorRef]
  = if (N < loads.length) {
    val topN = (loads.sorted.take(N))
    EventHandler.info(this, "select randomly in top " + N + " actors:" + topN)
    Some(topN(scala.util.Random.nextInt(N))._1)
  } else if (0 < loads.length) {
    Some(loads(scala.util.Random.nextInt(loads.length))._1)
  } else {
    None
  }
}

// 集めてきたloadの集合からminimumをもつActorを選択するSelector
trait MinLoadSelector extends RandomTopNSelector {
  lazy val N = 1
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
  // 各アクターに負荷を問い合わせるときのタイムアウト設定
  protected val loadRequestTimeout: Duration
  // 各アクターの負荷を保持するマップとその初期化
  protected var loadMap = Map[ActorRef, Ref[Float]]()
  atomic {
    for (actor <- actors) loadMap += actor -> Ref[Float]
  }

  //保持している負荷を返す
  def storedLoads = loadMap.toSeq.flatMap(entry => atomic {
    entry._2.opt match {
      case Some(load) => Seq((entry._1, load))
      case None => Seq.empty
    }
  })
}

trait PollingCollector extends MapStorageCollector {
  // these vals should be override as 'lazy val'
  // because polling starts below (in initial block) using these values.
  val initialDelay: Long
  val betweenPollingDelay: Long
  val delayTimeUnit: TimeUnit

  // 負荷をポーリングする
  // 各アクターへの負荷は輻輳しないのでアクターにしない
  actors foreach {
    actor => Scheduler.schedule(() => updateLoad(actor), initialDelay, betweenPollingDelay, delayTimeUnit)
  }
  //  updators foreach {
  //    updator => Scheduler.schedule(updator, RequestLoad, initialDelay, betweenPollingDelay, delayTimeUnit)
  //  }

  // just returned stored loads
  override def collect = _ => storedLoads

  // actorに負荷を問い合わせて負荷マップを更新する
  def updateLoad(actor: ActorRef) = (actor.?(RequestLoad)(timeout = loadRequestTimeout)).as[ReportLoad] match {
    case Some(ReportLoad(Some(load))) => {
      atomic {
        loadMap(actor).swap(load)
      }
      EventHandler.info(this, "[uuid=" + actor.uuid + "]'s load is updated to " + loadMap(actor).get + ".")
    }
    case _ => {
      atomic {
        loadMap(actor).swap(null.asInstanceOf[Float])
      }
      EventHandler.info(this, "[uuid=" + actor.uuid + "] didn't answer. So the load is reset.")
    }
  }
}

trait OnDemandCollector extends MapStorageCollector {
  // 各アクターの負荷を更新するアクター
  protected val updators: Seq[ActorRef] = for (actor <- actors) yield Actor.actorOf(
    new Actor {
      protected def receive = {
        case RequestLoad =>
          (actor.?(RequestLoad)(timeout = loadRequestTimeout)).as[ReportLoad] match {
            case Some(ReportLoad(Some(load))) => {
              atomic {
                loadMap(actor).swap(load)
              }
              EventHandler.info(this, "[uuid=" + actor.uuid + "]'s load is updated to " + loadMap(actor).get + ".")
            }
            case _ => {
              atomic {
                loadMap(actor).swap(null.asInstanceOf[Float])
              }
              EventHandler.info(this, "[uuid=" + actor.uuid + "] didn't answer. So the load is reset.")
            }
          }
      }
    }).start

  override def collect = _ => {
    updators foreach (_ ! ReportLoad)
    storedLoads
  }
}