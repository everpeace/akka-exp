package org.everpeace.akka.routing

/**
 *
 * @author everpeace _at_ gmail _dot_ com
 * @date 11/12/15
 */

sealed trait Msg

// サービス提供アクターがリクエストルータからの負荷リクエストに答えるためのメッセージ
case class ReportLoad(load: Option[Load]) extends Msg

// リクエストルータがサービス提供アクターにたいして行う負荷リクエストメッセージ
case object RequestLoad extends Msg