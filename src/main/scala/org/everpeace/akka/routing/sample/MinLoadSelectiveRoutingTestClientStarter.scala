package org.everpeace.akka.routing.sample

import akka.event.EventHandler
import java.util.concurrent.{ScheduledFuture, TimeUnit}
import akka.actor.{PoisonPill, Actor}


/**
 *
 * @author everpeace _at_ gmail _dot_ com
 * @date 11/12/18
 */

object MinLoadSelectiveRoutingTestClientStarter {
  def main(args: Array[String]) = run

  case object Start

  case object Stop

  def run: Unit = {
    val aggregateClient = Actor.actorOf(new Actor {
      var clientTasks: Seq[ScheduledFuture[AnyRef]] = Nil
      var num = 3
      var NUM = 100
      var clients = Seq.tabulate(num)(n => new SampleClient("client-" + ((n + 1) toString)))

      protected def receive = {
        // 100 個のクライアントをそれぞれ 、 500 ms後に起動して500ms ~1500ms以下のランダムな間隔で100回RoutingServiceにメッセージを投げる
        case Start =>
          clientTasks = for (client <- clients) yield akka.actor.Scheduler.schedule(new Runnable {
            var count: Int = 1

            def run = if (count <= NUM) {
              EventHandler.info(this, client.name + "'s " + count + "th call.")
              client.call
              count = count + 1
            } else {
              throw new java.lang.Error
            }
          }, 500, 1000 + (scala.util.Random.nextInt(1000) - 500), TimeUnit.MILLISECONDS)

        case Stop =>
          for (i <- 0 until num) {
            while (!clientTasks(i).isDone) {
              Thread.sleep(1000)
            }
          }
          for (i <- 0 until num) {
            val c = clients(i)
            val name = c.name
            val numRes = c.resTimes.length
            val resAve = if (numRes != 0) c.resTimes.sum.toFloat / numRes else 0.0f
            val resMax = if (numRes != 0) c.resTimes.max else -1L
            val resMin = if (numRes != 0) c.resTimes.min else -1L
            val numNoRes = c.noResTimes.length
            val noresAve = if (numNoRes != 0) c.noResTimes.sum.toFloat / numNoRes else 0.0f
            val noresMax = if (numNoRes != 0) c.noResTimes.max else -1L
            val noresMin = if (numNoRes != 0) c.noResTimes.min else -1L
            EventHandler.info(this, ("[%s]:totalCall=%d,  " +
              "averageResTime:%2.3f[ms](max=%d[ms], min=%d[ms], %d times),  " +
              "averageNoResTime:%2.3f[ms](max=%d[ms], min=%d[ms], %d times)")
              format(name, numRes + numNoRes, resAve, resMax, resMin, numRes, noresAve, noresMax, noresMin, numNoRes))
            c.server.stop
          }
          EventHandler.info(this, "all clients succesfully stopped.")
          System.exit(0)
      }
    }).start()
    aggregateClient ! Start
    aggregateClient ! Stop
  }
}


// クライアント
// Serviceを立ち上げた状態でこれをconsoleからnewしてcallすると動きが確認出来る
case class SampleClient(name: String) {
  val server = Actor.remote.actorFor("routing:service", "localhost", 2552).start()
  var resTimes: Seq[Long] = Nil
  var noResTimes: Seq[Long] = Nil

  def call = {
    val start = System.currentTimeMillis()
    (server ? 1).as[String] match {
      case Some(message) => {
        val time = (System.currentTimeMillis() - start)
        resTimes = time +: resTimes
        EventHandler.info(this, "[" + name + "] [response:" + message + "] (Turn Arround Time = " + time + "[msec])")
      }
      case None => {
        val time = (System.currentTimeMillis() - start)
        noResTimes = time +: noResTimes
        EventHandler.info(this, "[" + name + "] no response. (Turn Arround Time = " + time + "[msec])")
      }
    }
  }
}
