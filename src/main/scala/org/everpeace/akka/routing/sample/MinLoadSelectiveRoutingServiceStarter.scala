package org.everpeace.akka.routing.sample

import akka.actor._
import akka.actor.Actor._
import org.everpeace.akka.routing._

/**
 *
 * User: Shingo Omura <everpeace _at_ gmail _dot_ com>
 * Date: 11/12/16
 */

object MinLoadSelectiveRoutingServiceStarter {
  def main(args: Array[String]) = run

  def run: Unit = {
    actorOf[MinLoadSelectiveService].start()
  }
}

class MinLoadSelectiveService extends Actor {
  private[this] val selectiveRouter
  = minLoadSelectiveRouter(SampleActor("a", 1, 2).start() :: SampleActor("b", 2, 1).start() :: Nil).start()

  protected def receive = {
    case x => selectiveRouter forward x
  }

  override def preStart() = {
    remote.start("localhost", 2552)
    remote.register("routing:service", self)
  }
}