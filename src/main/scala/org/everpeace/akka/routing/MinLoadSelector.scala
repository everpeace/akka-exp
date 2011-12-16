package org.everpeace.akka.routing

import akka.actor.ActorRef
import akka.stm.TransactionalMap

/**
 *
 * User: Shingo Omura <everpeace _at_ gmail _dot_ com>
 * Date: 11/12/16
 */

trait MinLoadSelector[T <: ActorRef] {
  val actors:Seq[ActorRef]
  private val loads = TransactionalMap[T,Float]()

  def select: ActorRef

  def update ={

  }

}