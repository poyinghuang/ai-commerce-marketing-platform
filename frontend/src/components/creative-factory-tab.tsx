"use client";

import { useCallback, useEffect, useState } from "react";
import type { ApiError } from "@/lib/products";
import type { CreativePlanPage } from "@/lib/creative-plans";
import type { AssetPage } from "@/lib/assets";
import type { AiBatch, AiBudgetStatus, AiOutput } from "@/lib/ai-generation";

type Mode = "TEXT" | "IMAGE";

export function CreativeFactoryTab({ productUuid, productArchived }: { productUuid: string; productArchived: boolean }) {
  const [batches, setBatches] = useState<AiBatch[]>([]);
  const [plans, setPlans] = useState<CreativePlanPage["content"]>([]);
  const [assets, setAssets] = useState<AssetPage["content"]>([]);
  const [budget, setBudget] = useState<AiBudgetStatus | null>(null);
  const [outputs, setOutputs] = useState<Record<string, AiOutput>>({});
  const [outputEtags, setOutputEtags] = useState<Record<string, string>>({});
  const [rejectionReasons, setRejectionReasons] = useState<Record<string, string>>({});
  const [mode, setMode] = useState<Mode>("TEXT");
  const [planUuid, setPlanUuid] = useState("");
  const [templateKey, setTemplateKey] = useState("");
  const [modelProfile, setModelProfile] = useState("STANDARD");
  const [variationCount, setVariationCount] = useState(3);
  const [sourceAssetUuid, setSourceAssetUuid] = useState("");
  const [maskAssetUuid, setMaskAssetUuid] = useState("");
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [conflict, setConflict] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [batchResponse, planResponse, budgetResponse, assetResponse] = await Promise.all([
        fetch(`/api/products/${productUuid}/ai-generation-batches`, { cache: "no-store" }),
        fetch(`/api/products/${productUuid}/creative-plans?status=ACTIVE&page=0&size=100&sort=updatedAt,desc`, { cache: "no-store" }),
        fetch("/api/ai-budget/status", { cache: "no-store" }),
        fetch(`/api/products/${productUuid}/assets?status=ACTIVE&assetType=IMAGE&page=0&size=100&sort=updatedAt,desc`, { cache: "no-store" }),
      ]);
      const batchBody = await batchResponse.json() as AiBatch[] & ApiError;
      const planBody = await planResponse.json() as CreativePlanPage & ApiError;
      const budgetBody = await budgetResponse.json() as AiBudgetStatus & ApiError;
      const assetBody = await assetResponse.json() as AssetPage & ApiError;
      if (!batchResponse.ok) throw new Error(batchBody.message ?? "Unable to load generation history");
      if (!planResponse.ok) throw new Error(planBody.message ?? "Unable to load Creative Plans");
      if (!budgetResponse.ok) throw new Error(budgetBody.message ?? "AI budget is unavailable");
      if (!assetResponse.ok) throw new Error(assetBody.message ?? "Unable to load image Assets");
      setBatches(batchBody);
      setPlans(planBody.content);
      setBudget(budgetBody);
      setAssets(assetBody.content);
      setPlanUuid((current) => current || planBody.content[0]?.creativePlanUuid || "");
      setTemplateKey((current) => current || budgetBody.textTemplateKeys[0] || "");
      setModelProfile((current) => budgetBody.modelProfiles.includes(current) ? current : budgetBody.modelProfiles[0] || "");
      setSourceAssetUuid((current) => current || assetBody.content.find((asset) => asset.assetType === "IMAGE")?.assetUuid || "");
      const ids = batchBody.flatMap((batch) => batch.jobs.map((job) => job.outputUuid)).filter(Boolean) as string[];
      const loaded = await Promise.all(ids.map(async (id) => {
        const response = await fetch(`/api/ai-generation-outputs/${id}`, { cache: "no-store" });
        return response.ok ? [id, await response.json() as AiOutput, response.headers.get("ETag") || ""] as const : null;
      }));
      const present = loaded.filter(Boolean) as (readonly [string, AiOutput, string])[];
      setOutputs(Object.fromEntries(present.map(([id, output]) => [id, output])));
      setOutputEtags(Object.fromEntries(present.map(([id, , etag]) => [id, etag])));
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

  function selectMode(next: Mode) {
    setMode(next);
    setModelProfile(next === "IMAGE" ? budget?.imageModelProfiles[0] || "" : budget?.modelProfiles[0] || "");
  }

  async function createBatch() {
    const selectedTemplate = mode === "IMAGE" ? budget?.imageTemplateKeys[0] || "" : templateKey;
    if (!planUuid || !selectedTemplate || (mode === "IMAGE" && !sourceAssetUuid)) {
      setError(mode === "IMAGE" ? "An active Creative Plan, image template and source Asset are required" : "An active Creative Plan and text template are required");
      return;
    }
    setBusy(true); setError(null); setConflict(null);
    try {
      const payload = mode === "IMAGE" ? {
        generationType: "IMAGE", creativePlanUuid: planUuid, templateKey: selectedTemplate,
        workflowKey: budget?.imageWorkflowKeys[0], modelProfile: budget?.imageModelProfiles[0],
        variationCount: 1, sourceAssetUuid, maskAssetUuid: maskAssetUuid || null,
      } : { creativePlanUuid: planUuid, templateKey, modelProfile, variationCount };
      const response = await fetch(`/api/products/${productUuid}/ai-generation-batches`, {
        method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload),
      });
      const body = await response.json() as AiBatch & ApiError;
      if ([409, 412, 428].includes(response.status)) { setConflict("Generation state changed. Reload the latest state before retrying."); return; }
      if (!response.ok) throw new Error(body.message ?? `Unable to create ${mode.toLowerCase()} batch`);
      await load();
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : `Unable to create ${mode.toLowerCase()} batch`);
    } finally { setBusy(false); }
  }

  async function execute(jobUuid: string, version: number) {
    setBusy(true); setError(null); setConflict(null);
    try {
      const response = await fetch(`/api/ai-generation-jobs/${jobUuid}/execute`, {
        method: "POST", headers: { "If-Match": `W/\"${version}\"` },
      });
      const body = await response.json() as AiOutput & ApiError;
      if ([409, 412, 428].includes(response.status)) { setConflict("Generation state changed. Reload the latest state before retrying."); return; }
      if (!response.ok) throw new Error(body.message ?? "Unable to execute generation job");
      await load();
    } catch (failure) { setError(failure instanceof Error ? failure.message : "Unable to execute generation job"); }
    finally { setBusy(false); }
  }

  async function review(outputUuid: string, action: "approve" | "reject") {
    const etag = outputEtags[outputUuid];
    const reason = rejectionReasons[outputUuid]?.trim() || "";
    if (!etag) { setConflict("The review version is missing. Reload before retrying."); return; }
    if (action === "reject" && !reason) { setError("A rejection reason is required."); return; }
    setBusy(true); setError(null); setConflict(null);
    try {
      const response = await fetch(`/api/ai-generation-outputs/${outputUuid}/${action}`, {
        method: "POST",
        headers: { "Content-Type": "application/json", "If-Match": etag },
        body: JSON.stringify(action === "approve" ? {} : { reason }),
      });
      const body = await response.json() as AiOutput & ApiError;
      if (response.status === 412) { setConflict("This output changed. Reload the latest result before reviewing it."); return; }
      if (response.status === 428) { setConflict("The review version is missing. Reload before retrying."); return; }
      if (response.status === 409) { setConflict(body.message ?? "This output cannot be reviewed in its current state."); return; }
      if (!response.ok) throw new Error(body.message ?? `Unable to ${action} output`);
      await load();
    } catch (failure) { setError(failure instanceof Error ? failure.message : `Unable to ${action} output`); }
    finally { setBusy(false); }
  }

  return <section className="content-card creative-factory-tab">
    <div className="card-heading"><div><h2>Creative Factory</h2><p className="summary">Generate text and protected-product images, then record an explicit human decision.</p></div></div>
    {productArchived && <div className="state-card warning-state">Archived Products cannot start AI generation.</div>}
    {conflict && <div className="state-card warning-state" role="alert">{conflict} <button className="secondary-button" onClick={() => void load()}>Reload</button></div>}
    {error && <div className="state-card error-state" role="alert">{error}</div>}
    {!productArchived && <div className="form-grid">
      <label>Generation mode<select value={mode} onChange={(event) => selectMode(event.target.value as Mode)}><option value="TEXT">Text</option><option value="IMAGE">Image background</option></select></label>
      <label>Creative Plan<select value={planUuid} onChange={(event) => setPlanUuid(event.target.value)}><option value="">Select a plan</option>{plans.map((plan) => <option key={plan.creativePlanUuid} value={plan.creativePlanUuid}>{plan.planName}</option>)}</select></label>
      {mode === "TEXT" ? <>
        <label>Template<select value={templateKey} onChange={(event) => setTemplateKey(event.target.value)}><option value="">Select a template</option>{budget?.textTemplateKeys.map((key) => <option key={key}>{key}</option>)}</select></label>
        <label>Model profile<select value={modelProfile} onChange={(event) => setModelProfile(event.target.value)}>{budget?.modelProfiles.map((profile) => <option key={profile}>{profile}</option>)}</select></label>
        <label>Variations<select value={variationCount} onChange={(event) => setVariationCount(Number(event.target.value))}><option value={1}>1</option><option value={2}>2</option><option value={3}>3</option></select></label>
      </> : <>
        <label>Image template<select aria-label="Image template" value={budget?.imageTemplateKeys[0] || ""} disabled>{budget?.imageTemplateKeys.map((key) => <option key={key}>{key}</option>)}</select></label>
        <label>Workflow<select value={budget?.imageWorkflowKeys[0] || ""} disabled>{budget?.imageWorkflowKeys.map((key) => <option key={key}>{key}</option>)}</select></label>
        <label>Source image<select value={sourceAssetUuid} onChange={(event) => setSourceAssetUuid(event.target.value)}><option value="">Select a source image</option>{assets.filter((asset) => asset.assetType === "IMAGE").map((asset) => <option key={asset.assetUuid} value={asset.assetUuid}>{asset.originalFilename || asset.assetUuid.slice(0, 8)}</option>)}</select></label>
        <label>Optional mask<select value={maskAssetUuid} onChange={(event) => setMaskAssetUuid(event.target.value)}><option value="">Use source alpha</option>{assets.filter((asset) => asset.assetType === "IMAGE" && asset.assetUuid !== sourceAssetUuid).map((asset) => <option key={asset.assetUuid} value={asset.assetUuid}>{asset.originalFilename || asset.assetUuid.slice(0, 8)}</option>)}</select></label>
      </>}
      <button className="primary-button" disabled={busy || !planUuid || (mode === "TEXT" ? !templateKey : !sourceAssetUuid || !budget?.imageTemplateKeys.length)} onClick={() => void createBatch()}>Create {mode === "IMAGE" ? "image" : "text"} batch</button>
    </div>}
    {loading ? <div className="state-card" role="status">Loading generation history…</div>
      : batches.length === 0 ? <div className="state-card">No generation batches yet.</div>
        : <div className="page-stack">{batches.map((batch) => <article className="state-card" key={batch.generationBatchUuid}>
          <strong>Batch {batch.generationBatchUuid.slice(0, 8)}</strong> <span className="status-badge">{batch.status}</span>
          <p>{batch.succeededJobCount} succeeded · {batch.failedJobCount} failed · {batch.rejectedJobCount} rejected</p>
          {batch.jobs.map((job) => { const output = job.outputUuid ? outputs[job.outputUuid] : undefined; return <div className="content-card" key={job.generationJobUuid}>
            <div className="card-heading"><div><strong>{job.generationType === "IMAGE" ? "Image" : "Variation"} · {job.status}</strong><p className="summary">Reserved {job.reservedCost} {job.currency} · {job.providerKey || "provider"}/{job.modelKey || job.modelProfile}</p><p className="summary">Prompt version {job.promptTemplateVersionUuid?.slice(0, 8) || "unavailable"}</p></div>{job.status === "CREATED" && <button className="primary-button" disabled={busy} onClick={() => void execute(job.generationJobUuid, job.version)}>Execute</button>}</div>
            {job.failureCode && <div className="state-card error-state">{job.failureCode}: {job.failureMessage}</div>}
            {output && <div>{output.generationType === "TEXT" ? <p>{output.textContent}</p> : <div className={`state-card ${output.preservationStatus === "BLOCKED" ? "error-state" : ""}`}>
              <strong>Protected pixels: {output.preservationStatus}</strong>
              <p className="summary">{output.imageWidth}×{output.imageHeight} · {output.mediaType} · generated Asset {output.generatedAssetUuid?.slice(0, 8)} · Pending review</p>
              <p className="summary">Source {output.sourceAssetUuid?.slice(0, 8)}{output.maskAssetUuid ? ` · mask ${output.maskAssetUuid.slice(0, 8)}` : " · source alpha mask"}</p>
              {output.preservationDetails && <p>{output.preservationDetails.changedPixelCount ?? 0} changed of {output.preservationDetails.protectedPixelCount ?? 0} protected pixels</p>}
            </div>}<p className="summary">{output.modelLabel} · {output.actualCost} {output.currency} · {output.reviewStatus}</p>
              {output.safetyFindings?.length > 0 && <div className="state-card warning-state"><strong>Safety findings</strong><ul>{output.safetyFindings.map((finding) => <li key={finding}>{finding}</li>)}</ul></div>}
              {output.reviewBlockers?.length > 0 && <div className="state-card warning-state"><strong>Approval blocked</strong><ul>{output.reviewBlockers.map((blocker) => <li key={blocker}>{blocker}</li>)}</ul></div>}
              {output.reviewStatus === "PENDING_REVIEW" && <div className="form-grid">
                <button className="primary-button" disabled={busy || productArchived || output.reviewBlockers?.length > 0} onClick={() => void review(output.generationOutputUuid, "approve")}>Approve output</button>
                <label>Rejection reason<input maxLength={2000} value={rejectionReasons[output.generationOutputUuid] || ""} onChange={(event) => setRejectionReasons((current) => ({ ...current, [output.generationOutputUuid]: event.target.value }))} /></label>
                <button className="secondary-button" disabled={busy || !(rejectionReasons[output.generationOutputUuid]?.trim())} onClick={() => void review(output.generationOutputUuid, "reject")}>Reject output</button>
              </div>}
              {output.reviewDecisions?.map((decision) => <div className="state-card" key={decision.reviewDecisionUuid}><strong>{decision.decision}</strong><p>Reviewed by {decision.reviewerId} · {new Date(decision.decidedAt).toLocaleString()}</p>{decision.reason && <p>{decision.reason}</p>}</div>)}
            </div>}
          </div>; })}
        </article>)}</div>}
  </section>;
}
