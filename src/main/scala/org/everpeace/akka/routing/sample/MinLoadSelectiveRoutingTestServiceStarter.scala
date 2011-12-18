package org.everpeace.akka.routing.sample

import akka.actor._
import akka.actor.Actor._
import org.everpeace.akka.routing._
import akka.event.EventHandler
import akka.routing.{CyclicIterator, InfiniteIterator}
import sun.awt.windows.ThemeReader

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
  private val actors = Seq.tabulate(3) {
    n => RandomLoadActor("actor-" + ((n + 1) toString), 0, 100) start
  }

  private val selectiveRouter
  = minLoadSelectiveRouter(actors).start()

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
    EventHandler.info(this, "[name=%s uuid=%s] report Load=%f" format(name, self.uuid, load))
    load
  }
}

trait RandomLoadReporter extends LoadReporter {
  this: Actor =>
  val minLoad: Int
  val maxLoad: Int
  val name: String

  protected def reportLoad = {
    val load = (minLoad + scala.util.Random.nextInt(maxLoad - minLoad)).toFloat
    EventHandler.info(this, "%s report Load=%f" format(name, load))
    Thread.sleep(1000)
    load
  }
}

// sample用のサービスActorのActorRef用のextractor
object LoadSeqActor {
  def apply(name: String, loads: Load*) = Actor.actorOf(new LoadSeqActor(name, new CyclicIterator[Load](loads.toList)))
}

// sample用アクターサービス
class LoadSeqActor(val name: String, val loadSeq: InfiniteIterator[Load]) extends Actor with LoadSequenceReporter {
  def receive = requestLoad orElse forward

  def forward: Receive = {
    case x => {
      val retString = "%s called" format (name)
      EventHandler.info(this, retString)
      self.reply(retString)
    }
  }
}

object RandomLoadActor {
  def apply(name: String, min: Int, max: Int) = Actor.actorOf(new RandomLoadActor(name, min, max))
}

class RandomLoadActor(val name: String, val minLoad: Int, val maxLoad: Int) extends Actor with RandomLoadReporter {
  def receive = requestLoad orElse forward

  def forward: Receive = {
    case x => {
      val retString = "%s called" format (name)
      EventHandler.info(this, retString)
      self.reply(retString)
    }
  }
}
