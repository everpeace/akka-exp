package org.everpeace.akka.routing

import akka.actor.ActorRef
import akka.stm.TransactionalMap
import akka.stm.atomic

/**
 *
 * User: Shingo Omura <everpeace _at_ gmail _dot_ com>
 * Date: 11/12/16
 */

trait Selector {
  def select: Any => ActorRef
}

trait MinLoadSelector extends Selector {
  protected val actors: Seq[ActorRef]
  private[this] val loads = TransactionalMap[ActorRef, Float]()

  override def select = _ => {
    update
    atomic {
      loads.reduce[(ActorRef, Float)]((min, candidate)
      => if (min._2 > candidate._2) candidate
        else min
      )._1
    }
  }

  private def update = actors.foreach {
    a => atomic {
      (a ? RequestLoad()).as[ReportLoad] match {
        case Some(ReportLoad(load)) => loads +=(a, load)
      }
    }
  }

}