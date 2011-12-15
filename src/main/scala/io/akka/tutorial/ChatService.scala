package io.akka.tutorial

import akka.actor.Actor._

/**
 *
 * User: Shingo Omura <everpeace _at_ gmail _dot_ com>
 * Date: 11/12/15
 */

class ChatService extends ChatServer
  with SessionManagement
  with ChatManagement
  with MemoryChatStorageFactory {

  override def preStart() = {
    remote.start("localhost", 2552)
    remote.register("chat:service", self)
  }
}