package com.github.bieli.openinsuranceengine.rulesstudio

import cats.effect.{ExitCode, IO, IOApp}
import com.comcast.ip4s.{ipv4, port}
import org.http4s.{HttpRoutes, MediaType, Request, Response, StaticFile}
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.`Content-Type`
import org.http4s.implicits.*
import org.http4s.server.middleware.Logger
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

/** Serves the Rules Studio SPA (index.html + JS + CSS) and the current engine catalog YAML. */
object RulesStudio extends IOApp:

  given LoggerFactory[IO] = Slf4jFactory.create[IO]

  private val Host = ipv4"0.0.0.0"
  private val Port = port"8080"
  private val YamlType = `Content-Type`(MediaType.unsafeParse("text/yaml"))

  private def static(resource: String, request: Request[IO]): IO[Response[IO]] =
    StaticFile
      .fromResource[IO](resource, Some(request))
      .getOrElseF(NotFound())

  private val routes: HttpRoutes[IO] =
    HttpRoutes.of[IO]:
      case request @ GET -> Root / "api" / "catalog.yaml" =>
        StaticFile
          .fromResource[IO]("oie-rules.yaml", Some(request))
          .map(_.withContentType(YamlType))
          .getOrElseF(NotFound())

      case request @ GET -> Root =>
        static("web/index.html", request)

      case request @ GET -> path =>
        val relative = path.segments.map(_.decoded()).mkString("/")
        StaticFile
          .fromResource[IO](s"web/$relative", Some(request))
          .orElse(StaticFile.fromResource[IO]("web/index.html", Some(request)))
          .getOrElseF(NotFound())

  private val httpApp = Logger.httpApp(logHeaders = false, logBody = false)(routes.orNotFound)

  override def run(args: List[String]): IO[ExitCode] =
    EmberServerBuilder
      .default[IO]
      .withHost(Host)
      .withPort(Port)
      .withHttpApp(httpApp)
      .build
      .use: server =>
        IO.println(s"Rules Studio: ${server.baseUri}") *> IO.never
      .as(ExitCode.Success)
