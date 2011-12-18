package org.everpeace.akka.routing.sample

import akka.actor._
import akka.actor.Actor._
import org.everpeace.akka.routing._
import akka.event.EventHandler
import akka.routing.{CyclicIterator, InfiniteIterator}
import akka.util.duration._
import akka.util.Duration

/**
 *
 * User: Shingo Omura <everpeace _at_ gmail _dot_ com>
 * Date: 11/12/16
 */

object MinLoadSelectiveRoutingTestServiceStarter {
  def main(args: Array[String]) = run

  def run: Unit = {
    actorOf[MinLoadSelectiveRoutingService].start()
  }
}

// test 用のリモートサービス MinLoadSelectiveRouterを作ってリモートサービスに登録する
class MinLoadSelectiveRoutingService extends Actor {
  // 30個のアクターへ振り分けるシナリオを想定。
  // 各アクターの負荷は0~100で毎回ランダムに返答される
  // 各アクターの負荷返答にかかる時間は0~0.5[s]のランダムな時間を想定する
  private val actors = Seq.tabulate(30) {
    n => RandomLoadActor("actor-" + ((n + 1) toString), 0, 100, 0.5 second).start()
  }

  // 各アクターへのpolling間隔 1[s], 収集timeout 0.45[s](時々タイムアウトするはず)
  private val selectiveRouter
  = minLoadSelectiveRouter(0.5 second, 1 second, 0.45 second, actors).start()

  protected def receive = {
    case x => selectiveRouter forward x
  }

  override def preStart() = {
    remote.start("localhost", 2552)
    remote.register("routing:service", self)
  }
}

//テスト用にloadの数値列を与えてそれを順番に返すReporter
trait LoadSequenceReporter extends LoadReporter {
  this: Actor =>
  val name: String
  val loadSeq: InfiniteIterator[Load]
  val responseTime: Duration

  protected def reportLoad = {
    val load = loadSeq.next()
    //負荷返答に0~responseTime[ms]までのランダムな時間かかる想定
    Thread.sleep(scala.util.Random.nextLong() % responseTime.toMillis)
    EventHandler.info(this, "[%s(uuid=%s)] report Load=%f" format(name, self.uuid, load))
    load
  }
}

trait RandomLoadReporter extends LoadReporter {
  this: Actor =>
  val minLoad: Int
  val maxLoad: Int
  val name: String
  val responseTime: Duration

  protected def reportLoad = {
    val load = (minLoad + scala.util.Random.nextInt(maxLoad - minLoad)).toFloat
    //負荷返答に0~responseTime[ms]までのランダムな時間かかる想定
    Thread.sleep(scala.util.Random.nextLong().abs % responseTime.toMillis)
    //EventHandler.info(this, "[%s(uuid=%s)] report Load=%f" format(name, self.uuid, load))
    load
  }
}

// sample用のサービスActorのActorRef用のextractor
object LoadSeqActor {
  def apply(name: String, responseTime: Duration, loads: Load*)
  = Actor.actorOf(new LoadSeqActor(name, responseTime, new CyclicIterator[Load](loads.toList)))
}

// sample用アクターサービス
class LoadSeqActor(val name: String, val responseTime: Duration, val loadSeq: InfiniteIterator[Load]) extends Actor with LoadSequenceReporter {
  def receive = requestLoad orElse forward

  def forward: Receive = {
    case x => {
      val retString = "%s called" format (name)
      EventHandler.info(this, retString)
      self.reply(retString)
    }
  }
}

object RandomLoadActor {
  def apply(name: String, min: Int, max: Int, responseTime: Duration) = Actor.actorOf(new RandomLoadActor(name, min, max, responseTime))
}

class RandomLoadActor(val name: String, val minLoad: Int, val maxLoad: Int, val responseTime: Duration) extends Actor with RandomLoadReporter {
  def receive = requestLoad orElse forward

  def forward: Receive = {
    case x => {
      val retString = "%s called" format (name)
      EventHandler.info(this, retString)
      self.reply(retString)
    }
  }
}
