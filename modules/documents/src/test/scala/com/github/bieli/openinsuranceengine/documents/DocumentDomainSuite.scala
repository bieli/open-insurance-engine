package com.github.bieli.openinsuranceengine.documents

import munit.FunSuite

class DocumentDomainSuite extends FunSuite:

  test("DocumentFormat values cover common carrier outputs"):
    assert(DocumentFormat.Pdf != DocumentFormat.Html)
    assert(DocumentFormat.Xml != DocumentFormat.Json)
    assertEquals(DocumentFormat.Text, DocumentFormat.Text)

  test("DocumentType includes COI and loss run"):
    assertEquals(DocumentType.CertificateOfInsurance, DocumentType.CertificateOfInsurance)
    assert(DocumentType.LossRun != DocumentType.Invoice)
    assertEquals(DocumentType.Custom("GREEN-CARD"), DocumentType.Custom("GREEN-CARD"))

  test("DocumentTemplate defaults locale to pl-PL"):
    val t = DocumentTemplate(
      id = "dec-1",
      name = "Declarations",
      documentType = DocumentType.PolicyDeclarations,
      format = DocumentFormat.Pdf
    )
    assertEquals(t.locale, "pl-PL")

  test("DocumentTemplate can override locale"):
    val t = DocumentTemplate(
      id = "inv-de",
      name = "Rechnung",
      documentType = DocumentType.Invoice,
      format = DocumentFormat.Pdf,
      locale = "de-DE"
    )
    assertEquals(t.locale, "de-DE")
