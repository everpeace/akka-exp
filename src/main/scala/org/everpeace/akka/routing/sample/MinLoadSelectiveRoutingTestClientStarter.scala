package org.everpeace.akka.routing.sample

import akka.actor.Actor
import akka.event.EventHandler
import java.util.concurrent.TimeUnit

/**
 *
 * @author everpeace _at_ gmail _dot_ com
 * @date 11/12/18
 */

object MinLoadSelectiveRoutingTestClientStarter {
  def main(args: Array[String]) = run

  def run: Unit = {
    // 300個のクライアントをそれぞれ、500ms後に起動して500ms~1500s以下のランダムな間隔でRoutingServiceにメッセージを投げる
    Seq.tabulate(300) {
      n =>
        val client = new SampleClient("client-" + ((n + 1) toString))
        akka.actor.Scheduler.schedule(() => client call, 500, 1000+(scala.util.Random.nextInt(1000)-500), TimeUnit.MILLISECONDS)
    }
  }
}


// クライアント
// Serviceを立ち上げた状態でこれをconsoleからnewしてcallすると動きが確認出来る
case class SampleClient(name: String) {
  val server = Actor.remote.actorFor("routing:service", "localhost", 2552).start()

  def call = {
    val start = System.currentTimeMillis()
    (server ? 1).as[String] match {
      case Some(message) => {
        EventHandler.info(this, "[" + name + "] [response:" + message + "] (Turn Arround Time = " + (System.currentTimeMillis() - start) + "[msec])")
      }
      case None => {
        EventHandler.info(this, "[" + name + "] no response. (Turn Arround Time = " + (System.currentTimeMillis() - start) + "[msec])")
      }
    }
  }
}
