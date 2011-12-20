package org.everpeace.akka.routing.sample

import akka.event.EventHandler
import java.util.concurrent.TimeUnit
import akka.actor.Actor
import akka.util.duration._

/**
 *
 * @author everpeace _at_ gmail _dot_ com
 * @date 11/12/18
 */

object MinLoadSelectiveRoutingTestClientStarter {
  def main(args: Array[String]) = run

  def run: Unit = {
    // numClientsクライアントが各numCalls回ロードバランサをコールする
    val numClients = 5
    val numCalls = 10
    // コールする間隔を返す関数を用意してやる
    def initialDelay = scala.util.Random.nextInt(100) millis
    def betweenDelay = 1000 millis

    val clients = Seq.tabulate(numClients)(n => new SampleClient("client-" + (n + 1) ))
    // clientを順次起動
    val clientTasks = for (client <- clients) yield akka.actor.Scheduler.schedule(new Runnable {
      var count: Int = 1

      def run = if (count <= numCalls) {
        EventHandler.info(this, client.name + "'s " + count + "th call.")
        client.call
        count = count + 1
      } else {
        throw new java.lang.Error
      }
    }, initialDelay.toMillis, betweenDelay.toMillis, TimeUnit.MILLISECONDS)

    //終わってたら結果を表示して終了。
    akka.actor.Scheduler.schedule(() => if (clientTasks.forall(_ isDone)) reportResult, 1, 1, TimeUnit.SECONDS)

    def reportResult = {
      for (i <- 0 until numClients) {
        val c = clients(i)
        val name = c.name
        val numRes = c.resTimes.length
        val resAve = if (numRes != 0) c.resTimes.sum.toFloat / numRes else -1.0f
        val resStdDev = if (numRes != 0) scala.math.sqrt(c.resTimes.map(t => (t - resAve) * (t - resAve)).sum.toDouble / numRes) else -1.0f
        val resMax = if (numRes != 0) c.resTimes.max else -1L
        val resMin = if (numRes != 0) c.resTimes.min else -1L
        val numNoRes = c.noResTimes.length
        val noresAve = if (numNoRes != 0) c.noResTimes.sum.toFloat / numNoRes else -1.0f
        val noresStdDev = if (numNoRes != 0) scala.math.sqrt(c.noResTimes.map(t => (t - noresAve) * (t - noresAve)).sum.toDouble / numNoRes) else -1.0f
        val noresMax = if (numNoRes != 0) c.noResTimes.max else -1L
        val noresMin = if (numNoRes != 0) c.noResTimes.min else -1L
        EventHandler.info(this, ("[%s]:totalCall=%d,  " +
          "ResTime(ave,stddev):(%2.3f[ms],%2.3f[ms])  (max=%d[ms], min=%d[ms], %d times),  " +
          "NoResTime(ave,stddev):(%2.3f[ms],%2.3f[ms])  (max=%d[ms], min=%d[ms], %d times)")
          format(name, numRes + numNoRes, resAve, resStdDev, resMax, resMin, numRes, noresAve, noresStdDev, noresMax, noresMin, numNoRes))
      }
      val calledServerIds = ((Seq.empty: Seq[Int]) /: clients)(_ ++ _.calledServerIds)
      val serverIdRanking = calledServerIds.map((_, 1)).groupBy(_._1).mapValues(_.map(_._2).size).toSeq.sortWith(_._2 > _._2)
      EventHandler.info(this, "called serverId Ranking:" + serverIdRanking)
      EventHandler.info(this, "all clients succesfully stopped.")
      System.exit(0)
    }
  }
}


// クライアント
// Serviceを立ち上げた状態でこれをconsoleからnewしてcallすると動きが確認出来る
case class SampleClient(name: String) {
  val server = Actor.remote.actorFor("routing:service", "158.201.101.10", 2552).start()
  var resTimes: Seq[Long] = Nil
  var noResTimes: Seq[Long] = Nil
  var calledServerIds: Seq[Int] = Nil

  def call = {
    val start = System.currentTimeMillis()
    (server ? 1).as[(String, Int)] match {
      case Some(message) => {
        val time = (System.currentTimeMillis() - start)
        resTimes = time +: resTimes
        calledServerIds = message._2 +: calledServerIds
        EventHandler.info(this, "[" + name + "] [response:" + message._1 + "] (Turn Arround Time = " + time + "[msec])")
      }
      case None => {
        val time = (System.currentTimeMillis() - start)
        noResTimes = time +: noResTimes
        EventHandler.info(this, "[" + name + "] no response. (Turn Arround Time = " + time + "[msec])")
      }
    }
  }
}
