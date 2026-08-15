package com.github.bieli.openinsuranceengine.app

import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.all.*
import com.github.bieli.openinsuranceengine.billing.{BillingService, Invoice, Payment}
import com.github.bieli.openinsuranceengine.claim.*
import com.github.bieli.openinsuranceengine.core.algebra.Repository
import com.github.bieli.openinsuranceengine.core.id.{
  AccountTag,
  ClaimId,
  ClaimTag,
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
import com.github.bieli.openinsuranceengine.core.result.DomainResult
import com.github.bieli.openinsuranceengine.documents.DocumentService
import com.github.bieli.openinsuranceengine.plugins.PluginRegistry
import com.github.bieli.openinsuranceengine.policy.*
import com.github.bieli.openinsuranceengine.rating.*
import com.github.bieli.openinsuranceengine.workflow.{WorkflowDefinition, WorkflowEngine, WorkflowInstance, WorkflowStatus}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.time.LocalDate

object Main extends IOApp:

  final case class Services(
      policy: PolicyService[IO],
      billing: BillingService[IO],
      claim: ClaimService[IO],
      documents: DocumentService[IO],
      plugins: PluginRegistry[IO, PolicyPeriod],
      ratingEngine: RatingEngine,
      workflow: WorkflowEngine[IO, SubmissionState]
  )

  def buildServices: IO[Services] =
    for
      policyRepo <- Repository.inMemory[IO, PolicyId, PolicyPeriod](_.policyId)
      invoiceRepo <- Repository.inMemory[IO, InvoiceId, Invoice](_.id)
      paymentRepo <- Repository.inMemory[IO, PaymentId, Payment](_.id)
      claimRepo <- Repository.inMemory[IO, ClaimId, Claim](_.id)
      documents <- DocumentService.inMemory[IO]
      _ <- DemoDocuments.register(documents)
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
      claim = ClaimService[IO](claimRepo),
      documents = documents,
      plugins = plugins,
      ratingEngine = engine,
      workflow = WorkflowEngine[IO, SubmissionState]
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

    val definition = SubmissionWorkflow.definition(services.policy, services.ratingEngine)
    val submission = SubmissionState(period = period, insured = insured)

    for
      _ <- IO(println(s"Starting demo scenario on $today..."))
      _ <- logger.info(
        s"Insured: age=${insured.age}, licensed=${insured.yearsLicensed}y, claims=${insured.priorClaimsLast3Years}, " +
          s"credit=${insured.creditBand}, region=${insured.regionCode}"
      )
      _ <- logger.info(s"=== New-business workflow: ${definition.name} ===")
      started <- services.workflow.start(definition, submission).flatMap(requireDomain("workflow.start"))
      done <- runUntilDone(services.workflow, definition, started)
      rated = done.state.period
      worksheets = done.state.worksheets
      _ <- IO(println(s"Created Account: $accountId, Party: $partyId"))
      _ <- IO(println(s"Product ID: $productId, Policy ID: $policyId"))
      _ <- IO(println(s"Coverage: ${coverage.code} (Base Premium: $baseCoveragePremium)"))
      _ <- IO(println(s"Vehicle: ${vehicle.make} ${vehicle.model}"))
      _ <- IO(println(s"Rated premium: ${rated.totalPremium}"))
      _ <- worksheets.traverse_(w => IO(println(w.summary)))
      _ <- IO(
        println(
          s"Workflow: ${done.status} via ${done.history.map(_.stepId).mkString(" -> ")}"
        )
      )
      _ <- IO(
        println(
          s"Policy: status=${rated.status} number=${rated.policyNumber.getOrElse("N/A")}"
        )
      )
      _ <- logger.info("=== Document production: policy declarations ===")
      declarations <- services.documents
        .render(
          DemoDocuments.PolicyDeclarationsId,
          DemoDocuments.PolicyDeclarationsInput(rated, insured, vehicle)
        )
        .flatMap(requireDomain("documents.policyDeclarations"))
      _ <- IO(println(DemoDocuments.describe(declarations)))
      _ <- IO(println(new String(declarations.content, java.nio.charset.StandardCharsets.UTF_8).trim))
      _ <- logger.info("=== First notice of loss ===")
      opened <- services.claim
        .openFnol(
          Claim(
            id = EntityId.random[ClaimTag](),
            claimNumber = None,
            policyId = rated.policyId,
            status = ClaimStatus.Draft,
            loss = LossDetails(
              lossDate = today,
              lossType = LossType.Collision,
              description = "Rear-end collision, Volkswagen Golf",
              location = Some("Warsaw, PL"),
              policeReportNumber = Some("KSP-2026-88421")
            ),
            claimantId = partyId,
            reserves = Nil,
            payments = Nil,
            tier = ClaimTier.Medium,
            createdAt = EffectiveInstant.now(),
            defaultCurrency = currency
          ),
          policyInForce = rated.status == PolicyStatus.InForce,
          coverageLimit = Some(coverage.limit)
        )
        .flatMap(requireDomain("claim.openFnol"))
      reserved <- services.claim
        .setReserve(
          opened,
          Reserve(coverage.id, "vehicle_damage", Money.fromMajor(BigDecimal(8500), currency))
        )
        .flatMap(requireDomain("claim.setReserve"))
      approved <- services.claim.approve(reserved).flatMap(requireDomain("claim.approve"))
      paid <- services.claim
        .pay(
          approved,
          ClaimPayment(
            amount = Money.fromMajor(BigDecimal(7500), currency),
            payeeId = partyId,
            paidAt = EffectiveInstant.now(),
            reference = Some("IND-GOLF-001")
          )
        )
        .flatMap(requireDomain("claim.pay"))
      closed <- services.claim.close(paid).flatMap(requireDomain("claim.close"))
      _ <- IO(
        println(
          s"Claim: ${closed.claimNumber.getOrElse("N/A")} status=${closed.status} " +
            s"paid=${closed.totalPaid.getOrElse(Money.zero(currency))}"
        )
      )
      _ <- logger.info("=== Document production: claim acknowledgement ===")
      acknowledgement <- services.documents
        .render(
          DemoDocuments.ClaimAcknowledgementId,
          DemoDocuments.ClaimAcknowledgementInput(
            closed,
            rated.policyNumber.getOrElse("N/A")
          )
        )
        .flatMap(requireDomain("documents.claimAcknowledgement"))
      _ <- IO(println(DemoDocuments.describe(acknowledgement)))
      _ <- IO(println(new String(acknowledgement.content, java.nio.charset.StandardCharsets.UTF_8).trim))
      _ <- IO(println(s"Services container available: ${services.getClass.getSimpleName}"))
      _ <- IO(println("Finished!"))
    yield ()

  private def runUntilDone[S](
      engine: WorkflowEngine[IO, S],
      definition: WorkflowDefinition[S, IO],
      instance: WorkflowInstance[S]
  )(using logger: Logger[IO]): IO[WorkflowInstance[S]] =
    def loop(current: WorkflowInstance[S]): IO[WorkflowInstance[S]] =
      current.status match
        case WorkflowStatus.Completed | WorkflowStatus.Failed | WorkflowStatus.Cancelled =>
          IO.pure(current)
        case _ =>
          val stepId = current.currentStepId.getOrElse("?")
          logger.info(s"Workflow step: $stepId") *>
            engine.advance(current, definition).flatMap(requireDomain(s"workflow.$stepId")).flatMap(loop)
    loop(instance)

  private def requireDomain[A](step: String)(result: DomainResult[A]): IO[A] =
    result match
      case Right(value) => IO.pure(value)
      case Left(errs) =>
        IO.raiseError(
          new RuntimeException(errs.toList.map(e => s"$step: ${e.code}: ${e.message}").mkString(", "))
        )
