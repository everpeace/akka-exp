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
    case msg @ RequestLoad => this.self.reply(ReportLoad(convert(reportLoad)))
  }
  // strategy for what value is reported
  protected def convert(reported: Load): Load = reported

  // report present load
  // override it yourself!
  protected def reportLoad: Load
}

// k 回分のloadの平均値をloadとして返すようなLoadReporter
trait ThroughputAverageAsLoadReporter extends LoadReporter {
  this: Actor =>
  val range: Int
  // TODO implement this!
  protected def convert = reportLoad
}