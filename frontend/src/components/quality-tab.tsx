"use client";

import { useCallback, useEffect, useState } from "react";
import type { ApiError } from "@/lib/products";
import { qualityComponents, type ProductQuality } from "@/lib/quality";

export function QualityTab({ productUuid, productArchived }: { productUuid: string; productArchived: boolean }) {
  const [quality, setQuality] = useState<ProductQuality | null>(null);
  const [etag, setEtag] = useState<string | null>(null);
  const [adjustment, setAdjustment] = useState("0");
  const [reason, setReason] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [conflict, setConflict] = useState<number | null>(null);

  const load = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    setError(null);
    setConflict(null);
    try {
      const response = await fetch(`/api/products/${productUuid}/quality`, { cache: "no-store", signal });
      const body = (await response.json()) as ProductQuality & ApiError;
      if (!response.ok) throw new Error(body.message ?? "Quality score 讀取失敗");
      const nextEtag = response.headers.get("ETag");
      setQuality(body);
      setEtag(nextEtag);
      setAdjustment(String(body.manualAdjustment));
      setReason(body.manualAdjustmentReason ?? "");
    } catch (failure) {
      if (!signal?.aborted) setError(failure instanceof Error ? failure.message : "Quality score 讀取失敗");
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, [productUuid]);

  useEffect(() => {
    const controller = new AbortController();
    fetch(`/api/products/${productUuid}/quality`, { cache: "no-store", signal: controller.signal })
      .then(async (response) => {
        const body = (await response.json()) as ProductQuality & ApiError;
        if (!response.ok) throw new Error(body.message ?? "Quality score 讀取失敗");
        setQuality(body);
        setEtag(response.headers.get("ETag"));
        setAdjustment(String(body.manualAdjustment));
        setReason(body.manualAdjustmentReason ?? "");
      })
      .catch((failure: unknown) => {
        if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : "Quality score 讀取失敗");
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [productUuid]);

  async function save(nextAdjustment = Number(adjustment), nextReason = reason) {
    if (!etag) {
      setConflict(428);
      return;
    }
    if (!Number.isInteger(nextAdjustment) || nextAdjustment < -20 || nextAdjustment > 20) {
      setError("人工調整必須是 -20 到 20 的整數。");
      return;
    }
    if (nextAdjustment !== 0 && nextReason.trim() === "") {
      setError("非零人工調整必須填寫理由。");
      return;
    }
    setSaving(true);
    setError(null);
    setConflict(null);
    try {
      const response = await fetch(`/api/products/${productUuid}/quality/manual-adjustment`, {
        method: "PATCH",
        headers: { "Content-Type": "application/merge-patch+json", "If-Match": etag },
        body: JSON.stringify({ manualAdjustment: nextAdjustment, reason: nextAdjustment === 0 ? null : nextReason.trim() }),
      });
      if ([409, 412, 428].includes(response.status)) {
        setConflict(response.status);
        return;
      }
      const body = (await response.json()) as ProductQuality & ApiError;
      if (!response.ok) {
        const fieldMessage = body.fieldErrors?.map((item) => item.message).join("；");
        throw new Error(fieldMessage || body.message || "人工調整失敗");
      }
      setQuality(body);
      setEtag(response.headers.get("ETag"));
      setAdjustment(String(body.manualAdjustment));
      setReason(body.manualAdjustmentReason ?? "");
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "人工調整失敗");
    } finally {
      setSaving(false);
    }
  }

  if (loading) return <section className="content-card"><div className="state-card" role="status">正在載入 Quality score…</div></section>;
  if (!quality) return <section className="content-card"><div className="state-card error-state" role="alert"><p>{error ?? "Quality projection 不存在。"}</p><button className="secondary-button" onClick={() => void load()}>重試</button></div></section>;
  const isArchived = productArchived || quality.blockers.some((blocker) => blocker.code === "PRODUCT_ARCHIVED");

  return (
    <section className="content-card quality-tab" aria-labelledby="quality-heading">
      <div className="quality-hero">
        <div><p className="eyebrow">Product readiness</p><h2 id="quality-heading">Quality Score</h2><p className="summary">{quality.statusReason}</p></div>
        <div className="quality-total" aria-label={`Final score ${quality.finalScore} out of 100`}>
          <strong>{quality.finalScore}</strong><span>/ 100</span>
          <span className={`readiness-badge ${quality.readinessStatus.toLowerCase()}`}>{label(quality.readinessStatus)}</span>
        </div>
      </div>

      <div className="quality-components">
        {qualityComponents.map(([name, field, maximum]) => (
          <article key={field} className="quality-component">
            <div><strong>{name}</strong><span>{quality[field]} / {maximum}</span></div>
            <progress max={maximum} value={quality[field]} aria-label={`${name} ${quality[field]} out of ${maximum}`} />
          </article>
        ))}
      </div>

      <div className="quality-equation">
        <span>System score <strong>{quality.systemScore}</strong></span>
        <span>Manual adjustment <strong>{signed(quality.manualAdjustment)}</strong></span>
        <span>Final score <strong>{quality.finalScore}</strong></span>
      </div>

      <section className="quality-blockers" aria-labelledby="blocking-reasons-heading">
        <h3 id="blocking-reasons-heading">Blocking reasons</h3>
        {quality.blockers.length === 0 ? <p className="success-copy">沒有 Blocking reason。</p> : (
          <ul>{quality.blockers.map((blocker) => <li key={blocker.code}><strong>{blocker.code}</strong><span>{blocker.message}</span>{blocker.field ? <code>{blocker.field}</code> : null}</li>)}</ul>
        )}
        <p className="summary">Manual adjustment 不會移除 Blocking reason。</p>
      </section>

      {conflict ? <Conflict status={conflict} reload={() => void load()} /> : null}
      {error ? <div className="state-card error-state" role="alert">{error}</div> : null}
      {isArchived ? <div className="state-card warning-state">商品已封存；Quality 可檢視，但不能人工調整。</div> : (
        <form className="quality-adjustment" onSubmit={(event) => { event.preventDefault(); void save(); }}>
          <div><h3>Manual adjustment</h3><p className="summary">允許 -20 到 +20。非零調整必須附上理由。</p></div>
          <label>調整分數<input type="number" min={-20} max={20} step={1} value={adjustment} onChange={(event) => setAdjustment(event.target.value)} /></label>
          <label className="quality-reason">調整理由<textarea maxLength={1000} value={reason} onChange={(event) => setReason(event.target.value)} /></label>
          <div className="quality-actions"><button className="primary-button" disabled={saving}>{saving ? "儲存中…" : "儲存調整"}</button><button type="button" className="secondary-button" disabled={saving || quality.manualAdjustment === 0} onClick={() => void save(0, "")}>重設為 0</button></div>
        </form>
      )}
      <p className="quality-meta">Calculated {new Date(quality.calculatedAt).toLocaleString()} · Version {quality.version}{quality.manualAdjustedBy ? ` · Adjusted by ${quality.manualAdjustedBy}` : ""}</p>
    </section>
  );
}

function Conflict({ status, reload }: { status: number; reload: () => void }) {
  const message = status === 409
    ? "商品已封存，無法調整 Quality score。"
    : status === 412
      ? "Quality score 已被其他操作更新，請載入最新版本。"
      : "缺少有效版本資訊，請重新載入後再試。";
  return <div className="state-card warning-state" role="alert"><p>{message}</p><button className="secondary-button" onClick={reload}>重新載入</button></div>;
}

function signed(value: number) { return value > 0 ? `+${value}` : String(value); }
function label(value: ProductQuality["readinessStatus"]) {
  return value === "READY" ? "Ready" : value === "NEEDS_REVIEW" ? "Needs review" : "Draft";
}
