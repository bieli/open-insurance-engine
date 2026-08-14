export type WhenClause = {
  field: string;
  op: string;
  value?: unknown;
  otherField?: string;
};

export type DeclaredRule = {
  id: string;
  name: string;
  priority: number;
  action: string;
  when: WhenClause;
  reason: string;
  enabled?: boolean;
};

export type DeclaredRuleSet = {
  id: string;
  name: string;
  rules: DeclaredRule[];
};

export type DeclaredCheck = {
  id: string;
  field: string;
  check: string;
  message: string;
};

export type CatalogBand = {
  code?: string;
  label: string;
  min?: number;
  max?: number;
  factor: number;
};

export type CatalogTable = {
  id: string;
  name: string;
  kind: string;
  bands: CatalogBand[];
};

export type CatalogFactor = {
  code: string;
  name: string;
  weight: number;
  table: string;
  source: string;
  fallback?: string;
  default?: number;
};

export type CatalogPlan = {
  id: string;
  name: string;
  mode: string;
  factors: CatalogFactor[];
};

export type RatingSection = {
  defaultBaseRateMajor: number;
  currency: string;
  tables: CatalogTable[];
  plans: CatalogPlan[];
};

export type CatalogDocument = {
  underwriting: DeclaredRuleSet;
  fnol: DeclaredRuleSet;
  claimValidation: DeclaredCheck[];
  rating: RatingSection;
};

export const UW_FIELDS = ["driverAge", "lineOfBusiness", "coverageCount", "hasVehicle"] as const;
export const FNOL_FIELDS = ["policyInForce", "totalReserves", "coverageLimit", "tier"] as const;
export const OPS = ["lt", "lte", "gt", "gte", "eq", "neq", "in", "isTrue", "isFalse"] as const;
export const OP_LABELS: Record<(typeof OPS)[number], string> = {
  lt: "less than (<)",
  lte: "less or equal (≤)",
  gt: "greater than (>)",
  gte: "greater or equal (≥)",
  eq: "equals",
  neq: "not equal",
  in: "in list",
  isTrue: "is true",
  isFalse: "is false"
};
export const ACTIONS = ["reject", "refer"] as const;
export const ACTION_LABELS: Record<(typeof ACTIONS)[number], string> = {
  reject: "reject",
  refer: "refer to UW"
};
export const CHECKS = ["nonBlank", "notInFuture"] as const;
export const TABLE_KINDS = ["numeric", "categorical"] as const;
export const PLAN_MODES = ["WeightedAverage", "Multiplicative"] as const;
export const RATING_SOURCES = [
  "profile.age",
  "profile.yearsLicensed",
  "profile.priorClaimsLast3Years",
  "profile.creditBand",
  "profile.regionCode",
  "vehicle.annualMileage",
  "vehicle.ageYears"
] as const;

export function emptyRule(prefix: string): DeclaredRule {
  return {
    id: `${prefix}-${Date.now().toString(36)}`,
    name: "New rule",
    priority: 10,
    action: "refer",
    when: { field: "", op: "eq", value: "" },
    reason: ""
  };
}

export function emptyCheck(): DeclaredCheck {
  return {
    id: `CHK-${Date.now().toString(36)}`,
    field: "description",
    check: "nonBlank",
    message: "Field must not be blank"
  };
}

export function emptyBand(kind: string): CatalogBand {
  return kind === "categorical"
    ? { code: "NEW", label: "New band", factor: 1 }
    : { label: "New band", factor: 1 };
}

export function emptyTable(): CatalogTable {
  return { id: `table-${Date.now().toString(36)}`, name: "New table", kind: "numeric", bands: [emptyBand("numeric")] };
}

export function emptyFactor(): CatalogFactor {
  return { code: "NEW", name: "New factor", weight: 1, table: "", source: "profile.age" };
}

export function emptyPlan(): CatalogPlan {
  return { id: `plan-${Date.now().toString(36)}`, name: "New plan", mode: "WeightedAverage", factors: [] };
}

export function ruleSummary(rule: DeclaredRule): string {
  const { field, op, value, otherField } = rule.when;
  const rhs = otherField ? otherField : value === undefined || value === "" ? "…" : String(value);
  return `When ${field || "?"} ${op} ${rhs} → ${rule.action}`;
}
