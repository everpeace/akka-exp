package org.everpeace.akka.routing

import akka.routing.Dispatcher
import akka.actor.{ActorRef, Actor}
import akka.actor.ForwardableChannel._
import akka.actor.UntypedChannel._

/**
 *
 * User: Shingo Omura <everpeace _at_ gmail _dot_ com>
 * Date: 11/12/16
 */

trait SelectiveRouter extends Dispatcher {
  self: Actor =>
  protected val actors: Seq[ActorRef]

  protected def routes = {
    case x => select(x)
  }

  protected def select: Any => ActorRef

  override def broadcast(message: Any) =
    actors.foreach {
      a => if (isSenderDefined) a.forward(message)(someSelf)
      else a.!(message)(None)
    }

  override def isDefinedAt(msg: Any) = actors.exists(_.isDefinedAt(msg))
}