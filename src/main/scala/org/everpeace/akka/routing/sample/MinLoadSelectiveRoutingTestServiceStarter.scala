package org.everpeace.akka.routing.sample

import akka.actor._
import akka.actor.Actor._
import org.everpeace.akka.routing._
import akka.event.EventHandler
import akka.routing.{CyclicIterator, InfiniteIterator}
import akka.util.duration._
import akka.util.Duration
import java.util.concurrent.TimeUnit
import scala.util.Random._

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
  // 各アクターの負荷は0~20で毎回ランダムに返答される
  // 各アクターの負荷返答にかかる時間はN(1000[ms],400[ms])な時間を想定する
  private val actors = Seq.tabulate(30) {
    n => RandomLoadActor("actor-" + ((n + 1) toString), 0, 20, 1000, 100, TimeUnit.MILLISECONDS).start()
  }

  // 各アクターへの
  // polling間隔 1.5[s]
  // 収集timeout 1.2[s] (タイムアウト確率約3% (P(X in μ+_2σ)~95.4%))
  private val selectiveRouter
  = minLoadSelectiveRouter(50 millis, (1000 + 5 * 100) millis, (1000 + 2 * 100) millis, actors).start()

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

trait RandomLoadReporter extends AverageLoadReporter {
  this: Actor =>
  val minLoad: Int
  val maxLoad: Int
  val name: String
  val responseTimeAverage: Long
  val responseTimeStdDev: Long
  val responseTimeUnit: TimeUnit

  protected def reportLoad = {
    val load = (minLoad + scala.util.Random.nextInt(maxLoad - minLoad)).toFloat
    sleep
    EventHandler.info(this, "[%s(uuid=%s)] report Load=%f" format(name, self.uuid, load))
    load
  }

  //N(ave,stdDev^2)従う乱数[timeunit]スリープする
  private def sleep = {
    val rand = (responseTimeStdDev * nextGaussian() + responseTimeAverage) toInt
    val duration = Duration(rand, responseTimeUnit)
    if (rand > 0) Thread.sleep(duration.toMillis)
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
  def apply(name: String, min: Int, max: Int, responseTimeAverage: Long, responseTimeVar: Long, responseTimeUnit: TimeUnit)
  = Actor.actorOf(new RandomLoadActor(name, min, max, responseTimeAverage, responseTimeVar, responseTimeUnit))
}

class RandomLoadActor(val name: String, val minLoad: Int, val maxLoad: Int, val responseTimeAverage: Long, val responseTimeStdDev: Long, val responseTimeUnit: TimeUnit) extends Actor with RandomLoadReporter {
  protected lazy val historyLength = 3

  def receive = requestLoad orElse forward

  def forward: Receive = {
    case x => {
      val retString = "%s called" format (name)
      EventHandler.info(this, retString)
      self.reply(retString)
    }
  }
}
