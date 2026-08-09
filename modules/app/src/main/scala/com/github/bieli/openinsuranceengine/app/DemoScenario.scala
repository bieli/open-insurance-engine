package com.github.bieli.openinsuranceengine.app

import cats.effect.IO
import com.github.bieli.openinsuranceengine.app.Main.Services
import com.github.bieli.openinsuranceengine.core.id.*
import com.github.bieli.openinsuranceengine.core.money.{CurrencyCode, Money}
import com.github.bieli.openinsuranceengine.core.product.*
import com.github.bieli.openinsuranceengine.core.risk.VehicleRisk
import org.typelevel.log4cats.Logger

import java.time.LocalDate

object DemoScenario:

  def run(services: Services)(using logger: Logger[IO]): IO[Unit] =
    val currency = CurrencyCode.PLN
    val today = LocalDate.now()

    val accountId = EntityId.random[AccountTag]()
    val partyId = EntityId.random[PartyTag]()
    val productId = EntityId.random[ProductTag]()
    val policyId = EntityId.random[PolicyTag]()
    val coverageId = EntityId.random[CoverageTag]()

    // Base tariff before personal factors are applied
    val baseCoveragePremium = Money.fromMajor(BigDecimal(1000), currency)

    val coverage = Coverage(
      id = coverageId,
      code = "BI",
      coverageType = CoverageType.BodilyInjury,
      limit = Money.fromMajor(BigDecimal(1000000), currency),
      deductible = Money.fromMajor(BigDecimal(500), currency),
      premium = baseCoveragePremium
    )

    val vehicle = VehicleRisk(
      vin = "WVWZZZ1JZYW386752",
      make = "Volkswagen",
      model = "Golf",
      year = 2020,
      licensePlate = Some("WA12345"),
      annualMileage = Some(15000)
    )

    for {
      _ <- IO(println(s"Starting demo scenario on $today..."))
      _ <- IO(println(s"Created Account: $accountId, Party: $partyId"))
      _ <- IO(println(s"Product ID: $productId, Policy ID: $policyId"))
      _ <- IO(println(s"Coverage: ${coverage.code} (Base Premium: $baseCoveragePremium)"))
      _ <- IO(println(s"Vehicle: ${vehicle.make} ${vehicle.model}"))
      _ <- IO(println(s"Services container available: ${services.getClass.getSimpleName}"))
      _ <- IO(println("Finished!"))
    } yield ()
