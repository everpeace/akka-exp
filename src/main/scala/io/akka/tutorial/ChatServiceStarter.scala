package io.akka.tutorial

import akka.actor.Actor._

/**
 *
 * User: Shingo Omura <everpeace _at_ gmail _dot_ com>
 * Date: 11/12/15
 */

object ChatServiceStarter {
  def main(args: Array[String]) = run

  def run:Unit = {
    actorOf[ChatService].start()
  }
}