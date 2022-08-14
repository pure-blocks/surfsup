package com.pureblocks.surfsup.core
import com.pureblocks.surfsup.core.TechnicalError
import zio.{IO, ZIO, ZLayer}

type TechnicalError = Throwable
type TIO[+E, +A] = IO[TechnicalError | E, A]
type TRIO[R, +E, +A] = ZIO[R, TechnicalError | E, A]
