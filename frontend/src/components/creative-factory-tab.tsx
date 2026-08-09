"use client";

import { useCallback, useEffect, useState } from "react";
import type { ApiError } from "@/lib/products";
import type { CreativePlanPage } from "@/lib/creative-plans";
import type { AiBatch, AiBudgetStatus, AiOutput } from "@/lib/ai-generation";

export function CreativeFactoryTab({ productUuid, productArchived }: { productUuid: string; productArchived: boolean }) {
  const [batches, setBatches] = useState<AiBatch[]>([]);
  const [plans, setPlans] = useState<CreativePlanPage["content"]>([]);
  const [budget, setBudget] = useState<AiBudgetStatus | null>(null);
  const [outputs, setOutputs] = useState<Record<string, AiOutput>>({});
  const [planUuid, setPlanUuid] = useState("");
  const [templateKey, setTemplateKey] = useState("");
  const [modelProfile, setModelProfile] = useState("STANDARD");
  const [variationCount, setVariationCount] = useState(3);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [conflict, setConflict] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [batchResponse, planResponse, budgetResponse] = await Promise.all([
        fetch(`/api/products/${productUuid}/ai-generation-batches`, { cache: "no-store" }),
        fetch(`/api/products/${productUuid}/creative-plans?status=ACTIVE&page=0&size=100&sort=updatedAt,desc`, { cache: "no-store" }),
        fetch("/api/ai-budget/status", { cache: "no-store" }),
      ]);
      const batchBody = await batchResponse.json() as AiBatch[] & ApiError;
      const planBody = await planResponse.json() as CreativePlanPage & ApiError;
      const budgetBody = await budgetResponse.json() as AiBudgetStatus & ApiError;
      if (!batchResponse.ok) throw new Error(batchBody.message ?? "Unable to load generation history");
      if (!planResponse.ok) throw new Error(planBody.message ?? "Unable to load Creative Plans");
      if (!budgetResponse.ok) throw new Error(budgetBody.message ?? "AI budget is unavailable");
      setBatches(batchBody);
      setPlans(planBody.content);
      setBudget(budgetBody);
      setPlanUuid((current) => current || planBody.content[0]?.creativePlanUuid || "");
      setTemplateKey((current) => current || budgetBody.textTemplateKeys[0] || "");
      setModelProfile((current) => budgetBody.modelProfiles.includes(current) ? current : budgetBody.modelProfiles[0] || "");
      const ids = batchBody.flatMap((batch) => batch.jobs.map((job) => job.outputUuid)).filter(Boolean) as string[];
      const loaded = await Promise.all(ids.map(async (id) => {
        const response = await fetch(`/api/ai-generation-outputs/${id}`, { cache: "no-store" });
        return response.ok ? [id, await response.json() as AiOutput] as const : null;
      }));
      setOutputs(Object.fromEntries(loaded.filter(Boolean) as (readonly [string, AiOutput])[]));
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Unable to load Creative Factory");
    } finally {
      setLoading(false);
    }
  }, [productUuid]);

  useEffect(() => {
    const pending = window.setTimeout(() => { void load(); }, 0);
    return () => window.clearTimeout(pending);
  }, [load]);

  async function createBatch() {
    if (!planUuid || !templateKey) { setError("An active Creative Plan and text template are required"); return; }
    setBusy(true); setError(null); setConflict(false);
    try {
      const response = await fetch(`/api/products/${productUuid}/ai-generation-batches`, {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ creativePlanUuid: planUuid, templateKey, modelProfile, variationCount }),
      });
      const body = await response.json() as AiBatch & ApiError;
      if ([409, 412, 428].includes(response.status)) { setConflict(true); return; }
      if (!response.ok) throw new Error(body.message ?? "Unable to create text batch");
      await load();
    } catch (failure) { setError(failure instanceof Error ? failure.message : "Unable to create text batch"); }
    finally { setBusy(false); }
  }

  async function execute(jobUuid: string, version: number) {
    setBusy(true); setError(null); setConflict(false);
    try {
      const response = await fetch(`/api/ai-generation-jobs/${jobUuid}/execute`, {
        method: "POST", headers: { "If-Match": `W/"${version}"` },
      });
      const body = await response.json() as AiOutput & ApiError;
      if ([409, 412, 428].includes(response.status)) { setConflict(true); return; }
      if (!response.ok) throw new Error(body.message ?? "Unable to execute generation job");
      await load();
    } catch (failure) { setError(failure instanceof Error ? failure.message : "Unable to execute generation job"); }
    finally { setBusy(false); }
  }

  return <section className="content-card creative-factory-tab">
    <div className="card-heading"><div><h2>Creative Factory</h2><p className="summary">Text generation only · every result remains Pending review</p></div></div>
    {productArchived && <div className="state-card warning-state">Archived Products cannot start AI generation.</div>}
    {conflict && <div className="state-card warning-state" role="alert">Generation state changed. Reload the latest state before retrying. <button className="secondary-button" onClick={() => void load()}>Reload</button></div>}
    {error && <div className="state-card error-state" role="alert">{error}</div>}
    {!productArchived && <div className="form-grid">
      <label>Creative Plan<select value={planUuid} onChange={(event) => setPlanUuid(event.target.value)}><option value="">Select a plan</option>{plans.map((plan) => <option key={plan.creativePlanUuid} value={plan.creativePlanUuid}>{plan.planName}</option>)}</select></label>
      <label>Template<select value={templateKey} onChange={(event) => setTemplateKey(event.target.value)}><option value="">Select a template</option>{budget?.textTemplateKeys.map((key) => <option key={key}>{key}</option>)}</select></label>
      <label>Model profile<select value={modelProfile} onChange={(event) => setModelProfile(event.target.value)}>{budget?.modelProfiles.map((profile) => <option key={profile}>{profile}</option>)}</select></label>
      <label>Variations<select value={variationCount} onChange={(event) => setVariationCount(Number(event.target.value))}><option value={1}>1</option><option value={2}>2</option><option value={3}>3</option></select></label>
      <button className="primary-button" disabled={busy || !planUuid || !templateKey} onClick={() => void createBatch()}>Create text batch</button>
    </div>}
    {loading ? <div className="state-card" role="status">Loading generation history…</div>
      : batches.length === 0 ? <div className="state-card">No text generation batches yet.</div>
        : <div className="page-stack">{batches.map((batch) => <article className="state-card" key={batch.generationBatchUuid}>
          <strong>Batch {batch.generationBatchUuid.slice(0, 8)}</strong> <span className="status-badge">{batch.status}</span>
          <p>{batch.succeededJobCount} succeeded · {batch.failedJobCount} failed · {batch.rejectedJobCount} rejected</p>
          {batch.jobs.map((job) => { const output = job.outputUuid ? outputs[job.outputUuid] : undefined; return <div className="content-card" key={job.generationJobUuid}>
            <div className="card-heading"><div><strong>Variation · {job.status}</strong><p className="summary">Reserved {job.reservedCost} {job.currency}</p></div>{job.status === "CREATED" && <button className="primary-button" disabled={busy} onClick={() => void execute(job.generationJobUuid, job.version)}>Execute</button>}</div>
            {job.failureCode && <div className="state-card error-state">{job.failureCode}: {job.failureMessage}</div>}
            {output && <div><p>{output.textContent}</p><p className="summary">{output.modelLabel} · {output.inputUnits}/{output.outputUnits} units · {output.actualCost} {output.currency} · {output.reviewStatus}</p></div>}
          </div>; })}
        </article>)}</div>}
  </section>;
}
