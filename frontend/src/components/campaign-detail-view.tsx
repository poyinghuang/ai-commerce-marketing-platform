"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { CampaignForm } from "@/components/campaign-form";
import { CampaignProducts } from "@/components/campaign-products";
import { campaignPatch, campaignToInput, emptyCampaign, type Campaign, type CampaignInput } from "@/lib/campaigns";
import type { ApiError } from "@/lib/products";

export function CampaignDetailView({ campaignUuid }: { campaignUuid: string }) {
  const [campaign, setCampaign] = useState<Campaign | null>(null); const [input, setInput] = useState<CampaignInput>({ ...emptyCampaign }); const [etag, setEtag] = useState<string | null>(null);
  const [loading, setLoading] = useState(true); const [saving, setSaving] = useState(false); const [error, setError] = useState<string | null>(null); const [conflict, setConflict] = useState(false);
  const load = useCallback(async () => { setLoading(true); setError(null); try { const response = await fetch(`/api/campaigns/${campaignUuid}`, { cache: "no-store" }); const body = await response.json() as Campaign & ApiError; if (!response.ok) throw new Error(body.message ?? "Campaign 載入失敗"); setCampaign(body); setInput(campaignToInput(body)); setEtag(response.headers.get("ETag")); } catch (failure) { setError(failure instanceof Error ? failure.message : "Campaign 載入失敗"); } finally { setLoading(false); } }, [campaignUuid]);
  useEffect(() => { const controller = new AbortController(); fetch(`/api/campaigns/${campaignUuid}`, { cache: "no-store", signal: controller.signal }).then(async response => { const body = await response.json() as Campaign & ApiError; if (!response.ok) throw new Error(body.message ?? "Campaign 載入失敗"); setCampaign(body); setInput(campaignToInput(body)); setEtag(response.headers.get("ETag")); }).catch((failure: unknown) => { if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : "Campaign 載入失敗"); }).finally(() => { if (!controller.signal.aborted) setLoading(false); }); return () => controller.abort(); }, [campaignUuid]);
  async function mutate(method: "PATCH" | "DELETE" | "POST", url: string, body?: object) { if (!etag) { setConflict(true); return; } setSaving(true); setError(null); setConflict(false); const response = await fetch(url, { method, headers: { "If-Match": etag, ...(body ? { "Content-Type": "application/merge-patch+json" } : {}) }, body: body ? JSON.stringify(body) : undefined }); setSaving(false); if ([409, 412, 428].includes(response.status)) { setConflict(true); return; } if (!response.ok) { const failure = await response.json() as ApiError; setError(failure.message ?? "Campaign 更新失敗"); return; } await load(); }
  async function save() { if (!campaign) return; const patch = campaignPatch(campaignToInput(campaign), input); if (Object.keys(patch).length === 0) return; await mutate("PATCH", `/api/campaigns/${campaignUuid}`, patch); }
  if (loading) return <div className="state-card" role="status">載入 Campaign…</div>;
  if (!campaign) return <div className="state-card error-state" role="alert">{error ?? "找不到 Campaign"}</div>;
  const archived = campaign.lifecycleStatus === "ARCHIVED";
  return <div className="page-stack narrow-page"><header className="page-header"><div><p className="eyebrow">Campaign Plan</p><h1>{campaign.campaignName}</h1><p className="summary">Version {campaign.version}</p></div><Link href="/campaigns">返回列表</Link></header>
    {conflict && <div className="state-card warning-state" role="alert">資料已由其他使用者變更，或資源目前不可修改。<button className="secondary-button" onClick={() => void load()}>重新載入</button></div>}{error && <div className="state-card error-state" role="alert">{error}</div>}
    <section className="content-card"><div className="card-heading"><div><h2>Campaign 資料</h2><span className={`status-badge ${campaign.lifecycleStatus.toLowerCase()}`}>{campaign.lifecycleStatus}</span></div><button className={archived ? "secondary-button" : "danger-button"} disabled={saving} onClick={() => void mutate(archived ? "POST" : "DELETE", archived ? `/api/campaigns/${campaignUuid}/restore` : `/api/campaigns/${campaignUuid}`)}>{archived ? "還原 Campaign" : "封存 Campaign"}</button></div>{archived && <div className="state-card warning-state">Archived Campaign 僅供閱讀。</div>}<CampaignForm value={input} disabled={archived || saving} submitLabel="儲存 Campaign" onChange={setInput} onSubmit={() => void save()} /></section>
    <CampaignProducts campaignUuid={campaignUuid} campaignArchived={archived} />
  </div>;
}
