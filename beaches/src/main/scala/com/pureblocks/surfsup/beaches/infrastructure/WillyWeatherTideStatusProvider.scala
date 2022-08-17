package com.pureblocks.surfsup.beaches.infrastructure

import com.pureblocks.surfsup.beaches.core.{BeachId, BeachNotFound, TideHeight, TidesStatusProvider}
import com.pureblocks.surfsup.core.{TIO, TechnicalError}
import sttp.client3.*
import sttp.client3.httpclient.zio.*
import zio.json.*
import zio.*

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}
import scala.concurrent.Future

case class WillyWeatherTideStatusProvider(
    sttpBackend: SttpBackend[Task, Any],
    apiKey: String,
    config: Map[Long, com.pureblocks.surfsup.beaches.config.Beach],
    clock: Clock
) extends TidesStatusProvider:
  val startDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  val timeZoneForRegion = Map(
    "TAS" -> "Australia/Tasmania",
    "WA" -> "Australia/Perth",
    "VIC" -> "Australia/Victoria",
    "QLD" -> "Australia/Queensland",
    "SA" -> "Australia/Adelaide",
    "ACT" -> "Australia/ACT",
    "NSW" -> "Australia/NSW",
    "NT" -> "Australia/Darwin"
  )

  def get(
      beachId: BeachId
  ): TIO[BeachNotFound, TideHeight] =
    if (!config.contains(beachId.id)) {
      ZIO.fail(BeachNotFound(s"Beach not found $beachId"))
    } else {
      val tz =
        timeZoneForRegion.getOrElse(config(beachId.id).region, "Australia/NSW")
      val tzId = ZoneId.of(tz)
      val startDateFormatted =
        startDateFormat.format(ZonedDateTime.now(tzId).minusDays(1))

      val currentTimeGmt =
        (java.lang.System.currentTimeMillis() / 1000) + ZonedDateTime
          .now(tzId)
          .getOffset
          .getTotalSeconds
      sendRequestAndParseResponse(beachId, startDateFormatted, tzId, currentTimeGmt)

    }

  def sendRequestAndParseResponse(
      beachId: BeachId,
      startDateFormatted: String,
      tzId: ZoneId,
      currentTimeGmt: Long
  ) = {

    val request = basicRequest.response(asStringAlways)
      .get(uri"https://api.willyweather.com.au/v2/$apiKey/locations/${beachId.id}/weather.json?forecastGraphs=tides&days=3&startDate=$startDateFormatted")

    sttpBackend
      .send(request)
      .flatMap(r => parse(r.body))
      .map(tidesResponse => {
        val datums = tidesResponse.forecastGraphs.tides.dataConfig.series.groups.flatMap(_.points).sortBy(_.x)
        interpolate(tzId, currentTimeGmt, datums)
      })

  }

  private def parse(response: String): TIO[BeachNotFound, TidesResponse] =
    val tidesResponse = response.fromJson[TidesResponse]
    ZIO.fromEither(tidesResponse).mapError(_ => extractError(response))

  def extractError(json: String): TechnicalError | BeachNotFound =
    val either: Either[String, TechnicalError | BeachNotFound] = json.fromJson[ErrorResponse]
      .map(errorResponse => {
        errorResponse.error.code match {
          case "model-not-found" => BeachNotFound("Beach not found")
          case _ => InvalidResponseFromWW(json)
        }
      })
    either.getOrElse(InvalidResponseFromWW(json))


  private def interpolate(
      zoneId: ZoneId,
      currentTimeGmt: Long,
      sorted: Seq[Datum]
  ) = {
    try {
      val before = sorted.filterNot(s => s.x > currentTimeGmt)
      val after = sorted.filter(s => s.x > currentTimeGmt)

      val interpolated = before.last.interpolateWith(currentTimeGmt, after.head)
      val nextHigh_ = after.filter(_.description == "high").head
      val nextHigh = nextHigh_.copy(x =
        nextHigh_.x - ZonedDateTime.now(zoneId).getOffset.getTotalSeconds
      )
      val nextLow_ = after.filter(_.description == "low").head
      val nextLow = nextLow_.copy(x =
        nextLow_.x - ZonedDateTime.now(zoneId).getOffset.getTotalSeconds
      )

      val status = if (nextLow.x < nextHigh.x) "Decreasing" else "Increasing"

      TideHeight(interpolated.y, status, nextLow.x, nextHigh.x)
    } catch {
      case e: Exception => {
        TideHeight(Double.NaN, "NA", Long.MinValue, Long.MinValue)
      }
    }
  }

object WillyWeatherTideStatusProvider {
  val live: ZLayer[
    SttpBackend[Task, Any] & String & Map[Long, com.pureblocks.surfsup.beaches.config.Beach] & Clock,
    Any,
    WillyWeatherTideStatusProvider
  ] = ZLayer.fromFunction((a, b, c , d) => WillyWeatherTideStatusProvider(a, b, c , d))
}


case class TidesResponse(forecastGraphs:ForecastGraphs)
object TidesResponse {
  implicit val decoder: JsonDecoder[TidesResponse] = DeriveJsonDecoder.gen[TidesResponse]
}
case class ForecastGraphs(tides:Tides)
object ForecastGraphs {
  implicit val decoder: JsonDecoder[ForecastGraphs] = DeriveJsonDecoder.gen[ForecastGraphs]
}
case class Tides(dataConfig:DataConfig)
object Tides {
  implicit val decoder: JsonDecoder[Tides] = DeriveJsonDecoder.gen[Tides]
}
case class DataConfig(series:Series)
object DataConfig {
  implicit val decoder: JsonDecoder[DataConfig] = DeriveJsonDecoder.gen[DataConfig]
}
case class Series(groups:Seq[Groups])
object Series {
  implicit val decoder: JsonDecoder[Series] = DeriveJsonDecoder.gen[Series]
}
case class Groups(points:Seq[Datum])
object Groups {
  implicit val decoder: JsonDecoder[Groups] = DeriveJsonDecoder.gen[Groups]
}
case class Datum(
                  x: Long,
                  y: Double,
                  description: String,
                  interpolated: Boolean
                ) {
  def interpolateWith(newX: Long, other: Datum) =
    Datum(newX, BigDecimal((other.y - y) / (other.x - x) * (newX - x) + y).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble, "", true)
}
object Datum {
  implicit val decoder: JsonDecoder[Datum] = DeriveJsonDecoder.gen[Datum]
}

case class Tide(
                 dateTime: String,
                 height: Double,
                 `type`: String
               )