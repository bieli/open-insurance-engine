import Button, { Creator } from "@synerise/ds-button";
import Card from "@synerise/ds-card";
import { Input } from "@synerise/ds-input";
import Select from "@synerise/ds-select";
import type { CatalogBand, CatalogTable, RatingSection } from "../catalog/types";
import { TABLE_KINDS, emptyBand, emptyTable } from "../catalog/types";

const selectOpts = (values: readonly string[]) => values.map((value) => ({ value, label: value }));

export function TablesSection({
  rating,
  onChange
}: {
  rating: RatingSection;
  onChange: (next: RatingSection) => void;
}) {
  const updateTable = (index: number, table: CatalogTable) =>
    onChange({ ...rating, tables: rating.tables.map((t, i) => (i === index ? table : t)) });

  return (
    <div className="stack">
      <p className="lede">
        Rate dictionaries map an input (age, region, mileage) onto a factor. Numeric bands use inclusive min and exclusive max.
        Categorical bands look up by code.
      </p>
      <div className="grid">
        <Input
          label="Default base rate (major units)"
          type="number"
          value={String(rating.defaultBaseRateMajor)}
          onChange={(e) => onChange({ ...rating, defaultBaseRateMajor: Number(e.target.value) })}
        />
        <Input
          label="Currency"
          value={rating.currency}
          onChange={(e) => onChange({ ...rating, currency: e.target.value })}
        />
      </div>
      {rating.tables.map((table, ti) => (
        <Card
          key={`${table.id}-${ti}`}
          lively
          withHeader
          headerBorderBottom
          title={table.name || table.id}
          description={`${table.kind} · ${table.bands.length} bands`}
          headerSideChildren={
            <Button type="ghost" onClick={() => onChange({ ...rating, tables: rating.tables.filter((_, i) => i !== ti) })}>
              Remove table
            </Button>
          }
        >
          <div className="grid">
            <Input label="Table id" value={table.id} onChange={(e) => updateTable(ti, { ...table, id: e.target.value })} />
            <Input label="Name" value={table.name} onChange={(e) => updateTable(ti, { ...table, name: e.target.value })} />
            <Select
              label="Kind"
              value={table.kind}
              options={selectOpts(TABLE_KINDS)}
              onChange={(value) => updateTable(ti, { ...table, kind: String(value ?? "numeric") })}
            />
          </div>
          <div className="band-list">
            {table.bands.map((band, bi) => (
              <BandRow
                key={bi}
                kind={table.kind}
                band={band}
                onChange={(next) =>
                  updateTable(ti, { ...table, bands: table.bands.map((b, i) => (i === bi ? next : b)) })
                }
                onRemove={() => updateTable(ti, { ...table, bands: table.bands.filter((_, i) => i !== bi) })}
              />
            ))}
          </div>
          <Creator
            block
            label="Add band"
            onClick={() => updateTable(ti, { ...table, bands: [...table.bands, emptyBand(table.kind)] })}
          />
        </Card>
      ))}
      <Creator block label="Add rate table" onClick={() => onChange({ ...rating, tables: [...rating.tables, emptyTable()] })} />
    </div>
  );
}

function BandRow({
  kind,
  band,
  onChange,
  onRemove
}: {
  kind: string;
  band: CatalogBand;
  onChange: (next: CatalogBand) => void;
  onRemove: () => void;
}) {
  const num = (raw: string): number | undefined => (raw === "" ? undefined : Number(raw));
  return (
    <div className="band-row">
      {kind === "categorical" ? (
        <Input label="Code" value={band.code ?? ""} onChange={(e) => onChange({ ...band, code: e.target.value })} />
      ) : (
        <>
          <Input
            label="Min (inclusive)"
            type="number"
            value={band.min === undefined ? "" : String(band.min)}
            onChange={(e) => onChange({ ...band, min: num(e.target.value) })}
          />
          <Input
            label="Max (exclusive)"
            type="number"
            value={band.max === undefined ? "" : String(band.max)}
            onChange={(e) => onChange({ ...band, max: num(e.target.value) })}
          />
        </>
      )}
      <Input label="Label" value={band.label} onChange={(e) => onChange({ ...band, label: e.target.value })} />
      <Input
        label="Factor"
        type="number"
        step="0.01"
        value={String(band.factor)}
        onChange={(e) => onChange({ ...band, factor: Number(e.target.value) })}
      />
      <Button type="ghost" onClick={onRemove}>
        Remove
      </Button>
    </div>
  );
}
