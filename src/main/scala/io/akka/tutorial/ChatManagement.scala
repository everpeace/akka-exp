package io.akka.tutorial

import akka.actor.{ActorRef, Actor}
import collection.mutable.HashMap
import akka.event.EventHandler


/**
 *
 * User: Shingo Omura <everpeace _at_ gmail _dot_ com>
 * Date: 11/12/15
 */

trait ChatManagement { this: Actor =>
  val sessions:HashMap[String,ActorRef]

  protected def chatManagement: Receive ={
    case msg @ ChatMessage(from, _)  => getSession(from).foreach(_ ! msg)
    case msg @ GetChatLog(from) => getSession(from).foreach(_ forward msg)
  }

  private def getSession(from : String): Option[ActorRef] ={
    if(sessions contains from){
       Some(sessions(from))
    }else{
      EventHandler.info(this, "Session expired for %s" format from)
      None
    }
  }
}