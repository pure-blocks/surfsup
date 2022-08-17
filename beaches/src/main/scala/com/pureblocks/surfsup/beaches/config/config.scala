package com.pureblocks.surfsup.beaches.config

import com.pureblocks.surfsup.beaches.infrastructure.Datum
import zio.json.{DeriveJsonDecoder, JsonDecoder}

case class Beaches(beaches: Seq[Beach]) {
  def toMap() = {
    beaches.groupBy(_.id).mapValues(_.head).toMap
  }
}
object Beaches {
  implicit val decoder: JsonDecoder[Beaches] = DeriveJsonDecoder.gen[Beaches]
}

case class Beach(id: Long, location: String, postCode: Long, region: String)
object Beach {
  implicit val decoder: JsonDecoder[Beach] = DeriveJsonDecoder.gen[Beach]
}