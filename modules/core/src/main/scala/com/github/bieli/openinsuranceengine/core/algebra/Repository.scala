package com.github.bieli.openinsuranceengine.core.algebra

import cats.effect.Sync
import cats.syntax.all.*

/**
 * Tagless-final repository algebra - domain modules define specialised versions.
 * Keeps persistence swappable (in-memory, JDBC, Kafka state store, etc.).
 */
trait Repository[F[_], Id, Entity]:
  def get(id: Id): F[Option[Entity]]
  def save(entity: Entity): F[Entity]
  def delete(id: Id): F[Boolean]
  def findAll: F[List[Entity]]

object Repository:
  def inMemory[F[_]: Sync, Id, Entity](
      idOf: Entity => Id
  ): F[Repository[F, Id, Entity]] =
    Sync[F].delay(new java.util.concurrent.ConcurrentHashMap[Id, Entity]()).map { store =>
      new Repository[F, Id, Entity]:
        def get(id: Id): F[Option[Entity]] =
          Sync[F].delay(Option(store.get(id)))

        def save(entity: Entity): F[Entity] =
          Sync[F].delay:
            store.put(idOf(entity), entity)
            entity

        def delete(id: Id): F[Boolean] =
          Sync[F].delay:
            Option(store.remove(id)).isDefined

        def findAll: F[List[Entity]] =
          Sync[F].delay:
            import scala.jdk.CollectionConverters.*
            store.values().asScala.toList
    }
