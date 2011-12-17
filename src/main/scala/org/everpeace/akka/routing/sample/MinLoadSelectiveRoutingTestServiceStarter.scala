package org.everpeace.akka.routing.sample

import akka.actor._
import akka.actor.Actor._
import org.everpeace.akka.routing._
import akka.event.EventHandler
import akka.routing.{CyclicIterator, InfiniteIterator}

/**
 *
 * User: Shingo Omura <everpeace _at_ gmail _dot_ com>
 * Date: 11/12/16
 */

object MinLoadSelectiveRoutingTestServiceStarter {
  def main(args: Array[String]) = run

  def run: Unit = {
    actorOf[MinLoadSelectiveRoutingService].start()
  }
}

// test 用のリモートサービス MinLoadSelectiveRouterを作ってリモートサービスに登録する
class MinLoadSelectiveRoutingService extends Actor {
  private val a = SampleActor("a", 3, 2, 1).start()
  private val b = SampleActor("b", 4, 3, 2, 1).start()
  private val c = SampleActor("c", 5, 4, 3, 2, 1).start()
  private val selectiveRouter
  = minLoadSelectiveRouter(a :: b :: c :: Nil).start()

  protected def receive = {
    case x => selectiveRouter forward x
  }

  override def preStart() = {
    remote.start("localhost", 2552)
    remote.register("routing:service", self)
  }
}

//テスト用にloadの数値列を与えてそれを順番に返すReporter
trait LoadSequenceReporter extends LoadReporter {
  this: Actor =>
  val name: String
  val loadSeq: InfiniteIterator[Load]

  protected def reportLoad = {
    val load = loadSeq.next()
    EventHandler.info(this, "%s report Load %f" format(name, load))
    load
  }
}

// sample用のサービスActorのActorRef用のextractor
object SampleActor {
  def apply(name: String, loads: Load*) = Actor.actorOf(new SampleActor(name, new CyclicIterator[Load](loads.toList)))
}

// sample用アクターサービス
class SampleActor(val name: String, val loadSeq: InfiniteIterator[Load]) extends Actor with LoadSequenceReporter {
  def receive = requestLoad orElse forward

  def forward: Receive = {
    case x => EventHandler.info(this, "%s called" format name)
  }
}

// クライアント
// Serviceを立ち上げた状態でこれをconsoleからnewしてcallすると動きが確認出来る
class SampleClient {
  val server = Actor.remote.actorFor("routing:service", "localhost", 2552).start()

  def call = server ! 1

  def stop = server stop
}
