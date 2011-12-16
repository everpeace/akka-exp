package org.everpeace.akka.routing.sample

import akka.actor.Actor

/**
 *
 * User: Shingo Omura <everpeace _at_ gmail _dot_ com>
 * Date: 11/12/16
 */

class SampleClient {
  val server = Actor.remote.actorFor("routing:service", "localhost", 2552).start()

  def post = server ! 1

  def stop = server stop
}