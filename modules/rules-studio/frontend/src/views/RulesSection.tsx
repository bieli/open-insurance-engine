import type { DeclaredCheck, DeclaredRule, DeclaredRuleSet } from "../catalog/types";
import { ACTION_LABELS, ACTIONS, CHECKS, OP_LABELS, OPS, emptyCheck, emptyRule, ruleSummary } from "../catalog/types";
import Button, { Creator } from "@synerise/ds-button";
import Card from "@synerise/ds-card";
import { Input, TextArea } from "@synerise/ds-input";
import Select from "@synerise/ds-select";

type Props = {
  title: string;
  description: string;
  fields: readonly string[];
  set: DeclaredRuleSet;
  onChange: (next: DeclaredRuleSet) => void;
};

const selectOpts = (values: readonly string[], labels?: Record<string, string>) =>
  values.map((value) => ({ value, label: labels?.[value] ?? value }));

function RuleCard({
  rule,
  fields,
  onChange,
  onRemove
}: {
  rule: DeclaredRule;
  fields: readonly string[];
  onChange: (next: DeclaredRule) => void;
  onRemove: () => void;
}) {
  const usesOther = Boolean(rule.when.otherField);
  return (
    <Card
      lively
      withHeader
      headerBorderBottom
      title={rule.name || rule.id}
      description={ruleSummary(rule)}
      headerSideChildren={
        <Button type="ghost" onClick={onRemove}>
          Remove
        </Button>
      }
    >
      <div className="grid">
        <Input label="Id" value={rule.id} onChange={(e) => onChange({ ...rule, id: e.target.value })} />
        <Input label="Name" value={rule.name} onChange={(e) => onChange({ ...rule, name: e.target.value })} />
        <Input
          label="Priority"
          type="number"
          value={String(rule.priority)}
          onChange={(e) => onChange({ ...rule, priority: Number(e.target.value) })}
        />
        <Select
          label="Action"
          value={rule.action}
          options={selectOpts(ACTIONS, ACTION_LABELS)}
          onChange={(value) => onChange({ ...rule, action: String(value ?? "refer") })}
        />
        <Select
          label="When field"
          value={rule.when.field}
          options={selectOpts(fields)}
          onChange={(value) => onChange({ ...rule, when: { ...rule.when, field: String(value ?? "") } })}
        />
        <Select
          label="Operator"
          value={rule.when.op}
          options={selectOpts(OPS, OP_LABELS)}
          onChange={(value) => onChange({ ...rule, when: { ...rule.when, op: String(value ?? "eq") } })}
        />
        <Input
          label="Value (or comma list for in)"
          disabled={usesOther || rule.when.op === "isTrue" || rule.when.op === "isFalse"}
          value={Array.isArray(rule.when.value) ? rule.when.value.join(", ") : String(rule.when.value ?? "")}
          onChange={(e) => onChange({ ...rule, when: { ...rule.when, value: e.target.value, otherField: undefined } })}
        />
        <Select
          label="Or compare to another field"
          allowClear
          value={rule.when.otherField}
          options={selectOpts(fields)}
          onChange={(value) =>
            onChange({
              ...rule,
              when: { ...rule.when, otherField: value ? String(value) : undefined }
            })
          }
        />
        <div className="span-2">
          <TextArea
            label="Reason (use {field} placeholders)"
            rows={2}
            value={rule.reason}
            onChange={(e) => onChange({ ...rule, reason: e.target.value })}
          />
        </div>
      </div>
    </Card>
  );
}

export function RulesSection({ title, description, fields, set, onChange }: Props) {
  const prefix = title.toLowerCase().includes("fnol") ? "CLM" : "UW";
  return (
    <div className="stack">
      <p className="lede">{description}</p>
      <div className="grid">
        <Input label="Rule set id" value={set.id} onChange={(e) => onChange({ ...set, id: e.target.value })} />
        <Input label="Rule set name" value={set.name} onChange={(e) => onChange({ ...set, name: e.target.value })} />
      </div>
      {set.rules.map((rule, index) => (
        <RuleCard
          key={`${rule.id}-${index}`}
          rule={rule}
          fields={fields}
          onChange={(next) =>
            onChange({ ...set, rules: set.rules.map((r, i) => (i === index ? next : r)) })
          }
          onRemove={() => onChange({ ...set, rules: set.rules.filter((_, i) => i !== index) })}
        />
      ))}
      <Creator block label={`Add ${title.toLowerCase()} rule`} onClick={() => onChange({ ...set, rules: [...set.rules, emptyRule(prefix)] })} />
    </div>
  );
}

export function ValidationSection({
  checks,
  onChange
}: {
  checks: DeclaredCheck[];
  onChange: (next: DeclaredCheck[]) => void;
}) {
  return (
    <div className="stack">
      <p className="lede">Field checks run before FNOL. Keep them short and specific - they become validation errors on the claim.</p>
      {checks.map((check, index) => (
        <Card
          key={`${check.id}-${index}`}
          lively
          withHeader
          title={check.id}
          description={`${check.check} on ${check.field}`}
          headerSideChildren={
            <Button type="ghost" onClick={() => onChange(checks.filter((_, i) => i !== index))}>
              Remove
            </Button>
          }
        >
          <div className="grid">
            <Input
              label="Id"
              value={check.id}
              onChange={(e) => onChange(checks.map((c, i) => (i === index ? { ...c, id: e.target.value } : c)))}
            />
            <Input
              label="Field"
              value={check.field}
              onChange={(e) => onChange(checks.map((c, i) => (i === index ? { ...c, field: e.target.value } : c)))}
            />
            <Select
              label="Check"
              value={check.check}
              options={selectOpts(CHECKS)}
              onChange={(value) =>
                onChange(checks.map((c, i) => (i === index ? { ...c, check: String(value ?? "nonBlank") } : c)))
              }
            />
            <Input
              label="Message"
              value={check.message}
              onChange={(e) => onChange(checks.map((c, i) => (i === index ? { ...c, message: e.target.value } : c)))}
            />
          </div>
        </Card>
      ))}
      <Creator block label="Add field check" onClick={() => onChange([...checks, emptyCheck()])} />
    </div>
  );
}

