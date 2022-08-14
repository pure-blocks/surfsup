package com.pureblocks.surfsup.beaches.core

import com.pureblocks.surfsup.core.TIO
import zio.{IO, ZIO, ZLayer}

trait WindStatusService:
  def get(beachId: BeachId): TIO[BeachNotFound, Wind]

trait TidesStatusService:
  def get(beachId: BeachId): TIO[BeachNotFound, TideHeight]

trait SwellStatusService:
  def get(beachId: BeachId): TIO[BeachNotFound, Swell]

class BeachStatusService(
                      windService: WindStatusService,
                      tidesService: TidesStatusService,
                      swellsService: SwellStatusService
                    ):

  def getStatus(beachId: BeachId): ZIO[Any, Throwable | BeachNotFound, Beach] =
    (windService.get(beachId) <&> tidesService.get(
      beachId
    ) <&> swellsService.get(beachId)).map(s =>
      Beach(
        beachId,
        s._1,
        Tide(s._2, SwellOutput(s._3.height, s._3.direction, s._3.directionText))
      )
    )

  def getAll(
              beachIds: Seq[BeachId]
            ): ZIO[Any, Throwable | BeachNotFound, Map[BeachId, Beach]] =
    ZIO.collectAll(
      beachIds
        .map(getStatus(_))
        .map(beaches => beaches.map(beach => (beach.beachId, beach))))
      .map(_.view.toMap)


final case class BeachId(id: Long) extends AnyVal

final case class Wind(
                       direction: Double = 0,
                       speed: Double = 0,
                       directionText: String,
                       trend: String
                     )

final case class Swell(
                        height: Double = 0,
                        direction: Double = 0,
                        directionText: String
                      )

final case class TideHeight(
                             height: Double,
                             status: String,
                             nextLow: Long,
                             nextHigh: Long
                           )

final case class SwellOutput(
                              height: Double = 0,
                              direction: Double = 0,
                              directionText: String
                            )

final case class Tide(height: TideHeight, swell: SwellOutput)

final case class Beach(beachId: BeachId, wind: Wind, tide: Tide)