import { useMemo, useState } from "react";
import Button from "@synerise/ds-button";
import { TextArea } from "@synerise/ds-input";

export function YamlPreview({ yaml }: { yaml: string }) {
  const [copied, setCopied] = useState(false);
  const bytes = useMemo(() => new Blob([yaml]).size, [yaml]);

  const copy = async () => {
    await navigator.clipboard.writeText(yaml);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1500);
  };

  const download = () => {
    const blob = new Blob([yaml], { type: "text/yaml;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "oie-rules.yaml";
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="yaml-pane">
      <div className="yaml-actions">
        <Button type="primary" onClick={download}>
          Download YAML
        </Button>
        <Button type="secondary" onClick={copy}>
          {copied ? "Copied" : "Copy"}
        </Button>
        <span className="muted">{bytes} bytes · oie-rules.yaml</span>
      </div>
      <TextArea readOnly rows={28} value={yaml} />
    </div>
  );
}
