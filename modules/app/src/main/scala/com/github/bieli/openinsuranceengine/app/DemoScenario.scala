package com.github.bieli.openinsuranceengine.app

import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.all.*
import com.github.bieli.openinsuranceengine.billing.{BillingService, Invoice, Payment}
import com.github.bieli.openinsuranceengine.core.algebra.Repository
import com.github.bieli.openinsuranceengine.core.id.{
  AccountTag,
  CoverageTag,
  EntityId,
  InvoiceId,
  PartyTag,
  PaymentId,
  PolicyId,
  PolicyTag,
  ProductTag
}
import com.github.bieli.openinsuranceengine.core.money.{CurrencyCode, Money}
import com.github.bieli.openinsuranceengine.core.product.*
import com.github.bieli.openinsuranceengine.core.risk.VehicleRisk
import com.github.bieli.openinsuranceengine.core.time.{DateRange, EffectiveInstant}
import com.github.bieli.openinsuranceengine.plugins.PluginRegistry
import com.github.bieli.openinsuranceengine.policy.*
import com.github.bieli.openinsuranceengine.rating.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.time.LocalDate

object Main extends IOApp:

  final case class Services(
      policy: PolicyService[IO],
      billing: BillingService[IO],
      plugins: PluginRegistry[IO, PolicyPeriod],
      ratingEngine: RatingEngine
  )

  private def buildServices: IO[Services] =
    for
      policyRepo <- Repository.inMemory[IO, PolicyId, PolicyPeriod](_.policyId)
      invoiceRepo <- Repository.inMemory[IO, InvoiceId, Invoice](_.id)
      paymentRepo <- Repository.inMemory[IO, PaymentId, Payment](_.id)
      plugins <- PluginRegistry.inMemory[IO, PolicyPeriod]
      engine = RatingEngine()
      demoProfile = InsuredProfile(
        age = 35,
        yearsLicensed = 10,
        priorClaimsLast3Years = 0,
        regionCode = "PL-OTHER",
        creditBand = CreditBand.Good
      )
      _ <- plugins.register(
        PolicyRatingPlugin[IO](engine, PersonalAutoRatePlan.weightedPlan, demoProfile)
      )
    yield Services(
      policy = PolicyService[IO](policyRepo),
      billing = BillingService[IO](invoiceRepo, paymentRepo),
      plugins = plugins,
      ratingEngine = engine
    )

  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  override def run(args: List[String]): IO[ExitCode] =
    buildServices.flatMap: services =>
      if args.contains("--demo") then DemoScenario.run(services).as(ExitCode.Success)
      else IO(println("Application started normally without demo mode.")).as(ExitCode.Success)

object DemoScenario:

  def run(services: Main.Services)(using logger: Logger[IO]): IO[Unit] =
    val currency = CurrencyCode.PLN
    val today = LocalDate.now()

    val accountId = EntityId.random[AccountTag]()
    val partyId = EntityId.random[PartyTag]()
    val productId = EntityId.random[ProductTag]()
    val policyId = EntityId.random[PolicyTag]()
    val coverageId = EntityId.random[CoverageTag]()

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

    val period = PolicyPeriod(
      policyId = policyId,
      accountId = accountId,
      productId = productId,
      policyNumber = None,
      status = PolicyStatus.Draft,
      jobType = JobType.Submission,
      term = PolicyTerm(DateRange(today, today.plusYears(1)), termNumber = 1),
      lineOfBusiness = LineOfBusiness.PersonalAuto,
      primaryInsuredId = partyId,
      coverages = List(coverage),
      risks = List(vehicle),
      totalPremium = coverage.premium,
      createdAt = EffectiveInstant.now()
    )

    val insured = InsuredProfile(
      age = 23,
      yearsLicensed = 3,
      priorClaimsLast3Years = 1,
      regionCode = "PL-MZ",
      creditBand = CreditBand.Standard
    )

    for
      _ <- IO(println(s"Starting demo scenario on $today..."))
      _ <- logger.info("=== 1. Create draft policy ===")
      draft <- services.policy.createDraft(period).flatMap(requireOk("createDraft"))

      _ <- logger.info("=== 2. Weighted rating (client profile -> premium) ===")
      _ <- logger.info(
        s"Insured: age=${insured.age}, licensed=${insured.yearsLicensed}y, claims=${insured.priorClaimsLast3Years}, " +
          s"credit=${insured.creditBand}, region=${insured.regionCode}"
      )
      ratedPair <- IO.fromEither(
        PolicyRatingPlugin
          .rateWithWorksheets(services.ratingEngine, PersonalAutoRatePlan.weightedPlan, insured, draft)
          .leftMap(errs => new RuntimeException(errs.toList.map(e => s"${e.code}: ${e.message}").mkString(", ")))
      )
      (rated, worksheets) = ratedPair
      _ <- IO(println(s"Created Account: $accountId, Party: $partyId"))
      _ <- IO(println(s"Product ID: $productId, Policy ID: $policyId"))
      _ <- IO(println(s"Coverage: ${coverage.code} (Base Premium: $baseCoveragePremium)"))
      _ <- IO(println(s"Vehicle: ${vehicle.make} ${vehicle.model}"))
      _ <- IO(println(s"Rated premium: ${rated.totalPremium}"))
      _ <- worksheets.traverse_(w => IO(println(w.summary)))
      _ <- IO(println(s"Services container available: ${services.getClass.getSimpleName}"))
      _ <- IO(println("Finished!"))
    yield ()

  private def requireOk[A](step: String)(result: Either[String, A]): IO[A] =
    result match
      case Right(value) => IO.pure(value)
      case Left(err)    => IO.raiseError(new RuntimeException(s"$step failed: $err"))
