import com.pureblocks.surfsup.beaches.config.Beaches
import com.pureblocks.surfsup.beaches.core.{
  BeachId,
  BeachNotFound,
  TidesStatusProvider,
  WindStatusProvider
}
import com.pureblocks.surfsup.beaches.infrastructure.WillyWeatherWindStatusProvider
import com.pureblocks.surfsup.beaches.infrastructure.WillyWeatherTideStatusProvider
import sttp.client3.httpclient.zio.HttpClientZioBackend
import zio.{Console, Scope, ZIO, ZIOAppDefault, ZLayer}
import zio.*
import zio.json.*

import java.io.{FileInputStream, IOException}
import scala.io.Source

object Main extends ZIOAppDefault:
  override def run =
    def program(winds: WindStatusProvider, tides: TidesStatusProvider) = for {
      wind <- winds.get(BeachId(4988)).tapError(e => ZIO.logError("" + e))
      _ <- Console.printLine {
        wind
      }
      tide <- tides.get(BeachId(4988)).tapError(e => ZIO.logError("" + e))
        .tapError(e => ZIO.logError("" + e))
      _ <- Console.printLine {
        tide
      }
    } yield ()

    val a = ZLayer
      .make[WillyWeatherWindStatusProvider](
        WillyWeatherWindStatusProvider.live,
        HttpClientZioBackend.layer(),
        ZLayer.succeed(sys.env("WILLY_WEATHER_KEY"))
      )
      .build
      .map(_.get[WillyWeatherWindStatusProvider])
    val b = ZLayer
      .make[WillyWeatherTideStatusProvider](
        WillyWeatherTideStatusProvider.live,
        ZLayer.fromZIO(
          ZIO
            .readFile(getClass.getResource("beaches.json").getPath)
            .flatMap(c => {

              ZIO.fromEither(c.fromJson[Beaches])
            })
            .map(_.toMap())
        ),
        ZLayer.fromZIO(ZIO.clock),
        HttpClientZioBackend.layer(),
        ZLayer.succeed(sys.env("WILLY_WEATHER_KEY"))
      )
      .build
      .map(_.get[WillyWeatherTideStatusProvider])

    val c = a.zip(b)
    c.flatMap((a, b) => program(a, b))
