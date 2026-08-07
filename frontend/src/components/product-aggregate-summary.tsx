"use client";

import { useCallback, useEffect, useState } from "react";
import type { ProductAggregate } from "@/lib/aggregates";
import type { ApiError } from "@/lib/products";

export function ProductAggregateSummary({ productUuid }: { productUuid: string }) {
  const [aggregate, setAggregate] = useState<ProductAggregate | null>(null);
  const [includeArchived, setIncludeArchived] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    setError(null);
    try {
      const response = await fetch(
        `/api/products/${productUuid}/aggregate?includeArchived=${includeArchived}`,
        { cache: "no-store", signal },
      );
      const body = (await response.json()) as ProductAggregate & ApiError;
      if (!response.ok) throw new Error(body.message ?? "整合摘要讀取失敗");
      setAggregate(body);
    } catch (failure) {
      if (!signal?.aborted) setError(failure instanceof Error ? failure.message : "整合摘要讀取失敗");
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, [includeArchived, productUuid]);

  useEffect(() => {
    const controller = new AbortController();
    fetch(`/api/products/${productUuid}/aggregate?includeArchived=${includeArchived}`, {
      cache: "no-store",
      signal: controller.signal,
    })
      .then(async (response) => {
        const body = (await response.json()) as ProductAggregate & ApiError;
        if (!response.ok) throw new Error(body.message ?? "整合摘要讀取失敗");
        setAggregate(body);
      })
      .catch((failure: unknown) => {
        if (!controller.signal.aborted) {
          setError(failure instanceof Error ? failure.message : "整合摘要讀取失敗");
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [includeArchived, productUuid]);

  return (
    <section className="content-card" aria-labelledby="aggregate-summary-heading">
      <div className="card-heading">
        <div>
          <h2 id="aggregate-summary-heading">Product Center 整合摘要</h2>
          <p>唯讀檢視；各項目的版本與編輯仍由原本頁籤管理。</p>
        </div>
        <label>
          <input
            type="checkbox"
            checked={includeArchived}
            onChange={(event) => {
              setLoading(true);
              setError(null);
              setIncludeArchived(event.target.checked);
            }}
          />
          包含已封存項目
        </label>
      </div>
      {loading ? <div className="state-card" role="status">正在載入整合摘要…</div> : null}
      {error ? (
        <div className="state-card error-state" role="alert">
          <p>{error}</p>
          <button className="secondary-button" onClick={() => void load()}>重試</button>
        </div>
      ) : null}
      {!loading && !error && aggregate ? (
        <div className="aggregate-grid">
          <AggregateGroup title="Knowledge" items={aggregate.knowledge.map((item) => item.title)} />
          <AggregateGroup title="Creative Plans" items={aggregate.creativePlans.map((item) => item.planName)} />
          <AggregateGroup title="Campaigns" items={aggregate.campaigns.map((item) => item.campaignName)} />
          <AggregateGroup
            title="Assets"
            items={aggregate.assets.map((item) => item.originalFilename ?? item.purpose ?? item.assetType)}
          />
        </div>
      ) : null}
    </section>
  );
}

function AggregateGroup({ title, items }: { title: string; items: string[] }) {
  return (
    <article className="state-card">
      <h3>{title} <span aria-label={`${items.length} items`}>({items.length})</span></h3>
      {items.length === 0 ? <p>尚無資料</p> : <ul>{items.slice(0, 5).map((item, index) => <li key={`${item}-${index}`}>{item}</li>)}</ul>}
      {items.length > 5 ? <p>另有 {items.length - 5} 項</p> : null}
    </article>
  );
}
