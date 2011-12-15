package io.akka.tutorial

import akka.actor.{ActorRef, Actor}
import collection.mutable.HashMap
import akka.event.EventHandler


/**
 *
 * User: Shingo Omura <everpeace _at_ gmail _dot_ com>
 * Date: 11/12/15
 */

trait SessionManagement {
  this: Actor =>
  val storage: ActorRef
  val sessions = new HashMap[String, ActorRef]

  protected def sessionManagement: Receive = {
    case Login(username) =>
      EventHandler.info(this, "User [%s] has logged in" format username)
      val session = Actor.actorOf(new Session(username, storage))
      session.start()
      sessions += (username -> session)

    case Logout(username) =>
      EventHandler.info(this, "User [%s] has logged out" format username)
      val session = sessions(username)
      session.stop()
      sessions -= username
  }

  protected def shutdownSessions = sessions.foreach {
    case (_, session) => session.stop()
  }
}