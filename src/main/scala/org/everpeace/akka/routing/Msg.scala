package org.everpeace.akka.routing

/**
 *
 * @author everpeace _at_ gmail _dot_ com
 * @date 11/12/15
 */

sealed trait Msg

case class ReportLoad(load: Load) extends Msg
case object RequestLoad extends Msg