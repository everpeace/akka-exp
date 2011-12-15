package org.everpeace.akka.routing

/**
 * 
 * @author everpeace _at_ gmail _dot_ com
 * @date 11/12/15
 */

sealed trait Event
case class ReportLoad(load:Float)