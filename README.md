# Open Insurance Engine

A Property & Casualty (P&C) insurance core engine for financial and insurance carriers, built with **Scala 3**, **Cats Effect**, **FS2**, and in near future **Apache Kafka**.

Architecture inspired by the typical domain ecosystem with below generic modules like: `PolicyCenter`, `BillingCenter`, `ClaimCenter`. 

## Modules

| Module | Role |
|--------|------|
| `core` | Opaque IDs, Money (minor units), Time, Result, in-memory `Repository` |
| `rules` | Business rules engine (Accept / Reject / Modify / Refer) |
| `validation` | Accumulating field validation (`ValidatedNel`) |
| `plugins` | Plugin SPI (rating, underwriting, payments, fraud, documents...) |
| `policy` | New business: draft -> quote -> bind -> cancel |
| `rating` | Weighted / multiplicative rate engine, rate tables, worksheets, Personal Auto plan |
| `billing` | BillingCenter: installment invoices, bill, apply payment |
| `workflow` | Generic process engine: steps, transitions, guards, instance history |
| `claim` | ClaimCenter: FNOL, reserves, approve, pay, close / deny |
| `app` | Composition root and demo: submission workflow + rating + FNOL |

### Module dependencies

Arrows mean **depends on** (sbt `dependsOn`). `core` is the shared foundation; `app` is the composition root.

```mermaid
flowchart TB
  subgraph foundation["Foundation"]
    core["core"]
  end

  subgraph shared["Shared"]
    rules["rules"]
    validation["validation"]
    plugins["plugins"]
    billing["billing"]
  end

  subgraph domain["Domain"]
    policy["policy"]
    workflow["workflow"]
    rating["rating"]
    claim["claim"]
  end

  subgraph runtime["Runtime"]
    app["app"]
  end

  rules --> core
  validation --> core
  plugins --> core
  billing --> core

  policy --> core
  policy --> rules
  workflow --> core
  workflow --> rules
  rating --> core
  rating --> plugins
  rating --> policy
  claim --> core
  claim --> rules
  claim --> validation
  claim --> workflow
  claim --> plugins

  app --> core
  app --> rules
  app --> validation
  app --> plugins
  app --> policy
  app --> rating
  app --> billing
  app --> workflow
  app --> claim
```

## Rules and dictionaries

Business rules and rate dictionaries live in one classpath YAML file. Domain classes (`PolicyRules`, `ClaimRules`, `PersonalAutoRatePlan`) only compile it; they do not hardcode the catalog.

| | |
|--|--|
| File | `modules/rules/src/main/resources/oie-rules.yaml` |
| Loader | `RuleCatalog` (`modules/rules`) |
| Rate book | `RateBook` compiles the `rating` section into tables and plans |

**Top-level YAML keys**

| Key | What it drives |
|-----|----------------|
| `underwriting` | UW `RuleSet` (demo: `young-driver` referral if `driverAge` &lt; 21) |
| `fnol` | FNOL `RuleSet` (policy in force, reserve vs limit, high-severity refer) |
| `claimValidation` | Field checks before FNOL (`nonBlank`, `notInFuture`) |
| `rating` | Rate tables (`age`, `region`, …) and plans (`PA-WEIGHTED-PL-2026`, `PA-MULT-PL-2026`) |

Add a rule by appending an entry under `underwriting.rules` or `fnol.rules` (`action`: `reject` / `refer`, `when.field` + `op` + `value` or `otherField`). Add a tariff band under `rating.tables`. Available facts and `when.op` values are listed in the comments at the top of the YAML file.

## Requirements

- JDK 17+
- sbt 1.10+

## Running demo app

The demo binds a Personal Auto policy through the submission workflow (`draft -> rate -> underwrite -> quote -> bind`), then opens a collision FNOL and settles it (`open -> reserve -> approve -> pay -> close`).

```bash
sbt "app/run --demo"
```

```
[info] Starting demo scenario on 2026-08-14...
[info] 20:55:56.445 [io-compute-11] INFO  c.g.b.openinsuranceengine.app.Main - Insured: age=23, licensed=3y, claims=1, credit=Standard, region=PL-MZ
[info] 20:55:56.449 [io-compute-11] INFO  c.g.b.openinsuranceengine.app.Main - === New-business workflow: New Business Submission ===
[info] 20:55:56.456 [io-compute-11] INFO  c.g.b.openinsuranceengine.app.Main - Workflow step: draft
[info] 20:55:56.462 [io-compute-11] INFO  c.g.b.openinsuranceengine.app.Main - Workflow step: rate
[info] 20:55:56.524 [io-compute-11] INFO  c.g.b.openinsuranceengine.app.Main - Workflow step: underwrite
[info] 20:55:56.544 [io-compute-11] INFO  c.g.b.openinsuranceengine.app.Main - Workflow step: quote
[info] 20:55:56.545 [io-compute-11] INFO  c.g.b.openinsuranceengine.app.Main - Workflow step: bind
[info] Created Account: 4a68c77c-5140-4f8e-a4f8-118d4158bc3f, Party: 347b30f5-9192-4d7e-9891-678be9c58196
[info] Product ID: 7a0f1f16-12b9-41fc-9723-c7431dde63c1, Policy ID: 40571cf1-ab7d-4659-94e7-36fe6c236756
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
[info] Workflow: Completed via draft -> rate -> underwrite -> quote -> bind
[info] Policy: status=InForce number=POL-40571CF1
[info] 20:55:56.592 [io-compute-11] INFO  c.g.b.openinsuranceengine.app.Main - === First notice of loss ===
[info] Claim: CLM-D889D119 status=Closed paid=7500,00 PLN
[info] Services container available: Services
[info] Finished!
[success] Total time: 3 s, completed 14 sie 2026, 20:55:56
```

### What the demo does

The demo replays one end-to-end customer story: **new Personal Auto policy -> rating -> bind -> collision claim and payout**. Everything runs in memory (in-memory repositories); there is no database and no network I/O.

There are two phases: **new business** (workflow), then **FNOL** (claim). Identifiers (`Account`, `Party`, `Product`, `Policy`, claim number) are random UUIDs, so they change on every run. Premium math is deterministic for this fixture.

The `Services` container also holds `BillingService` and a registered `PolicyRatingPlugin`, but **this run does not call billing**. Rating is invoked directly via `PolicyRatingPlugin.rateWithWorksheets`, not via `plugins.executeAll`.

#### 0. The submission being rated

Before the workflow starts, `DemoScenario` builds a synthetic application:

- **Insured**: age 23, 3 years licensed, 1 claim in the last 3 years, credit band `Standard`, region `PL-MZ` (Mazowieckie / Warsaw)
- **Risk**: Volkswagen Golf 2020, 15 000 km/year, plate `WA12345`
- **Coverage**: `BI` (bodily injury), limit 1 000 000 PLN, deductible 500 PLN, **base tariff 1 000 PLN**
- **Term**: from today for 1 year, line of business Personal Auto, job type `Submission`

#### 1. New-business workflow: `draft -> rate -> underwrite -> quote -> bind`

`WorkflowEngine` starts the `New Business Submission` definition with a `SubmissionState` payload (policy period + insured profile; worksheets are filled later). `start` does **not** execute the first step — it only sets `currentStepId = draft`. `runUntilDone` then calls `advance` until the instance status is `Completed`.

**`draft`.** `PolicyService.createDraft` checks that at least one coverage exists and that Personal Auto has a `VehicleRisk`. The period is saved with status `Draft`.

**`rate`.** The engine applies plan `Personal Auto Weighted (PL 2026)` on top of the 1 000 PLN base. Combination mode is **weighted average**, not a product of factors.

| Factor | Input | Band | Factor | Weight |
|--------|-------|------|--------|--------|
| AGE | 23 | Youth (21-24) | 1.45 | 2.50 |
| EXPERIENCE | 3 | Junior (2-4y) | 1.20 | 1.50 |
| CLAIMS | 1 | 1 claim | 1.15 | 2.00 |
| CREDIT | Standard | Standard | 1.00 | 1.20 |
| REGION | PL-MZ | Mazowieckie (Warsaw) | 1.25 | 1.00 |
| MILEAGE | 15000 | High (15-25k) | 1.18 | 1.00 |
| VEHICLE_AGE | 6 (year − 2020) | Mid (3-7y) | 1.00 | 0.80 |

Formula: `Σ(factor × weight) / Σ(weights)` = `12.155 / 10.00` = **1.2155**.  
Premium: `1000 × 1.2155` = **1 215.50 PLN**. Youth, one prior claim, and Warsaw load the rate; vehicle age is neutral.

**`underwrite`.** Rule `young-driver` refers to UW only when age **&lt; 21**. This insured is 23, so `referred = false` and the transition goes to `quote`, not `refer`.

**`quote`.** Status `Draft` -> `Quoted` (offer, still no policy number).

**`bind`.** Status `Quoted` -> `InForce`, policy number `POL-` plus the first 8 hex chars of the policy UUID (e.g. `POL-40571CF1`). The policy is now in force and can accept a claim.

The line `Workflow: Completed via draft -> rate -> underwrite -> quote -> bind` is the instance step history.

#### 2. FNOL: a loss on the bound policy

`=== First notice of loss ===` opens a Golf rear-end collision in Warsaw. Loss date is today, tier `Medium`, claimant is the same `Party`.

`ClaimService` then runs:

1. **`openFnol`** — validation (non-blank description, loss date not in the future) plus FNOL rules: the policy **must be InForce** (it is), and reserves must not exceed the 1 000 000 PLN limit. Medium tier -> status `Open`, claim number `CLM-` plus 8 hex chars.
2. **`setReserve`** — `vehicle_damage` reserve of **8 500 PLN** -> `Reserved`.
3. **`approve`** -> `Approved`.
4. **`pay`** — indemnity **7 500 PLN** to the insured, reference `IND-GOLF-001` -> `Paid`.
5. **`close`** -> `Closed`.

Hence: `Claim: CLM-D889D119 status=Closed paid=7500,00 PLN`. Reserve 8 500 vs payment 7 500 is intentional (estimate vs actual indemnity).

#### Paths this fixture does not take

- Billing (installment invoices / cash application) is wired in `Services` but unused.
- The `refer` branch (driver younger than 21) does not fire here.
- The registered rating plugin is not executed through the plugin registry; the workflow calls rating directly.

In one sentence: **a young Warsaw driver with one prior claim is charged 1 215.50 PLN instead of 1 000, the policy goes in force, then a collision claim is opened, reserved, approved, paid (7 500 PLN), and closed.**

## Running unit tests

```bash
sbt test

...

[info] Passed: Total 180, Failed 0, Errors 0, Passed 68
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
