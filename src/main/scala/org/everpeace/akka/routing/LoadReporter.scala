package org.everpeace.akka.routing

import akka.actor.Actor._

/**
 *
 * User: Shingo Omura <everpeace _at_ gmail _dot_ com>
 * Date: 11/12/16
 */
// actor にこの LoadReporter or LoadReporter の sub trait を mix-in する。
trait LoadReporter {
    protected def requestLoad: Receive = {
      case RequestLoad() => reportLoad
    }
     protected def reportLoad:ReportLoad
}

trait ConstantLoadReporter extends LoadReporter{
  val load:Float
  protected def  reportLoad = ReportLoad(load)
}

trait ThroughputAverageAsLoadReporter  extends LoadReporter{
  // TODO not yet implemented
  protected def reportLoad = ReportLoad(1)
}