package com.github.bieli.openinsuranceengine.rules

import java.nio.charset.StandardCharsets
import scala.util.Using

import io.circe.{Decoder, HCursor, Json}
import io.circe.yaml.parser

/**
 * Declarative business catalog loaded from YAML. Domain modules compile these
 * entries into `Rule` / `RuleSet` / rate tables - they do not hardcode the rules.
 */
enum Fact:
  case Num(n: BigDecimal)
  case Bool(b: Boolean)
  case Text(s: String)
  case Missing

object Fact:
  given CanEqual[Fact, Fact] = CanEqual.derived
  def num(n: Int): Fact = Num(BigDecimal(n))
  def num(n: Long): Fact = Num(BigDecimal(n))
  def num(n: BigDecimal): Fact = Num(n)
  def fromOptionNum(o: Option[Int]): Fact = o.map(num).getOrElse(Missing)
  def fromOptionBig(o: Option[BigDecimal]): Fact = o.map(Num.apply).getOrElse(Missing)

  def display(fact: Fact): String = fact match
    case Num(n)    => n.bigDecimal.stripTrailingZeros.toPlainString
    case Bool(b)   => b.toString
    case Text(s)   => s
    case Missing   => "?"

final case class WhenClause(
    field: String,
    op: String,
    value: Option[Json] = None,
    otherField: Option[String] = None
)

final case class DeclaredRule(
    id: String,
    name: String,
    priority: Int,
    action: String,
    when: WhenClause,
    reason: String,
    enabled: Boolean = true
)

final case class DeclaredRuleSet(
    id: String,
    name: String,
    rules: List[DeclaredRule]
)

final case class DeclaredCheck(
    id: String,
    field: String,
    check: String,
    message: String
)

final case class CatalogBand(
    code: Option[String],
    label: String,
    min: Option[BigDecimal],
    max: Option[BigDecimal],
    factor: BigDecimal
)

final case class CatalogTable(
    id: String,
    name: String,
    kind: String,
    bands: List[CatalogBand]
)

final case class CatalogFactor(
    code: String,
    name: String,
    weight: BigDecimal,
    table: String,
    source: String,
    fallback: Option[String],
    defaultValue: Option[BigDecimal]
)

final case class CatalogPlan(
    id: String,
    name: String,
    mode: String,
    factors: List[CatalogFactor]
)

final case class RatingSection(
    defaultBaseRateMajor: BigDecimal,
    currency: String,
    tables: List[CatalogTable],
    plans: List[CatalogPlan]
)

final case class CatalogDocument(
    underwriting: DeclaredRuleSet,
    fnol: DeclaredRuleSet,
    claimValidation: List[DeclaredCheck],
    rating: RatingSection
)

object RuleCatalog:
  val ResourceName = "oie-rules.yaml"

  lazy val document: CatalogDocument = load()

  def load(resource: String = ResourceName): CatalogDocument =
    parse(readResource(resource))

  def parse(yaml: String): CatalogDocument =
    parser
      .parse(yaml)
      .flatMap(_.as[CatalogDocument])
      .fold(
        err => throw new IllegalStateException(s"Failed to parse $ResourceName: ${err.getMessage}", err),
        identity
      )

  def compile[C](set: DeclaredRuleSet, factsOf: C => Map[String, Fact]): RuleSet[C] =
    RuleSet(set.id, set.name, set.rules.map(compileRule(_, factsOf)))

  def interpolate(template: String, facts: Map[String, Fact]): String =
    "\\{([A-Za-z0-9_]+)\\}".r.replaceAllIn(
      template,
      m => java.util.regex.Matcher.quoteReplacement(Fact.display(facts.getOrElse(m.group(1), Fact.Missing)))
    )

  def matches(clause: WhenClause, facts: Map[String, Fact]): Boolean =
    val left = facts.getOrElse(clause.field, Fact.Missing)
    clause.otherField match
      case Some(other) =>
        compareFacts(clause.op, left, facts.getOrElse(other, Fact.Missing))
      case None =>
        compareToLiteral(clause.op, left, clause.value)

  private def compileRule[C](declared: DeclaredRule, factsOf: C => Map[String, Fact]): Rule[C] =
    val predicate = (ctx: C) => matches(declared.when, factsOf(ctx))
    val reasonFn = (ctx: C) => interpolate(declared.reason, factsOf(ctx))
    declared.action.trim.toLowerCase match
      case "reject" =>
        Rule.rejectWhen(declared.id, declared.name, declared.priority, predicate, reasonFn, declared.enabled)
      case "refer" =>
        Rule.referWhen(declared.id, declared.name, declared.priority, predicate, reasonFn, declared.enabled)
      case other =>
        throw new IllegalStateException(s"Unsupported rule action '$other' on rule '${declared.id}'")

  private def compareFacts(op: String, left: Fact, right: Fact): Boolean =
    (left, right) match
      case (Fact.Missing, _) | (_, Fact.Missing) => false
      case (Fact.Num(a), Fact.Num(b))            => numericOp(op, a, b)
      case (Fact.Bool(a), Fact.Bool(b))          => equalityOp(op, a == b)
      case (Fact.Text(a), Fact.Text(b))          => equalityOp(op, a.equalsIgnoreCase(b))
      case _                                     => false

  private def compareToLiteral(op: String, left: Fact, literal: Option[Json]): Boolean =
    val opNorm = op.trim.toLowerCase
    opNorm match
      case "istrue" =>
        left match
          case Fact.Bool(true) => true
          case _               => false
      case "isfalse" =>
        left match
          case Fact.Bool(false) => true
          case _                => false
      case "in" =>
        left match
          case Fact.Text(s) => inList(literal).exists(_.equalsIgnoreCase(s))
          case Fact.Num(n)  => inList(literal).exists(v => numericEquals(v, n))
          case _            => false
      case _ =>
        literal match
          case None => false
          case Some(json) =>
            left match
              case Fact.Missing => false
              case Fact.Num(n) =>
                json.asNumber.flatMap(_.toBigDecimal).exists(v => numericOp(opNorm, n, v))
                  || json.asString.exists(s => numericOp(opNorm, n, BigDecimal(s)))
              case Fact.Bool(b) =>
                json.asBoolean.exists(v => equalityOp(opNorm, b == v))
              case Fact.Text(s) =>
                json.asString.exists(v => equalityOp(opNorm, s.equalsIgnoreCase(v)))

  private def numericOp(op: String, left: BigDecimal, right: BigDecimal): Boolean =
    val c = left.compare(right)
    op.trim.toLowerCase match
      case "lt"  => c < 0
      case "lte" => c <= 0
      case "gt"  => c > 0
      case "gte" => c >= 0
      case "eq"  => c == 0
      case "neq" => c != 0
      case _     => false

  private def equalityOp(op: String, equal: Boolean): Boolean =
    op.trim.toLowerCase match
      case "eq"  => equal
      case "neq" => !equal
      case _     => false

  private def inList(literal: Option[Json]): List[String] =
    literal match
      case None => Nil
      case Some(json) if json.isArray =>
        json.asArray.toList.flatten.flatMap: item =>
          item.asString.orElse(item.asNumber.flatMap(_.toBigDecimal).map(_.toString))
      case Some(json) =>
        json.asString.toList.flatMap(_.split(",").map(_.trim).toList)

  private def numericEquals(raw: String, n: BigDecimal): Boolean =
    try n.compare(BigDecimal(raw)) == 0
    catch case _: NumberFormatException => false

  private def readResource(name: String): String =
    val loader = Option(Thread.currentThread().getContextClassLoader).getOrElse(getClass.getClassLoader)
    val in =
      Option(loader.getResourceAsStream(name))
        .orElse(Option(getClass.getResourceAsStream(s"/$name")))
        .getOrElse(throw new IllegalStateException(s"Missing classpath resource: $name"))
    Using.resource(in): stream =>
      new String(stream.readAllBytes(), StandardCharsets.UTF_8)

  private given Decoder[WhenClause] = Decoder.instance: (c: HCursor) =>
    for
      field <- c.get[String]("field")
      op <- c.get[String]("op")
    yield
      val value = c.downField("value").focus.filterNot(_.isNull)
      val other = c.get[Option[String]]("otherField").toOption.flatten
      WhenClause(field, op, value, other)

  private given Decoder[DeclaredRule] = Decoder.instance: (c: HCursor) =>
    for
      id <- c.get[String]("id")
      name <- c.get[String]("name")
      priority <- c.get[Int]("priority")
      action <- c.get[String]("action")
      when <- c.get[WhenClause]("when")
      reason <- c.get[String]("reason")
      enabled <- c.getOrElse[Boolean]("enabled")(true)
    yield DeclaredRule(id, name, priority, action, when, reason, enabled)

  private given Decoder[DeclaredRuleSet] = Decoder.instance: (c: HCursor) =>
    for
      id <- c.get[String]("id")
      name <- c.get[String]("name")
      rules <- c.get[List[DeclaredRule]]("rules")
    yield DeclaredRuleSet(id, name, rules)

  private given Decoder[DeclaredCheck] = Decoder.instance: (c: HCursor) =>
    for
      id <- c.get[String]("id")
      field <- c.get[String]("field")
      check <- c.get[String]("check")
      message <- c.get[String]("message")
    yield DeclaredCheck(id, field, check, message)

  private given Decoder[CatalogBand] = Decoder.instance: (c: HCursor) =>
    for
      label <- c.get[String]("label")
      factor <- c.get[BigDecimal]("factor")
    yield CatalogBand(
      code = c.get[Option[String]]("code").toOption.flatten,
      label = label,
      min = c.get[Option[BigDecimal]]("min").toOption.flatten,
      max = c.get[Option[BigDecimal]]("max").toOption.flatten,
      factor = factor
    )

  private given Decoder[CatalogTable] = Decoder.instance: (c: HCursor) =>
    for
      id <- c.get[String]("id")
      name <- c.get[String]("name")
      kind <- c.getOrElse[String]("kind")("numeric")
      bands <- c.get[List[CatalogBand]]("bands")
    yield CatalogTable(id, name, kind, bands)

  private given Decoder[CatalogFactor] = Decoder.instance: (c: HCursor) =>
    for
      code <- c.get[String]("code")
      name <- c.get[String]("name")
      weight <- c.get[BigDecimal]("weight")
      table <- c.get[String]("table")
      source <- c.get[String]("source")
    yield CatalogFactor(
      code = code,
      name = name,
      weight = weight,
      table = table,
      source = source,
      fallback = c.get[Option[String]]("fallback").toOption.flatten,
      defaultValue = c.get[Option[BigDecimal]]("default").toOption.flatten
    )

  private given Decoder[CatalogPlan] = Decoder.instance: (c: HCursor) =>
    for
      id <- c.get[String]("id")
      name <- c.get[String]("name")
      mode <- c.get[String]("mode")
      factors <- c.get[List[CatalogFactor]]("factors")
    yield CatalogPlan(id, name, mode, factors)

  private given Decoder[RatingSection] = Decoder.instance: (c: HCursor) =>
    for
      defaultBaseRateMajor <- c.getOrElse[BigDecimal]("defaultBaseRateMajor")(BigDecimal(1000))
      currency <- c.getOrElse[String]("currency")("PLN")
      tables <- c.get[List[CatalogTable]]("tables")
      plans <- c.get[List[CatalogPlan]]("plans")
    yield RatingSection(defaultBaseRateMajor, currency, tables, plans)

  private given Decoder[CatalogDocument] = Decoder.instance: (c: HCursor) =>
    for
      underwriting <- c.get[DeclaredRuleSet]("underwriting")
      fnol <- c.get[DeclaredRuleSet]("fnol")
      claimValidation <- c.getOrElse[List[DeclaredCheck]]("claimValidation")(Nil)
      rating <- c.get[RatingSection]("rating")
    yield CatalogDocument(underwriting, fnol, claimValidation, rating)
