package com.github.bieli.openinsuranceengine.app

import cats.effect.IO
import cats.syntax.all.*
import com.github.bieli.openinsuranceengine.claim.Claim
import com.github.bieli.openinsuranceengine.core.risk.VehicleRisk
import com.github.bieli.openinsuranceengine.documents.{
  DocumentFormat,
  DocumentService,
  DocumentTemplate,
  DocumentType,
  GeneratedDocument,
  TextDocumentRenderer
}
import com.github.bieli.openinsuranceengine.policy.PolicyPeriod
import com.github.bieli.openinsuranceengine.rating.InsuredProfile

/** Demo forms: policy declarations after bind, claim acknowledgement after close. */
object DemoDocuments:

  val PolicyDeclarationsId = "PA-DEC-PL-2026"
  val ClaimAcknowledgementId = "PA-CLAIM-ACK-PL-2026"

  final case class PolicyDeclarationsInput(
      period: PolicyPeriod,
      insured: InsuredProfile,
      vehicle: VehicleRisk
  )

  final case class ClaimAcknowledgementInput(
      claim: Claim,
      policyNumber: String
  )

  def register(svc: DocumentService[IO]): IO[Unit] =
    svc.register(policyDeclarationsRenderer) *> svc.register(claimAcknowledgementRenderer)

  def describe(doc: GeneratedDocument): String =
    s"Document: ${doc.fileName} type=${doc.documentType} ${doc.content.length} bytes"

  private val policyDeclarationsRenderer =
    TextDocumentRenderer[IO, PolicyDeclarationsInput](
      DocumentTemplate(
        id = PolicyDeclarationsId,
        name = "Personal Auto Declarations (PL 2026)",
        documentType = DocumentType.PolicyDeclarations,
        format = DocumentFormat.Text
      ),
      renderFn = in =>
        val number = in.period.policyNumber.getOrElse("UNNUMBERED")
        val plate = in.vehicle.licensePlate.getOrElse("n/a")
        val term = in.period.term.period
        val coverages = in.period.coverages
          .map(c => s"  - ${c.code} limit=${c.limit} deductible=${c.deductible}")
          .mkString("\n")
        s"""PERSONAL AUTO - POLICY DECLARATIONS
           |Policy: $number
           |Status: ${in.period.status}
           |Line: ${in.period.lineOfBusiness}
           |Term: ${term.start} / ${term.end}
           |Insured age: ${in.insured.age}  licensed: ${in.insured.yearsLicensed}y  region: ${in.insured.regionCode}
           |Vehicle: ${in.vehicle.year} ${in.vehicle.make} ${in.vehicle.model} ($plate)
           |Coverages:
           |$coverages
           |Premium: ${in.period.totalPremium}
           |""".stripMargin,
      fileNameFn = in => s"DEC-${in.period.policyNumber.getOrElse("DRAFT")}.txt"
    )

  private val claimAcknowledgementRenderer =
    TextDocumentRenderer[IO, ClaimAcknowledgementInput](
      DocumentTemplate(
        id = ClaimAcknowledgementId,
        name = "Claim acknowledgement (PL 2026)",
        documentType = DocumentType.ClaimAcknowledgement,
        format = DocumentFormat.Text
      ),
      renderFn = in =>
        val number = in.claim.claimNumber.getOrElse("UNNUMBERED")
        val paid = in.claim.totalPaid.fold(_ => "n/a", _.toString)
        s"""CLAIM ACKNOWLEDGEMENT
           |Claim: $number
           |Policy: ${in.policyNumber}
           |Status: ${in.claim.status}
           |Loss: ${in.claim.loss.lossType} on ${in.claim.loss.lossDate}
           |Location: ${in.claim.loss.location.getOrElse("n/a")}
           |Description: ${in.claim.loss.description}
           |Paid: $paid
           |""".stripMargin,
      fileNameFn = in => s"ACK-${in.claim.claimNumber.getOrElse("DRAFT")}.txt"
    )
