package com.github.bieli.openinsuranceengine.documents

import cats.effect.IO
import munit.CatsEffectSuite

class DocumentServiceSuite extends CatsEffectSuite:
  private val template = DocumentTemplate(
    id = "inv-v1",
    name = "Invoice",
    documentType = DocumentType.Invoice,
    format = DocumentFormat.Text
  )

  test("register and render text document"):
    for
      svc <- DocumentService.inMemory[IO]
      _ <- svc.register(
        TextDocumentRenderer[IO, String](
          template,
          renderFn = s => s"Invoice for $s",
          fileNameFn = s => s"$s.txt"
        )
      )
      result <- svc.render("inv-v1", "Alice")
    yield
      assert(result.isRight)
      val doc = result.toOption.get
      assertEquals(doc.fileName, "Alice.txt")
      assertEquals(new String(doc.content, "UTF-8"), "Invoice for Alice")
      assertEquals(doc.documentType, DocumentType.Invoice)

  test("render unknown template fails"):
    for
      svc <- DocumentService.inMemory[IO]
      result <- svc.render[String]("missing", "x")
    yield assert(result.isLeft)

  test("overwrite template registration"):
    for
      svc <- DocumentService.inMemory[IO]
      _ <- svc.register(TextDocumentRenderer[IO, Int](template, _ => "v1"))
      _ <- svc.register(TextDocumentRenderer[IO, Int](template, _ => "v2"))
      result <- svc.render("inv-v1", 1)
    yield assertEquals(result.map(d => new String(d.content, "UTF-8")), Right("v2"))
