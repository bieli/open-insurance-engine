package com.github.bieli.openinsuranceengine.core.algebra

import cats.effect.IO
import munit.CatsEffectSuite

final case class Item(id: String, name: String)

class RepositorySuite extends CatsEffectSuite:
  test("save get delete findAll"):
    for
      repo <- Repository.inMemory[IO, String, Item](_.id)
      _ <- repo.save(Item("1", "a"))
      _ <- repo.save(Item("2", "b"))
      got <- repo.get("1")
      all <- repo.findAll
      deleted <- repo.delete("1")
      missing <- repo.get("1")
      deletedAgain <- repo.delete("1")
    yield
      assertEquals(got, Some(Item("1", "a")))
      assertEquals(all.map(_.id).toSet, Set("1", "2"))
      assert(deleted)
      assertEquals(missing, None)
      assert(!deletedAgain)

  test("save overwrites existing id"):
    for
      repo <- Repository.inMemory[IO, String, Item](_.id)
      _ <- repo.save(Item("1", "old"))
      _ <- repo.save(Item("1", "new"))
      got <- repo.get("1")
    yield assertEquals(got.map(_.name), Some("new"))

  test("get returns None for unknown id"):
    for
      repo <- Repository.inMemory[IO, String, Item](_.id)
      got <- repo.get("missing")
    yield assertEquals(got, None)
