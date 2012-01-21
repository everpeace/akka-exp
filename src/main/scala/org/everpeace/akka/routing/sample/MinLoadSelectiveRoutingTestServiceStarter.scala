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

// テスト用のリモートサービスの作成
// ロードバランサーにフォワードするだけのアクター
class MinLoadSelectiveRoutingService extends Actor {
  // numServers個のアクターへ振り分けるシナリオを想定。
  // マシンによってあまり増やしすぎるとスレッド数が多くなってパフォーマンス激減する
  val numServers = 4
  val loadMyu = 200 millis // 負荷の平均
  val loadSigma = 30 millis //負荷の標準偏差

  // サーバー群の作成
  // 各アクターの負荷返答にかかる時間はN(1000[ms],100[ms])な時間を想定する
  private val actors
  = Seq.tabulate(numServers)(n => RandomLoadActor("actor-" + (n + 1), loadMyu, loadSigma).start())

  // ロードバランサーの作成
  // 各アクターへのpolling間隔 = μ+10σ[s]
  // 収集するときのtimeout      = μ+2σ[s] (タイムアウト確率約3% (P(X in μ+_2σ)~95.4%))
  // 負荷収集時にタイムアウトが起きるとそのアクターへはメッセージをルートしないようになる
  private val selectiveRouter
  = minLoadSelectiveRouter(50 millis, (loadMyu + 10 * loadSigma), (loadMyu + 2 * loadSigma), actors).start()

  // ポート2552, サービス名routing:serviceで作成
  override def preStart() = {
    remote.start("localhost", 2552)
    remote.register("routing:service", self)
  }

  protected def receive = {
    case x => selectiveRouter forward x
  }
}

object MinLoadSelectiveRoutingServiceInspector {

  def main(args: Array[String]): Unit = {
    val server = Actor.remote.actorFor("routing:service", "localhost", 2552).start()
    import org.everpeace.akka.routing.RequestStoredLoad
    import org.everpeace.akka.routing.StoredLoad

    akka.actor.Scheduler.schedule(() => printStatus, 2000, 5000, TimeUnit.MILLISECONDS)
    def printStatus = {
      (server ? RequestStoredLoad).as[StoredLoad] match {
        case Some(msg) =>
          EventHandler.info(this, "=====")
          msg.loads.foreach(load => EventHandler.info(this, load))
          EventHandler.info(this, "=====")
        case None =>
          EventHandler.info(this, None)
      }
    }
  }

}

//テスト用：負荷数値列を与えてそれを順番に返すReporter
// 負荷返答にかかる時間はresponseTimeで指定してそれ未満のランダムな時間
trait LoadSequenceReporter extends LoadReporter {
  this: Actor =>
  val name: String
  val loadSeq: InfiniteIterator[Load]
  val responseTime: Duration

  protected def reportPresentLoad = {
    val load = loadSeq.next()
    //負荷返答に0~responseTime[ms]までのランダムな時間かかる想定
    Thread.sleep(scala.util.Random.nextLong() % responseTime.toMillis)
    EventHandler.info(this, "[%s(uuid=%s)] report Load=%f" format(name, self.uuid, load))
    Some(load)
  }
}

// テスト用：LoadSeqReporter用のextractor
object LoadSequenceReportActor {
  def apply(name: String, responseTime: Duration, loads: Load*)
  = Actor.actorOf(new LoadSequenceReportActor(name, responseTime, new CyclicIterator[Load](loads.toList)))
}

// テスト用：負荷数値列を負荷として返すようなアクタークラス
class LoadSequenceReportActor(val name: String, val responseTime: Duration, val loadSeq: InfiniteIterator[Load]) extends Actor with LoadSequenceReporter {
  def receive = requestLoad orElse forward

  def forward: Receive = {
    case x => {
      val retString = "%s called" format (name)
      EventHandler.info(this, retString)
      self.reply(retString)
    }
  }
}


// テスト用：正規分布に従うような負荷を返答するReporter
// AverageLoadReporterをmixinしてあって、実際には負荷履歴の平均を返す。
trait RandomLoadReporter extends AverageLoadReporter {
  this: Actor =>
  val name: String
  val responseTimeAverage: Duration
  val responseTimeStdDev: Duration

  protected def reportPresentLoad = {
    val load = (responseTimeStdDev.toMillis * nextGaussian() + responseTimeAverage.toMillis).toFloat.abs
    sleep
    EventHandler.info(this, "[%s(uuid=%s)] report Load=%f" format(name, self.uuid, load))
    Some(load)
  }

  //N(ave,stdDev^2)従う乱数[timeunit]スリープする
  protected def sleep = {
    val rand = (responseTimeStdDev.toMillis * nextGaussian() + responseTimeAverage.toMillis).toLong.abs
    if (rand > 0) Thread.sleep(rand)
  }
}

// テスト用：RandomLoadActor用のextractor
object RandomLoadActor {
  def apply(name: String, responseTimeAverage: Duration, responseTimeStdDev: Duration)
  = Actor.actorOf(new RandomLoadActor(name, responseTimeAverage, responseTimeStdDev))
}

// テスト用：正規分布に従う乱数を負荷の履歴の平均を負荷として報告するアクター
// 負荷リクエスト以外のメッセージが来た場合は、同じ正規分布に従う乱数の時間だけ待つ
class RandomLoadActor(val name: String, val responseTimeAverage: Duration, val responseTimeStdDev: Duration) extends Actor with RandomLoadReporter {
  protected lazy val historyLength = 1

  def receive = requestLoad orElse forward

  def forward: Receive = {
    case x => {
      val start = System.currentTimeMillis()
      val retString = "%s called" format (name)
      EventHandler.info(this, retString)
      sleep
      val end = System.currentTimeMillis()
      self.reply((retString, name.replace("actor-", "").toInt,end-start))
    }
  }
}
