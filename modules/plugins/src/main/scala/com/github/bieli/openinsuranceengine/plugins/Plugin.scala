package com.github.bieli.openinsuranceengine.plugins

import cats.effect.Sync
import cats.syntax.all.*
import com.github.bieli.openinsuranceengine.core.result.{DomainError, DomainResult}

/**
 * Plugin SPI - mirrors Guidewire plugin architecture (pre/post update, rating,
 * geocoding, document production, payment gateways, etc.).
 */
trait PluginCapability

trait Plugin[F[_], Ctx, +Cap <: PluginCapability]:
  def id: String
  def name: String
  def version: String
  def capability: Cap
  def execute(ctx: Ctx): F[DomainResult[Ctx]]

enum BuiltinCapability extends PluginCapability:
  case PreUpdate
  case PostUpdate
  case Rating
  case Underwriting
  case Geocoding
  case PaymentGateway
  case DocumentProduction
  case FraudDetection
  case Notification
  case Custom(code: String)

object BuiltinCapability:
  given CanEqual[BuiltinCapability, BuiltinCapability] = CanEqual.derived

trait PluginRegistry[F[_], Ctx]:
  def register[Cap <: PluginCapability](plugin: Plugin[F, Ctx, Cap]): F[Unit]
  def findByCapability(cap: PluginCapability): F[List[Plugin[F, Ctx, PluginCapability]]]
  def findById(id: String): F[Option[Plugin[F, Ctx, PluginCapability]]]
  def executeAll(cap: PluginCapability, ctx: Ctx): F[DomainResult[Ctx]]

object PluginRegistry:
  def inMemory[F[_]: Sync, Ctx]: F[PluginRegistry[F, Ctx]] =
    Sync[F]
      .delay(scala.collection.mutable.ListBuffer.empty[Plugin[F, Ctx, PluginCapability]])
      .map: buffer =>
        new PluginRegistry[F, Ctx]:
          def register[Cap <: PluginCapability](plugin: Plugin[F, Ctx, Cap]): F[Unit] =
            Sync[F].delay:
              buffer += plugin.asInstanceOf[Plugin[F, Ctx, PluginCapability]]
              ()

          def findByCapability(cap: PluginCapability): F[List[Plugin[F, Ctx, PluginCapability]]] =
            Sync[F].delay(buffer.filter(_.capability.equals(cap)).toList)

          def findById(id: String): F[Option[Plugin[F, Ctx, PluginCapability]]] =
            Sync[F].delay(buffer.find(_.id == id))

          def executeAll(cap: PluginCapability, ctx: Ctx): F[DomainResult[Ctx]] =
            findByCapability(cap).flatMap: plugins =>
              plugins.foldLeft(Sync[F].pure(Right(ctx): DomainResult[Ctx])): (accF, plugin) =>
                accF.flatMap:
                  case Left(errs) => Sync[F].pure(Left(errs))
                  case Right(c) =>
                    plugin.execute(c).map:
                      case Right(nc) => Right(nc)
                      case Left(errs) =>
                        Left(errs.map: e =>
                          DomainError.PluginError(e.code, e.message, plugin.id): DomainError
                        )
