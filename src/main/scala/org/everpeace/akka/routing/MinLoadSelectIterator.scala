package org.everpeace.akka.routing

import akka.routing.InfiniteIterator
import akka.actor.{Actor, ActorRef}

/**
 *
 * @author everpeace _at_ gmail _dot_ com
 * @date 11/12/15
 */

case class MinLoadSelectIterator[T]() extends InfiniteIterator[T] with Actor {
  protected def receive = {
    case msg@ReportLoad(d) =>
  }

}