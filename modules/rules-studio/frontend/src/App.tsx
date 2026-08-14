import { useEffect, useMemo, useState } from "react";
import DSProvider from "@synerise/ds-core/dist/js/DSProvider/DSProvider.js";
import Button from "@synerise/ds-button";
import Layout from "@synerise/ds-layout";
import Tabs from "@synerise/ds-tabs";
import { Title } from "@synerise/ds-typography";
import seedYaml from "./catalog/seed.yaml?raw";
import type { CatalogDocument } from "./catalog/types";
import { dumpCatalog, parseCatalog } from "./catalog/yaml";
import { FNOL_FIELDS, UW_FIELDS } from "./catalog/types";
import { PlansSection } from "./views/PlansSection";
import { RulesSection, ValidationSection } from "./views/RulesSection";
import { TablesSection } from "./views/TablesSection";
import { YamlPreview } from "./views/YamlPreview";
import "./styles.css";

const TABS = [
  { label: "Underwriting" },
  { label: "FNOL" },
  { label: "Claim checks" },
  { label: "Rate tables" },
  { label: "Rate plans" }
];

function loadSeed(): CatalogDocument {
  return parseCatalog(seedYaml);
}

export function App() {
  const [catalog, setCatalog] = useState<CatalogDocument>(loadSeed);
  const [tab, setTab] = useState(0);
  const [yamlOpen, setYamlOpen] = useState(true);
  const yaml = useMemo(() => dumpCatalog(catalog), [catalog]);

  useEffect(() => {
    fetch("/api/catalog.yaml")
      .then((r) => (r.ok ? r.text() : Promise.reject()))
      .then((text) => setCatalog(parseCatalog(text)))
      .catch(() => undefined);
  }, []);

  const reset = () => setCatalog(loadSeed());

  return (
    <DSProvider locale="en-US" toasterProps={false}>
      <div className={`studio-shell ${yamlOpen ? "yaml-open" : "yaml-closed"}`}>
        <Layout
          className="studio-layout"
          fillViewport
          viewportTopOffset={0}
          nativeScroll
          header={
            <header className="studio-header">
              <div>
                <Title level={2}>Open Insurance Engine - Rules Studio</Title>
                <p className="muted">Edit dictionaries visually, then download YAML for the engine catalog.</p>
              </div>
              <div className="header-actions">
                <Button type="ghost" onClick={reset}>
                  Reset to seed
                </Button>
                <Button type="secondary" onClick={() => setYamlOpen((v) => !v)}>
                  {yamlOpen ? "Hide YAML" : "Show YAML"}
                </Button>
              </div>
            </header>
          }
        >
          <Tabs tabs={TABS} activeTab={tab} handleTabClick={setTab} block underscore />
          <div className="workspace">
            {tab === 0 && (
              <RulesSection
                title="Underwriting"
                description="These rules run after rating. Refer sends the submission to the UW desk; reject stops the job."
                fields={UW_FIELDS}
                set={catalog.underwriting}
                onChange={(underwriting) => setCatalog({ ...catalog, underwriting })}
              />
            )}
            {tab === 1 && (
              <RulesSection
                title="FNOL"
                description="First Notice of Loss gates. Compare facts such as policyInForce or totalReserves vs coverageLimit."
                fields={FNOL_FIELDS}
                set={catalog.fnol}
                onChange={(fnol) => setCatalog({ ...catalog, fnol })}
              />
            )}
            {tab === 2 && (
              <ValidationSection
                checks={catalog.claimValidation}
                onChange={(claimValidation) => setCatalog({ ...catalog, claimValidation })}
              />
            )}
            {tab === 3 && (
              <TablesSection rating={catalog.rating} onChange={(rating) => setCatalog({ ...catalog, rating })} />
            )}
            {tab === 4 && (
              <PlansSection rating={catalog.rating} onChange={(rating) => setCatalog({ ...catalog, rating })} />
            )}
          </div>
        </Layout>
        {yamlOpen ? (
          <aside className="yaml-aside" aria-label="YAML preview">
            <YamlPreview yaml={yaml} />
          </aside>
        ) : null}
      </div>
    </DSProvider>
  );
}
