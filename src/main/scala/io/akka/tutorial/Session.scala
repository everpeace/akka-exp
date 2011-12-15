package io.akka.tutorial

import akka.actor.{Actor, ActorRef}


/**
 *
 * User: Shingo Omura <everpeace _at_ gmail _dot_ com>
 * Date: 11/12/15
 */

class Session(user: String, storage: ActorRef) extends Actor {

  private val loginTime = System.currentTimeMillis
  private var userLog: List[String] = Nil

  def receive = {
    case msg @ ChatMessage(from, message) =>
      userLog ::= message
      storage ! msg

    case msg @ GetChatLog(_) =>
      storage forward msg
  }
}