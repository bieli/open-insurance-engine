package com.github.bieli.openinsuranceengine.plugins

import cats.data.NonEmptyList
import cats.effect.IO
import com.github.bieli.openinsuranceengine.core.result.DomainError
import munit.CatsEffectSuite

class PluginRegistrySuite extends CatsEffectSuite:
  private def plugin(pid: String, cap: BuiltinCapability, transform: Int => Int) =
    new Plugin[IO, Int, BuiltinCapability]:
      def id = pid
      def name = pid
      def version = "1"
      def capability = cap
      def execute(ctx: Int) = IO.pure(Right(transform(ctx)))

  private def failing(pid: String) =
    new Plugin[IO, Int, BuiltinCapability]:
      def id = pid
      def name = pid
      def version = "1"
      def capability = BuiltinCapability.Rating
      def execute(ctx: Int) =
        IO.pure(Left(NonEmptyList.one(DomainError.Unexpected("FAIL", "boom"))))

  test("register find and executeAll chains plugins"):
    for
      reg <- PluginRegistry.inMemory[IO, Int]
      _ <- reg.register(plugin("a", BuiltinCapability.Rating, _ + 1))
      _ <- reg.register(plugin("b", BuiltinCapability.Rating, _ * 2))
      _ <- reg.register(plugin("c", BuiltinCapability.Underwriting, _ + 100))
      byCap <- reg.findByCapability(BuiltinCapability.Rating)
      byId <- reg.findById("a")
      rated <- reg.executeAll(BuiltinCapability.Rating, 3)
    yield
      assertEquals(byCap.map(_.id), List("a", "b"))
      assert(byId.isDefined)
      // (3+1)*2 = 8
      assertEquals(rated, Right(8))

  test("executeAll stops on first failure and wraps PluginError"):
    for
      reg <- PluginRegistry.inMemory[IO, Int]
      _ <- reg.register(plugin("ok", BuiltinCapability.Rating, _ + 1))
      _ <- reg.register(failing("bad"))
      _ <- reg.register(plugin("never", BuiltinCapability.Rating, _ + 99))
      result <- reg.executeAll(BuiltinCapability.Rating, 0)
    yield
      assert(result.isLeft)
      result.left.foreach: errs =>
        assert(errs.head.isInstanceOf[DomainError.PluginError])
        assertEquals(errs.head.asInstanceOf[DomainError.PluginError].pluginId, "bad")

  test("executeAll with no plugins is identity"):
    for
      reg <- PluginRegistry.inMemory[IO, Int]
      result <- reg.executeAll(BuiltinCapability.FraudDetection, 42)
    yield assertEquals(result, Right(42))
