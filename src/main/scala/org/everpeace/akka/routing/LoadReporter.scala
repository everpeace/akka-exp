package org.everpeace.akka.routing

import akka.actor.Actor

/**
 *
 * User: Shingo Omura <everpeace _at_ gmail _dot_ com>
 * Date: 11/12/16
 */
// actor にこの LoadReporter or LoadReporter の sub trait を mix-in する。
trait LoadReporter {
  this: Actor =>
  protected def requestLoad: Receive = {
    case msg@RequestLoad() => this.self.reply(ReportLoad(reportLoad))
  }

  protected def reportLoad:Float
}

trait ConstantLoadReporter extends LoadReporter {
  this: Actor =>
  val load: Float

  protected def reportLoad = load
}

trait ThroughputAverageAsLoadReporter extends LoadReporter {
  this: Actor =>
  // TODO not yet implemented
  protected def reportLoad = 1
}