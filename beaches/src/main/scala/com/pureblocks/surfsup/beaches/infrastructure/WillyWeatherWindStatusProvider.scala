package com.pureblocks.surfsup.beaches.infrastructure

import com.pureblocks.surfsup.beaches.core.{BeachId, BeachNotFound, WindStatusProvider}
import com.pureblocks.surfsup.core.{TIO, TechnicalError}
import sttp.client3.*
import sttp.client3.httpclient.zio.*
import zio.*
import zio.{IO, *}
import zio.json.*

case class WillyWeatherWindStatusProvider(sttpBackend: SttpBackend[Task, Any], apiKey: String) extends WindStatusProvider :
  def get(beachId: BeachId): TIO[BeachNotFound, com.pureblocks.surfsup.beaches.core.Wind] =
    val request = basicRequest.response(asStringAlways)
      .get(uri"https://api.willyweather.com.au/v2/$apiKey/locations/${beachId.id}/weather.json?observational=true")
    sttpBackend.send(request)
      .flatMap(r => parse(r.body))
      .map(windsResponse => {
        val wind = windsResponse.observational.observations.wind
        com.pureblocks.surfsup.beaches.core.Wind(
          wind.direction,
          wind.speed,
          wind.directionText,
          "" + wind.trend
        )
      })

  private def parse(response: String): TIO[BeachNotFound, WindsResponse] =
    val windsResponse = response.fromJson[WindsResponse]
    ZIO.fromEither(windsResponse).mapError(_ => extractError(response))

  def extractError(json: String): TechnicalError | BeachNotFound =
    val either: Either[String, TechnicalError | BeachNotFound] = json.fromJson[ErrorResponse]
      .map(errorResponse => {
        errorResponse.error.code match {
          case "model-not-found" => BeachNotFound("Beach not found")
          case _ => InvalidResponseFromWW(json)
        }
      })
    either.getOrElse(InvalidResponseFromWW(json))

object WillyWeatherWindStatusProvider {
  val live: ZLayer[SttpBackend[Task, Any] & String, Any, WillyWeatherWindStatusProvider] = ZLayer.fromFunction((a, b) => WillyWeatherWindStatusProvider(a, b))
}


case class Wind(
                 speed: Double,
                 gustSpeed: Double,
                 trend: Double,
                 direction: Double,
                 directionText: String
               )

object Wind {
  implicit val decoder: JsonDecoder[Wind] = DeriveJsonDecoder.gen[Wind]
}

case class Observations(wind: Wind)

object Observations {
  implicit val decoder: JsonDecoder[Observations] =
    DeriveJsonDecoder.gen[Observations]
}

case class Observational(observations: Observations)

object Observational {
  implicit val decoder: JsonDecoder[Observational] =
    DeriveJsonDecoder.gen[Observational]
}

case class WindsResponse(observational: Observational)

object WindsResponse {
  implicit val decoder: JsonDecoder[WindsResponse] =
    DeriveJsonDecoder.gen[WindsResponse]
}

case class Error(code: String)

object Error {
  implicit val decoder: JsonDecoder[Error] = DeriveJsonDecoder.gen[Error]
}

case class ErrorResponse(error: Error)

object ErrorResponse {
  implicit val decoder: JsonDecoder[ErrorResponse] =
    DeriveJsonDecoder.gen[ErrorResponse]
}

case class InvalidResponseFromWW(response: String) extends TechnicalError