package io.akka.tutorial

import akka.config.Supervision.OneForOneStrategy
import akka.actor.{ActorRef, Actor}
import akka.event.EventHandler

/**
 *
 * User: Shingo Omura <everpeace _at_ gmail _dot_ com>
 * Date: 11/12/15
 */

trait ChatServer extends Actor {
  self.faultHandler = OneForOneStrategy(List(classOf[Exception]), 5, 5000)
  val storage: ActorRef

  EventHandler.info(this, "ChatServer is starting up...")

  def receive: Receive = sessionManagement orElse chatManagement

  protected def chatManagement: Receive

  protected def sessionManagement: Receive

  protected def shutdownSessions(): Unit

  override def postStop() {
    EventHandler.info(this, "Chat server is shutting down...")
    shutdownSessions
    self.unlink(storage)
    storage.stop()
  }
}