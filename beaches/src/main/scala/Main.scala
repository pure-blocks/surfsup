import com.pureblocks.surfsup.beaches.core.{BeachId, BeachNotFound, WindStatusService}
import com.pureblocks.surfsup.beaches.infrastructure.WillyWeatherWindStatusService
import sttp.client3.httpclient.zio.HttpClientZioBackend
import zio.{Console, Scope, ZIO, ZIOAppDefault, ZLayer}
import zio.*

import java.io.IOException

object Main extends ZIOAppDefault :
  override def run =
    def program(api: WindStatusService) = for {
      data <- api.get(BeachId(4988)).tapError(e=>ZIO.logError("" + e))
      _ <- Console.printLine {
        data
      }
    } yield ()

    ZLayer.make[WillyWeatherWindStatusService](
      WillyWeatherWindStatusService.live,
      HttpClientZioBackend.layer(),
      ZLayer.succeed(sys.env("WILLY_WEATHER_KEY")))
        .build
        .map(_.get[WillyWeatherWindStatusService])
        .flatMap(program(_))