package com.github.bieli.openinsuranceengine.plugins

import munit.FunSuite

class BuiltinCapabilitySuite extends FunSuite:

  test("builtin capabilities are distinct"):
    assert(BuiltinCapability.Rating != BuiltinCapability.Underwriting)
    assert(BuiltinCapability.PreUpdate != BuiltinCapability.PostUpdate)
    assert(BuiltinCapability.FraudDetection != BuiltinCapability.Notification)

  test("Custom capability distinguished by code"):
    assertEquals(BuiltinCapability.Custom("SANCTIONS"), BuiltinCapability.Custom("SANCTIONS"))
    assert(BuiltinCapability.Custom("A") != BuiltinCapability.Custom("B"))

  test("PaymentGateway and DocumentProduction are separate SPIs"):
    assert(BuiltinCapability.PaymentGateway != BuiltinCapability.DocumentProduction)
    assert(BuiltinCapability.Geocoding != BuiltinCapability.Rating)
