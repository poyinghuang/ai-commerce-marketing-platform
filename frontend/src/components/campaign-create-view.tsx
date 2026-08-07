"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { CampaignForm } from "@/components/campaign-form";
import { campaignPayload, emptyCampaign, type Campaign, type CampaignInput } from "@/lib/campaigns";
import type { ApiError } from "@/lib/products";

export function CampaignCreateView() {
  const router = useRouter();
  const [input, setInput] = useState<CampaignInput>({ ...emptyCampaign });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  async function create() {
    setSaving(true); setError(null);
    try {
      const response = await fetch("/api/campaigns", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(campaignPayload(input)) });
      const body = await response.json() as Campaign & ApiError;
      if (!response.ok) throw new Error(body.message ?? "建立 Campaign 失敗");
      router.push(`/campaigns/${body.campaignUuid}`);
    } catch (failure) { setError(failure instanceof Error ? failure.message : "建立 Campaign 失敗"); }
    finally { setSaving(false); }
  }
  return <div className="page-stack narrow-page">
    <header className="page-header"><div><p className="eyebrow">Campaign Plan</p><h1>新增 Campaign</h1></div><Link href="/campaigns">返回列表</Link></header>
    {error && <div className="state-card error-state" role="alert">{error}</div>}
    <section className="content-card"><CampaignForm value={input} disabled={saving} submitLabel="建立 Campaign" onChange={setInput} onSubmit={() => void create()} /></section>
  </div>;
}
