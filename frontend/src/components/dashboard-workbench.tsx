"use client";

import Link from "next/link";
import { useCallback, useEffect, useState, type ReactNode } from "react";

type Section<T> = { available: boolean; items: T[]; truncated: boolean; totalElements: number };
type Todo = { kind: string; subjectUuid: string; productUuid?: string; href: string; title: string; summary: string; occurredAt: string };
type Product = { productUuid: string; productName: string; lifecycleStatus: string; readinessStatus: string; finalScore: number; blockerCount: number; href: string };
type Review = { generationOutputUuid: string; productUuid: string; generationType: string; reviewStatus: string; version: number; blockerCount: number; approvalBlocked: boolean; href: string };
type Campaign = { campaignUuid: string; campaignName: string; lifecycleStatus: string; startDate?: string; endDate?: string; platform?: string; href: string };
type PlatformCampaign = { platformCampaignUuid: string; campaignUuid: string; campaignName: string; desiredState: string; observedState?: string; href: string };
type Anomaly = { kind: string; subjectUuid: string; href: string; title: string; summary: string; occurredAt: string };
type Kpis = {
  available: boolean; windowStart?: string; windowEnd?: string; timezone?: string; currency?: string;
  eligibleCampaignCount?: number; presentCampaignCount?: number; incomplete?: boolean;
  impressions?: number; reach?: number; clicks?: number; conversions?: number;
  spend?: string; revenue?: string; ctr?: string; cpc?: string; cpm?: string; cpa?: string; cvr?: string; roas?: string;
};
type Dashboard = {
  generatedAt: string;
  todos: Section<Todo>;
  products: Section<Product>;
  reviews: Section<Review>;
  campaigns: Section<Campaign>;
  platformCampaigns: Section<PlatformCampaign>;
  anomalies: Section<Anomaly>;
  kpis: Kpis;
};
type Evidence = {
  impressions?: number; reach?: number; clicks?: number; conversions?: number;
  spend?: string; revenue?: string; ctr?: string; cpc?: string; cpm?: string; cpa?: string; cvr?: string; roas?: string;
};
type Recommendation = {
  recommendationUuid: string; platformCampaignUuid: string; campaignUuid: string; campaignName: string;
  recommendationType: string; status: string; reasonSummary: string; riskSummary: string; evidence: Evidence;
  href: string; productUuid?: string; version: number; warnings: string[];
};

type Pending =
  | { kind: "review"; uuid: string; action: "approve" | "reject" }
  | { kind: "generate" }
  | { kind: "optimize"; uuid: string; action: "approve" | "reject" }
  | null;

export function DashboardWorkbench({ stage6Enabled = false }: { stage6Enabled?: boolean }) {
  const [data, setData] = useState<Dashboard | null>(null);
  const [recommendations, setRecommendations] = useState<Recommendation[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState<Pending>(null);
  const [reasons, setReasons] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const dashboardResponse = await fetch("/api/dashboard", { cache: "no-store" });
      const body = await dashboardResponse.json() as Dashboard & { message?: string };
      if (!dashboardResponse.ok) throw new Error(body.message ?? "Dashboard is unavailable");
      setData(body);
      if (stage6Enabled) {
        const recs = await fetch("/api/decision-recommendations?status=PENDING", { cache: "no-store" });
        const recBody = await recs.json() as { content?: Recommendation[]; message?: string };
        if (!recs.ok) throw new Error(recBody.message ?? "Decision engine is unavailable");
        setRecommendations(recBody.content ?? []);
      } else {
        setRecommendations([]);
      }
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Dashboard is unavailable");
      setData(null);
    } finally {
      setLoading(false);
    }
  }, [stage6Enabled]);

  useEffect(() => {
    const timer = window.setTimeout(() => { void load(); }, 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  async function confirmReview() {
    if (pending?.kind !== "review") return;
    const itemReason = (reasons[pending.uuid] ?? "").trim();
    if (pending.action === "reject" && !itemReason) {
      setError("A rejection reason is required.");
      return;
    }
    const item = data?.reviews.items.find((review) => review.generationOutputUuid === pending.uuid);
    if (!item) return;
    setBusy(true);
    setError(null);
    try {
      const response = await fetch(`/api/ai-generation-outputs/${pending.uuid}/${pending.action}`, {
        method: "POST",
        headers: { "Content-Type": "application/json", "If-Match": `W/"${item.version}"` },
        body: JSON.stringify(pending.action === "approve" ? {} : { reason: itemReason }),
      });
      const body = await response.json() as { message?: string };
      if (!response.ok) throw new Error(body.message ?? "Unable to record the review decision");
      setPending(null);
      setReasons((current) => {
        const next = { ...current };
        delete next[pending.uuid];
        return next;
      });
      await load();
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Unable to record the review decision");
    } finally {
      setBusy(false);
    }
  }

  async function confirmGenerate() {
    if (pending?.kind !== "generate") return;
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const response = await fetch("/api/decision-recommendations/generate", { method: "POST", cache: "no-store" });
      const body = await response.json() as { message?: string };
      if (!response.ok) throw new Error(body.message ?? "Unable to generate suggestions");
      setPending(null);
      await load();
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Unable to generate suggestions");
    } finally {
      setBusy(false);
    }
  }

  async function confirmOptimize() {
    if (pending?.kind !== "optimize") return;
    const itemReason = (reasons[pending.uuid] ?? "").trim();
    if (pending.action === "reject" && !itemReason) {
      setError("A rejection reason is required.");
      return;
    }
    const item = recommendations.find((recommendation) => recommendation.recommendationUuid === pending.uuid);
    if (!item) return;
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const response = await fetch(`/api/decision-recommendations/${pending.uuid}/${pending.action}`, {
        method: "POST",
        headers: { "Content-Type": "application/json", "If-Match": `W/"${item.version}"` },
        body: JSON.stringify(pending.action === "approve" ? {} : { reason: itemReason }),
      });
      const body = await response.json() as { message?: string };
      if (!response.ok) throw new Error(body.message ?? "Unable to record the operator decision");
      setPending(null);
      setNotice("Ads state did not change. Approval records the operator decision only.");
      setReasons((current) => {
        const next = { ...current };
        delete next[pending.uuid];
        return next;
      });
      await load();
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Unable to record the operator decision");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main>
      <div className="page-stack">
        <header className="page-header">
          <div>
            <p className="eyebrow">Stage 05 · Dashboard</p>
            <h1>營運工作台</h1>
            <p className="summary">待辦、商品完整度、審核、Campaign 狀態、KPI 與異常。AI 建議是既有素材審核，不是 Decision Engine。</p>
          </div>
          <Link href="/">返回首頁</Link>
        </header>
        {loading && <div className="state-card" role="status">載入 Dashboard…</div>}
        {error && <div className="state-card error-state" role="alert">{error}</div>}
        {notice && <div className="state-card" role="status">{notice}</div>}
        {data && (
          <div className="dashboard-grid">
            <section className="content-card" aria-labelledby="todos-heading">
              <h2 id="todos-heading">今日待辦</h2>
              <ItemList available={data.todos.available} empty="目前沒有待辦。" truncated={data.todos.truncated}
                items={data.todos.items.map((item) => (
                  <li key={`${item.kind}-${item.subjectUuid}`}><Link href={item.href}>{item.title}</Link> · {item.kind} · {item.summary}</li>
                ))} />
            </section>
            <section className="content-card" aria-labelledby="products-heading">
              <h2 id="products-heading">商品與資料完整度</h2>
              <ItemList available={data.products.available} empty="目前沒有商品。" truncated={data.products.truncated}
                items={data.products.items.map((item) => (
                  <li key={item.productUuid}><Link href={item.href}>{item.productName}</Link> · {item.readinessStatus} · {item.finalScore} · {item.blockerCount} blockers</li>
                ))} />
            </section>
            <section className="content-card" aria-labelledby="creative-heading">
              <h2 id="creative-heading">素材待審核</h2>
              <ItemList available={data.reviews.available} empty="沒有待審核素材。" truncated={data.reviews.truncated}
                items={data.reviews.items.map((item) => (
                  <li key={item.generationOutputUuid}><Link href={item.href}>{item.generationType} {item.generationOutputUuid}</Link> · {item.reviewStatus}</li>
                ))} />
            </section>
            <section className="content-card" aria-labelledby="campaigns-heading">
              <h2 id="campaigns-heading">Campaign 狀態</h2>
              <h3>Campaign plans</h3>
              <ItemList available={data.campaigns.available} empty="目前沒有 Campaign Plan。" truncated={data.campaigns.truncated}
                items={data.campaigns.items.map((item) => (
                  <li key={item.campaignUuid}><Link href={item.href}>{item.campaignName}</Link> · {item.lifecycleStatus} · {item.platform ?? "—"}</li>
                ))} />
              <h3>Platform campaigns</h3>
              {data.platformCampaigns.available
                ? (
                  <>
                    <ItemList available empty="目前沒有平台 Campaign。" truncated={data.platformCampaigns.truncated}
                      items={data.platformCampaigns.items.map((item) => (
                        <li key={item.platformCampaignUuid}><Link href={item.href}>{item.campaignName}</Link> · desired {item.desiredState} · observed {item.observedState ?? "—"}</li>
                      ))} />
                    <p className="summary">FAKE LOCAL/TEST only. No real provider or spend.</p>
                  </>
                )
                : <p className="summary">Platform campaigns are unavailable.</p>}
            </section>
            <section className="content-card" aria-labelledby="kpi-heading">
              <h2 id="kpi-heading">KPI Overview</h2>
              {data.kpis.available ? (
                <div>
                  <p className="summary">Window {data.kpis.windowStart ?? "—"} – {data.kpis.windowEnd ?? "—"} · {data.kpis.timezone} · {data.kpis.currency}</p>
                  <p className="summary">Coverage {data.kpis.presentCampaignCount ?? 0} / {data.kpis.eligibleCampaignCount ?? 0}</p>
                  {data.kpis.incomplete && <div className="state-card warning-state">Some campaign metrics are missing or unknown. NULL is not shown as zero.</div>}
                  <ul className="kpi-list">
                    <Metric label="Impressions" value={data.kpis.impressions} />
                    <Metric label="Reach" value={data.kpis.reach} />
                    <Metric label="Clicks" value={data.kpis.clicks} />
                    <Metric label="Conversions" value={data.kpis.conversions} />
                    <Metric label="Spend" value={data.kpis.spend} />
                    <Metric label="Revenue" value={data.kpis.revenue} />
                    <Metric label="CTR" value={data.kpis.ctr} />
                    <Metric label="CPC" value={data.kpis.cpc} />
                    <Metric label="CPM" value={data.kpis.cpm} />
                    <Metric label="CPA" value={data.kpis.cpa} />
                    <Metric label="CVR" value={data.kpis.cvr} />
                    <Metric label="ROAS" value={data.kpis.roas} />
                  </ul>
                  <p className="summary">FAKE LOCAL/TEST only. No real provider or spend.</p>
                </div>
              ) : <p className="summary">KPI Overview is unavailable.</p>}
            </section>
            <section className="content-card" aria-labelledby="suggestions-heading">
              <h2 id="suggestions-heading">AI 建議</h2>
              <p className="summary">Accept or reject pending Stage 03 generation outputs. This is not a Decision Engine suggestion.</p>
              {data.reviews.items.length === 0 ? <p className="summary">沒有待處理的 AI 建議。</p> : data.reviews.items.map((item) => (
                <article className="creative-item dashboard-review" data-output-uuid={item.generationOutputUuid} key={`review-${item.generationOutputUuid}`}>
                  <div>
                    <strong>{item.generationType}</strong>
                    <p className="summary">{item.generationOutputUuid} · {item.reviewStatus} · blockers {item.blockerCount}</p>
                    <Link href={item.href}>Open Creative Factory</Link>
                  </div>
                  <div className="form-grid">
                    <button className="primary-button" disabled={busy || item.approvalBlocked}
                      onClick={() => setPending({ kind: "review", uuid: item.generationOutputUuid, action: "approve" })}>
                      Approve output
                    </button>
                    <label>Rejection reason
                      <input maxLength={2000} value={reasons[item.generationOutputUuid] ?? ""}
                        onChange={(event) => setReasons((current) => ({
                          ...current,
                          [item.generationOutputUuid]: event.target.value,
                        }))} />
                    </label>
                    <button className="secondary-button" disabled={busy || !(reasons[item.generationOutputUuid] ?? "").trim()}
                      onClick={() => setPending({ kind: "review", uuid: item.generationOutputUuid, action: "reject" })}>
                      Reject output
                    </button>
                  </div>
                  {pending?.kind === "review" && pending.uuid === item.generationOutputUuid && (
                    <div className="form-grid">
                      <button className="primary-button" disabled={busy || (pending.action === "reject" && !(reasons[item.generationOutputUuid] ?? "").trim())}
                        onClick={() => void confirmReview()}>
                        {pending.action === "approve" ? "Confirm approve" : "Confirm reject"}
                      </button>
                      <button className="secondary-button" disabled={busy} onClick={() => setPending(null)}>Cancel</button>
                    </div>
                  )}
                </article>
              ))}
            </section>
            {stage6Enabled && (
              <section className="content-card optimization-card" aria-labelledby="optimization-heading">
                <h2 id="optimization-heading">優化建議</h2>
                <p className="summary">Deterministic FAKE suggestions only. No real provider or spend. Null metrics mean unknown. Approval does not execute.</p>
                <button className="primary-button" disabled={busy} onClick={() => setPending({ kind: "generate" })}>
                  Generate suggestions
                </button>
                {pending?.kind === "generate" && (
                  <div className="form-grid">
                    <button className="primary-button" disabled={busy} onClick={() => void confirmGenerate()}>
                      Confirm generate suggestions
                    </button>
                    <button className="secondary-button" disabled={busy} onClick={() => setPending(null)}>Cancel</button>
                  </div>
                )}
                {recommendations.length === 0
                  ? <p className="summary">目前沒有優化建議。</p>
                  : recommendations.map((item) => (
                    <article className="creative-item" data-recommendation-uuid={item.recommendationUuid} key={item.recommendationUuid}>
                      <div>
                        <strong>{item.recommendationType}</strong>
                        <p className="summary">{item.campaignName} · {item.status}</p>
                        <p className="summary">{item.reasonSummary}</p>
                        <p className="summary">{item.riskSummary}</p>
                        <p className="summary">ROAS {item.evidence.roas ?? "unknown"} · CPA {item.evidence.cpa ?? "unknown"} · CTR {item.evidence.ctr ?? "unknown"}</p>
                        <Link href={item.href}>Open linked workflow</Link>
                      </div>
                      <div className="form-grid">
                        <button className="primary-button" disabled={busy || item.status !== "PENDING"}
                          onClick={() => setPending({ kind: "optimize", uuid: item.recommendationUuid, action: "approve" })}>
                          Approve suggestion
                        </button>
                        <label>Rejection reason
                          <input maxLength={2000} value={reasons[item.recommendationUuid] ?? ""}
                            onChange={(event) => setReasons((current) => ({
                              ...current,
                              [item.recommendationUuid]: event.target.value,
                            }))} />
                        </label>
                        <button className="secondary-button" disabled={busy || item.status !== "PENDING" || !(reasons[item.recommendationUuid] ?? "").trim()}
                          onClick={() => setPending({ kind: "optimize", uuid: item.recommendationUuid, action: "reject" })}>
                          Reject suggestion
                        </button>
                      </div>
                      {pending?.kind === "optimize" && pending.uuid === item.recommendationUuid && (
                        <div className="form-grid">
                          <button className="primary-button" disabled={busy || (pending.action === "reject" && !(reasons[item.recommendationUuid] ?? "").trim())}
                            onClick={() => void confirmOptimize()}>
                            {pending.action === "approve" ? "Confirm approve suggestion" : "Confirm reject suggestion"}
                          </button>
                          <button className="secondary-button" disabled={busy} onClick={() => setPending(null)}>Cancel</button>
                        </div>
                      )}
                    </article>
                  ))}
              </section>
            )}
            <section className="content-card" aria-labelledby="anomalies-heading">
              <h2 id="anomalies-heading">異常事件</h2>
              <ItemList available={data.anomalies.available} empty="目前沒有異常事件。" truncated={data.anomalies.truncated}
                items={data.anomalies.items.map((item) => (
                  <li key={`${item.kind}-${item.subjectUuid}`}><Link href={item.href}>{item.title}</Link> · {item.kind} · {item.summary}</li>
                ))} />
            </section>
          </div>
        )}
      </div>
    </main>
  );
}

function ItemList({ available, empty, truncated, items }: { available: boolean; empty: string; truncated: boolean; items: ReactNode[] }) {
  if (!available) return <p className="summary">This section is unavailable.</p>;
  if (items.length === 0) return <p className="summary">{empty}</p>;
  return (
    <>
      <ul className="dashboard-list">{items}</ul>
      {truncated && <p className="summary">More items exist. Open the source list to continue.</p>}
    </>
  );
}

function Metric({ label, value }: { label: string; value: string | number | undefined }) {
  return <li><span>{label}</span><strong>{value === undefined ? "unknown" : value}</strong></li>;
}
