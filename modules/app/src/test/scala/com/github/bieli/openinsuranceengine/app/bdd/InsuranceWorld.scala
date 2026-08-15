package com.github.bieli.openinsuranceengine.app.bdd

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.github.bieli.openinsuranceengine.app.{Main, SubmissionState, SubmissionWorkflow}
import com.github.bieli.openinsuranceengine.billing.Invoice
import com.github.bieli.openinsuranceengine.claim.*
import com.github.bieli.openinsuranceengine.core.id.{
  AccountTag,
  CoverageTag,
  EntityId,
  PartyTag,
  PolicyTag,
  ProductTag
}
import com.github.bieli.openinsuranceengine.core.money.{CurrencyCode, Money}
import com.github.bieli.openinsuranceengine.core.product.{Coverage, CoverageType, LineOfBusiness}
import com.github.bieli.openinsuranceengine.core.result.DomainResult
import com.github.bieli.openinsuranceengine.core.risk.VehicleRisk
import com.github.bieli.openinsuranceengine.core.time.{DateRange, EffectiveInstant}
import com.github.bieli.openinsuranceengine.documents.GeneratedDocument
import com.github.bieli.openinsuranceengine.policy.{PolicyPeriod, PolicyStatus, PolicyTerm}
import com.github.bieli.openinsuranceengine.policy.JobType
import com.github.bieli.openinsuranceengine.rating.{CreditBand, InsuredProfile}
import com.github.bieli.openinsuranceengine.workflow.{WorkflowEngine, WorkflowInstance, WorkflowStatus}

import java.time.LocalDate

/** Mutable scenario context for Gherkin steps. Reset in Before. */
final class InsuranceWorld:
  var services: Option[Main.Services] = None
  var insured: InsuredProfile = InsuredProfile(35, 10, 0, "PL-OTHER", CreditBand.Good)
  var vehicle: VehicleRisk = VehicleRisk("WVWZZZ1JZYW386752", "Volkswagen", "Golf", 2020, Some("WA12345"), Some(15000))
  var period: Option[PolicyPeriod] = None
  var workflow: Option[WorkflowInstance[SubmissionState]] = None
  var claim: Option[Claim] = None
  var lastErrors: List[String] = Nil
  var documents: List[GeneratedDocument] = Nil
  var invoices: List[Invoice] = Nil
  var pendingReserve: Option[Money] = None

  val currency: CurrencyCode = CurrencyCode.PLN
  val coverageLimit: Money = Money.fromMajor(BigDecimal(1000000), currency)

  def run[A](io: IO[A]): A = io.unsafeRunSync()

  def requireDomain[A](result: DomainResult[A]): A =
    result match
      case Right(value) =>
        lastErrors = Nil
        value
      case Left(errs) =>
        lastErrors = errs.toList.map(_.code)
        throw new AssertionError(s"Expected success, got ${errs.toList.map(e => s"${e.code}: ${e.message}").mkString(", ")}")

  def buildPeriod(): PolicyPeriod =
    val coverageId = EntityId.random[CoverageTag]()
    PolicyPeriod(
      policyId = EntityId.random[PolicyTag](),
      accountId = EntityId.random[AccountTag](),
      productId = EntityId.random[ProductTag](),
      policyNumber = None,
      status = PolicyStatus.Draft,
      jobType = JobType.Submission,
      term = PolicyTerm(DateRange(LocalDate.now(), LocalDate.now().plusYears(1)), termNumber = 1),
      lineOfBusiness = LineOfBusiness.PersonalAuto,
      primaryInsuredId = EntityId.random[PartyTag](),
      coverages = List(
        Coverage(
          id = coverageId,
          code = "BI",
          coverageType = CoverageType.BodilyInjury,
          limit = coverageLimit,
          deductible = Money.fromMajor(BigDecimal(500), currency),
          premium = Money.fromMajor(BigDecimal(1000), currency)
        )
      ),
      risks = List(vehicle),
      totalPremium = Money.fromMajor(BigDecimal(1000), currency),
      createdAt = EffectiveInstant.now()
    )

  def svc: Main.Services = services.getOrElse(sys.error("engine not started"))

  def runWorkflow(): WorkflowInstance[SubmissionState] =
    val definition = SubmissionWorkflow.definition(svc.policy, svc.ratingEngine)
    val submission = SubmissionState(period = buildPeriod(), insured = insured)
    val started = requireDomain(run(svc.workflow.start(definition, submission)))
    val done = run(loop(svc.workflow, definition, started))
    workflow = Some(done)
    period = Some(done.state.period)
    done

  private def loop(
      engine: WorkflowEngine[IO, SubmissionState],
      definition: com.github.bieli.openinsuranceengine.workflow.WorkflowDefinition[SubmissionState, IO],
      instance: WorkflowInstance[SubmissionState]
  ): IO[WorkflowInstance[SubmissionState]] =
    instance.status match
      case WorkflowStatus.Completed | WorkflowStatus.Failed | WorkflowStatus.Cancelled =>
        IO.pure(instance)
      case _ =>
        engine.advance(instance, definition).flatMap: result =>
          result match
            case Right(next) => loop(engine, definition, next)
            case Left(errs) =>
              IO.raiseError(new AssertionError(errs.toList.map(e => s"${e.code}: ${e.message}").mkString(", ")))
