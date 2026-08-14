import Button, { Creator } from "@synerise/ds-button";
import Card from "@synerise/ds-card";
import { Input } from "@synerise/ds-input";
import Select from "@synerise/ds-select";
import type { CatalogFactor, CatalogPlan, RatingSection } from "../catalog/types";
import { PLAN_MODES, RATING_SOURCES, emptyFactor, emptyPlan } from "../catalog/types";

const selectOpts = (values: readonly string[]) => values.map((value) => ({ value, label: value }));

export function PlansSection({
  rating,
  onChange
}: {
  rating: RatingSection;
  onChange: (next: RatingSection) => void;
}) {
  const tableIds = rating.tables.map((t) => t.id);
  const updatePlan = (index: number, plan: CatalogPlan) =>
    onChange({ ...rating, plans: rating.plans.map((p, i) => (i === index ? plan : p)) });

  return (
    <div className="stack">
      <p className="lede">
        A plan is an ordered list of factors. Each factor reads a source, looks up a table, and contributes a weighted
        factor to the premium. WeightedAverage is what the demo uses.
      </p>
      {rating.plans.map((plan, pi) => (
        <Card
          key={`${plan.id}-${pi}`}
          lively
          withHeader
          headerBorderBottom
          title={plan.name || plan.id}
          description={`${plan.mode} · ${plan.factors.length} factors`}
          headerSideChildren={
            <Button type="ghost" onClick={() => onChange({ ...rating, plans: rating.plans.filter((_, i) => i !== pi) })}>
              Remove plan
            </Button>
          }
        >
          <div className="grid">
            <Input label="Plan id" value={plan.id} onChange={(e) => updatePlan(pi, { ...plan, id: e.target.value })} />
            <Input label="Name" value={plan.name} onChange={(e) => updatePlan(pi, { ...plan, name: e.target.value })} />
            <Select
              label="Mode"
              value={plan.mode}
              options={selectOpts(PLAN_MODES)}
              onChange={(value) => updatePlan(pi, { ...plan, mode: String(value ?? "WeightedAverage") })}
            />
          </div>
          {plan.factors.map((factor, fi) => (
            <FactorRow
              key={`${factor.code}-${fi}`}
              factor={factor}
              tableIds={tableIds}
              onChange={(next) =>
                updatePlan(pi, { ...plan, factors: plan.factors.map((f, i) => (i === fi ? next : f)) })
              }
              onRemove={() => updatePlan(pi, { ...plan, factors: plan.factors.filter((_, i) => i !== fi) })}
            />
          ))}
          <Creator
            block
            label="Add factor"
            onClick={() => updatePlan(pi, { ...plan, factors: [...plan.factors, emptyFactor()] })}
          />
        </Card>
      ))}
      <Creator block label="Add rate plan" onClick={() => onChange({ ...rating, plans: [...rating.plans, emptyPlan()] })} />
    </div>
  );
}

function FactorRow({
  factor,
  tableIds,
  onChange,
  onRemove
}: {
  factor: CatalogFactor;
  tableIds: string[];
  onChange: (next: CatalogFactor) => void;
  onRemove: () => void;
}) {
  return (
    <div className="band-row">
      <Input label="Code" value={factor.code} onChange={(e) => onChange({ ...factor, code: e.target.value })} />
      <Input label="Name" value={factor.name} onChange={(e) => onChange({ ...factor, name: e.target.value })} />
      <Input
        label="Weight"
        type="number"
        step="0.1"
        value={String(factor.weight)}
        onChange={(e) => onChange({ ...factor, weight: Number(e.target.value) })}
      />
      <Select
        label="Table"
        value={factor.table}
        options={selectOpts(tableIds)}
        onChange={(value) => onChange({ ...factor, table: String(value ?? "") })}
      />
      <Select
        label="Source"
        value={factor.source}
        options={selectOpts(RATING_SOURCES)}
        onChange={(value) => onChange({ ...factor, source: String(value ?? "") })}
      />
      <Input
        label="Fallback"
        value={factor.fallback ?? ""}
        onChange={(e) => onChange({ ...factor, fallback: e.target.value || undefined })}
      />
      <Input
        label="Default"
        type="number"
        value={factor.default === undefined ? "" : String(factor.default)}
        onChange={(e) =>
          onChange({ ...factor, default: e.target.value === "" ? undefined : Number(e.target.value) })
        }
      />
      <Button type="ghost" onClick={onRemove}>
        Remove
      </Button>
    </div>
  );
}
