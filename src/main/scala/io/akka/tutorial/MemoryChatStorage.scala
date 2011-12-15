package io.akka.tutorial

import akka.config.Supervision.Permanent
import akka.stm.TransactionalVector
import akka.event.EventHandler
import akka.stm.atomic
/**
 *
 * User: Shingo Omura <everpeace _at_ gmail _dot_ com>
 * Date: 11/12/15
 */

class MemoryChatStorage extends ChatStorage {
  self.lifeCycle = Permanent

  private var chatLog = TransactionalVector[Array[Byte]]()
  EventHandler.info(this,"Memory-based chat storage is starting up...")

  def receive = {
    case msg @ ChatMessage(from, message) =>
      EventHandler.debug(this, "New chat message [%s]" format message)
     atomic{ chatLog + message.getBytes("UTF-8") }

    case GetChatLog(_) =>
      val messageList = atomic{ chatLog.map(bytes => new String(bytes,"UTF-8")).toList }
      self.reply(ChatLog(messageList))
  }

  override def postRestart(reason: Throwable) = (chatLog = TransactionalVector())
}