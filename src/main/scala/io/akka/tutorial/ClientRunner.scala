package io.akka.tutorial

/**
 *
 * User: Shingo Omura <everpeace _at_ gmail _dot_ com>
 * Date: 11/12/15
 */

object ClientRunner {
  def main(args: Array[String]) = run

  def run = {

//    val chatService = Actor.actorOf[ChatService].start()

    val client1 = new ChatClient("jonas")
    client1.login
    val client2 = new ChatClient("patrik")
    client2.login

    client1.post("Hi there")
    println("CHAT LOG:\n\t"+ client1.chatLog.log.mkString ("\n\t"))

    client2.post("Hello")
    println("CHAT LOG:\n\t" + client2.chatLog.log.mkString("\n\t"))

    client1.post("Hi again")
    println("CHAT LOG:\n\t" + client1.chatLog.log.mkString("\n\t"))

    client1.logout
    client2.logout
//    chatService.stop()
  }
}