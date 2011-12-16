package org.everpeace.akka.routing.sample

import akka.actor.Actor
import akka.event.EventHandler
import akka.routing.{CyclicIterator, InfiniteIterator}
import org.everpeace.akka.routing.LoadReporter

/**
 *
 * User: Shingo Omura <everpeace _at_ gmail _dot_ com>
 * Date: 11/12/16
 */
class SampleActor(val name: String, val loadSeq: InfiniteIterator[Float]) extends Actor with LoadSequenceReporter {
  protected def receive = requestLoad orElse {
    case x => EventHandler.info(this, "%s called" format name)
  }
}

object SampleActor {
  def apply(name: String, loads: Float*) = Actor.actorOf(new SampleActor(name, new CyclicIterator[Float](loads.toList)))
}

trait LoadSequenceReporter extends LoadReporter {
  this: Actor =>
  val loadSeq: InfiniteIterator[Float]

  protected def reportLoad = {
    val load = loadSeq.next()
    EventHandler.info(this, "report Load %f" format load)
    load
  }
}