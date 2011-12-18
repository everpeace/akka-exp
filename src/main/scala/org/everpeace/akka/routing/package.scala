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
    lazy val initialDelay = 1L
    lazy val betweenPollingDelay = 3L
    lazy val delayTimeUnit = TimeUnit.SECONDS
    lazy val actors = as
  })

}