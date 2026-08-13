# Open Insurance Engine

A Property & Casualty (P&C) insurance core engine for financial and insurance carriers, built with **Scala 3**, **Cats Effect**, **FS2**, and in near future **Apache Kafka**.

Architecture inspired by the typical domain ecosystem with below generic modules like: `PolicyCenter`, `BillingCenter`, `ClaimCenter`. 

## Modules

| Module | Role |
|--------|------|
| `core` | Opaque IDs, Money (minor units), Time, Result |
| `rules` | Business rules engine (Accept / Reject / Modify / Refer) |
| `validation` | Accumulating field validation (`ValidatedNel`) |
| `plugins` | Plugin SPI (rating, underwriting, payments, fraud, documents...) |
| `policy` | New business: draft -> quote -> bind -> cancel |
| `rating` | Weighted / multiplicative rate engine, rate tables, worksheets, Personal Auto plan |

## Requirements

- JDK 17+
- sbt 1.10+

## Running demo app

```bash
sbt "app/run --demo"


[info] Starting demo scenario on 2026-08-13...
[info] 23:15:49.278 [io-compute-1] INFO  c.g.b.openinsuranceengine.app.Main - === 1. Create draft policy ===
[info] 23:15:49.288 [io-compute-1] INFO  c.g.b.openinsuranceengine.app.Main - === 2. Weighted rating (client profile -> premium) ===
[info] 23:15:49.294 [io-compute-1] INFO  c.g.b.openinsuranceengine.app.Main - Insured: age=23, licensed=3y, claims=1, credit=Standard, region=PL-MZ
[info] Created Account: ae9ab8f9-0abf-4da3-ba99-dc7991dbf292, Party: 866a9032-2142-4184-8fc8-81eda13fe1e6
[info] Product ID: 3672d1f5-9d84-4569-8ddb-b372415e7380, Policy ID: 95f0766a-4ecb-4ec9-9015-5e2dd6187b40
[info] Coverage: BI (Base Premium: 1000,00 PLN)
[info] Vehicle: Volkswagen Golf
[info] Rated premium: 1215,50 PLN
[info] Rate worksheet (WeightedAverage)
[info]   Base rate:      1000,00 PLN
[info]   Combined factor: 1.2155
[info]   Final premium:  1215,50 PLN
[info]   Factors:
[info]     - AGE              Driver age                   input=23           band=Youth (21-24)    factor=1,450  weight= 2,50
[info]     - EXPERIENCE       Years licensed               input=3            band=Junior (2-4y)    factor=1,200  weight= 1,50
[info]     - CLAIMS           Prior claims                 input=1            band=1 claim          factor=1,150  weight= 2,00
[info]     - CREDIT           Credit band                  input=Standard     band=Standard         factor=1,000  weight= 1,20
[info]     - REGION           Region                       input=PL-MZ        band=Mazowieckie (Warsaw) factor=1,250  weight= 1,00
[info]     - MILEAGE          Annual mileage               input=15000        band=High (15-25k)    factor=1,180  weight= 1,00
[info]     - VEHICLE_AGE      Vehicle age                  input=6            band=Mid (3-7y)       factor=1,000  weight= 0,80
[info] Services container available: Services
[info] Finished!



```

## Running unit tests

```bash
sbt test

...

[info] Passed: Total 68, Failed 0, Errors 0, Passed 68
```


## Stack

- Scala 3.3
- Cats Effect 3
- FS2 3
- fs2-kafka
- Circe
- Decline
- log4cats
- MUnit

## TBD
