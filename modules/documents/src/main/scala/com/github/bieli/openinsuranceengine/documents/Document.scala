package com.github.bieli.openinsuranceengine.documents

import cats.effect.Sync
import cats.syntax.all.*
import com.github.bieli.openinsuranceengine.core.id.{DocumentId, DocumentTag, EntityId}
import com.github.bieli.openinsuranceengine.core.result.DomainResult
import com.github.bieli.openinsuranceengine.core.time.EffectiveInstant

/**
 * Document generation - Document Production / forms engine.
 * Templates are rendered against a typed model into bytes (PDF, XML, HTML...).
 */
enum DocumentFormat:
  case Pdf, Html, Xml, Json, Text

object DocumentFormat:
  given CanEqual[DocumentFormat, DocumentFormat] = CanEqual.derived

enum DocumentType:
  case PolicyDeclarations
  case Invoice
  case Endorsement
  case ClaimAcknowledgement
  case LossRun
  case CertificateOfInsurance
  case Custom(code: String)

object DocumentType:
  given CanEqual[DocumentType, DocumentType] = CanEqual.derived

final case class DocumentTemplate(
    id: String,
    name: String,
    documentType: DocumentType,
    format: DocumentFormat,
    locale: String = "pl-PL"
)

final case class GeneratedDocument(
    id: DocumentId,
    templateId: String,
    documentType: DocumentType,
    format: DocumentFormat,
    fileName: String,
    content: Array[Byte],
    generatedAt: EffectiveInstant,
    metadata: Map[String, String] = Map.empty
)

trait DocumentRenderer[F[_], Model]:
  def template: DocumentTemplate
  def render(model: Model): F[DomainResult[GeneratedDocument]]

trait DocumentService[F[_]]:
  def register[Model](renderer: DocumentRenderer[F, Model]): F[Unit]
  def render[Model](templateId: String, model: Model): F[DomainResult[GeneratedDocument]]

object DocumentService:
  def inMemory[F[_]: Sync]: F[DocumentService[F]] =
    Sync[F]
      .delay(scala.collection.mutable.Map.empty[String, DocumentRenderer[F, Any]])
      .map: registry =>
        new DocumentService[F]:
          def register[Model](renderer: DocumentRenderer[F, Model]): F[Unit] =
            Sync[F].delay:
              registry.update(renderer.template.id, renderer.asInstanceOf[DocumentRenderer[F, Any]])
              ()

          def render[Model](templateId: String, model: Model): F[DomainResult[GeneratedDocument]] =
            Sync[F].defer:
              registry.get(templateId) match
                case None =>
                  Sync[F].pure(
                    DomainResult.raise(
                      com.github.bieli.openinsuranceengine.core.result.DomainError.NotFound(
                        "DOC_TEMPLATE",
                        s"Template '$templateId' not found"
                      )
                    )
                  )
                case Some(renderer) =>
                  renderer.asInstanceOf[DocumentRenderer[F, Model]].render(model)

/** Simple text/HTML template renderer for demos and tests. */
final class TextDocumentRenderer[F[_]: Sync, Model](
    val template: DocumentTemplate,
    renderFn: Model => String,
    fileNameFn: Model => String = (_: Model) => s"${java.util.UUID.randomUUID()}.txt"
) extends DocumentRenderer[F, Model]:
  def render(model: Model): F[DomainResult[GeneratedDocument]] =
    Sync[F].realTimeInstant.map: now =>
      val body = renderFn(model)
      Right(
        GeneratedDocument(
          id = EntityId.random[DocumentTag](),
          templateId = template.id,
          documentType = template.documentType,
          format = template.format,
          fileName = fileNameFn(model),
          content = body.getBytes(java.nio.charset.StandardCharsets.UTF_8),
          generatedAt = EffectiveInstant(now)
        )
      )
