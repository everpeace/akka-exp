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
    case msg@RequestLoad() => this.self.reply(ReportLoad(convert(reportLoad)))
  }

  protected def convert(reported: Float): Float = reported

  // override by yourself!
  protected def reportLoad: Float
}

// k 回分のloadの平均値をloadとして返すようなLoadReporter
trait ThroughputAverageAsLoadReporter extends LoadReporter {
  this: Actor =>
  val range: Int
  // TODO implement this!
  protected def convert = reportLoad
}