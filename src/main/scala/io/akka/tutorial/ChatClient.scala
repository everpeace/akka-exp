package io.akka.tutorial

import akka.actor.Actor

/**
 *
 * User: Shingo Omura <everpeace _at_ gmail _dot_ com>
 * Date: 11/12/15
 */

class ChatClient(val name: String) {
  val server = Actor.remote.actorFor("chat:service", "localhost", 2552)

  def login = server ! Login(name)

  def logout = server ! Logout(name)

  def post(message: String) = server ! ChatMessage(name, name + ":" + message)

  def chatLog = (server ? GetChatLog(name)).as[ChatLog]
    .getOrElse(throw new Exception("Couldn't get the chat log from ChatServer"))


}