package org.everpeace.akka

import akka.actor.{Actor, ActorRef}
import scala.collection.JavaConversions._

/**
 *
 * @author everpeace _at_ gmail _dot_ com
 * @date 11/12/15
 */

package object routing {
  type Load = Float

  // MinLoadSelectiveRouterのコンストラクタユーティリティ
  def minLoadSelectiveRouter(as: java.util.List[ActorRef]): ActorRef =
    Actor.actorOf(new Actor with SelectiveRouter with MinLoadSelector with OnDemandCollector {
      protected val actors = as toList
    })

  // MinLoadSelectiveRouterのコンストラクタユーティリティ
  def minLoadSelectiveRouter(as: Seq[ActorRef]): ActorRef =
    Actor.actorOf(new Actor with SelectiveRouter with MinLoadSelector with OnDemandCollector {
      protected val actors = as
    })
}