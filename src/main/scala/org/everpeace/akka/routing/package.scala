package org.everpeace.akka

import akka.actor.{Actor, ActorRef}
import scala.collection.JavaConversions._
import java.util.concurrent.TimeUnit

/**
 *
 * @author everpeace _at_ gmail _dot_ com
 * @date 11/12/15
 */

package object routing {
  type Load = Float

  // MinLoadSelectiveRouterのコンストラクタユーティリティ
  def minLoadSelectiveRouter(as: java.util.List[ActorRef]): ActorRef =
    minLoadSelectiveRouter(as toList)


  // MinLoadSelectiveRouterのコンストラクタユーティリティ
  def minLoadSelectiveRouter(as: Seq[ActorRef]): ActorRef
  = Actor.actorOf(new Actor with SelectiveRouter with MinLoadSelector with PollingCollector {
    val initialDelay = 3L
    val betweenPollingDelay = 3L
    val delayTimeUnit = TimeUnit.SECONDS
    val actors = as

    override def preStart() = {
      startPolling
    }
  })

}