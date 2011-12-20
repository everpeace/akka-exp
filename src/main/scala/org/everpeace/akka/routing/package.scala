package org.everpeace.akka

import akka.actor.{Actor, ActorRef}
import scala.collection.JavaConversions._
import java.util.concurrent.TimeUnit
import akka.util.Duration

/**
 *
 * @author everpeace _at_ gmail _dot_ com
 * @date 11/12/15
 */

package object routing {
  type Load = Float

  // MinLoadSelectiveRouterのコンストラクタユーティリティ
  def minLoadSelectiveRouter(initDelay: Duration, pollingDelay: Duration, loadRequestTimeout: Duration, as: java.util.List[ActorRef]): ActorRef =
    minLoadSelectiveRouter(initDelay, pollingDelay, loadRequestTimeout, as toList)


  // MinLoadSelectiveRouterのコンストラクタユーティリティ
  def minLoadSelectiveRouter(initDelay: Duration, pollingDelay: Duration, _loadRequestTimeout: Duration, as: Seq[ActorRef]): ActorRef
  = Actor.actorOf(new Actor with SelectiveRouter with RandomTopNSelector with PollingCollector {
    lazy val initialDelay = initDelay.toMillis
    lazy val betweenPollingDelay = pollingDelay.toMillis
    lazy val delayTimeUnit = TimeUnit.MILLISECONDS
    lazy val loadRequestTimeout = _loadRequestTimeout
    lazy val N = 3
    lazy val actors = as

    override def receive = reportStoredLoad orElse dispatch
  })

}