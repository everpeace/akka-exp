package io.akka.tutorial

import akka.actor.Actor

/**
 *
 * User: Shingo Omura <everpeace _at_ gmail _dot_ com>
 * Date: 11/12/15
 */

class ChatServiceStarter {
       def main()={
           Actor.actorOf[ChatService].start()
       }
}