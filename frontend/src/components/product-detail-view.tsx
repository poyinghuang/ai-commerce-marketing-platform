"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ProductForm } from "@/components/product-form";
import type { ApiError, Product, ProductInput } from "@/lib/products";
import { createMergePatch, productToInput } from "@/lib/products";
import { KnowledgeTab } from "@/components/knowledge-tab";

export function ProductDetailView({ productUuid }: { productUuid: string }) {
  const [product, setProduct] = useState<Product | null>(null);
  const [etag, setEtag] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [conflict, setConflict] = useState(false);

  async function load() {
    setLoading(true);
    setError(null);
    setConflict(false);
    try {
      const response = await fetch(`/api/products/${productUuid}`, { cache: "no-store" });
      const body = (await response.json()) as Product & ApiError;
      if (!response.ok) throw new Error(body.message ?? "商品讀取失敗");
      setProduct(body);
      setEtag(response.headers.get("ETag"));
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "商品讀取失敗");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    const controller = new AbortController();
    fetch(`/api/products/${productUuid}`, { cache: "no-store", signal: controller.signal })
      .then(async (response) => {
        const body = (await response.json()) as Product & ApiError;
        if (!response.ok) throw new Error(body.message ?? "商品讀取失敗");
        setProduct(body);
        setEtag(response.headers.get("ETag"));
      })
      .catch((failure: unknown) => {
        if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : "商品讀取失敗");
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [productUuid]);

  async function save(input: ProductInput) {
    if (!product) return;
    const patch = createMergePatch(productToInput(product), input);
    if (Object.keys(patch).length === 0) return;
    await mutate("PATCH", `/api/products/${productUuid}`, patch);
  }

  async function changeLifecycle() {
    if (!product) return;
    if (product.lifecycleStatus === "ACTIVE") {
      await mutate("DELETE", `/api/products/${productUuid}`);
    } else {
      await mutate("POST", `/api/products/${productUuid}/restore`);
    }
  }

  async function mutate(method: string, url: string, body?: object) {
    if (!etag) {
      setError("缺少版本資訊，請重新載入商品後再試。");
      return;
    }
    setSaving(true);
    setError(null);
    setConflict(false);
    try {
      const response = await fetch(url, {
        method,
        headers: {
          "If-Match": etag,
          ...(body ? { "Content-Type": "application/merge-patch+json" } : {}),
        },
        body: body ? JSON.stringify(body) : undefined,
      });
      if ([409, 412, 428].includes(response.status)) {
        setConflict(true);
        return;
      }
      if (!response.ok) {
        const failure = (await response.json()) as ApiError;
        throw new Error(failure.message ?? "商品更新失敗");
      }
      await load();
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "商品更新失敗");
    } finally {
      setSaving(false);
    }
  }

  if (loading) return <div className="state-card" role="status">正在載入商品…</div>;
  if (error && !product) return <div className="state-card error-state" role="alert">{error}</div>;
  if (!product) return <div className="state-card">找不到商品。</div>;

  return (
    <div className="page-stack narrow-page">
      <header className="page-header">
        <div><p className="eyebrow">{product.productId}</p><h1>{product.productName || "未命名商品"}</h1><p className="summary">Version {product.version} · {product.sku ?? "無 SKU"}</p></div>
        <Link href="/products">返回列表</Link>
      </header>
      {conflict && <div className="state-card warning-state" role="alert"><strong>資料已被其他操作更新。</strong><p>請重新載入最新版本後再決定是否套用變更。</p><button className="secondary-button" onClick={() => void load()}>重新載入</button></div>}
      {error && <div className="state-card error-state" role="alert">{error}</div>}
      <section className="content-card">
        <div className="card-heading"><div><h2>商品資料</h2><span className={`status-badge ${product.lifecycleStatus.toLowerCase()}`}>{product.lifecycleStatus === "ACTIVE" ? "使用中" : "已封存"}</span></div><button className={product.lifecycleStatus === "ACTIVE" ? "danger-button" : "secondary-button"} disabled={saving} onClick={() => void changeLifecycle()}>{product.lifecycleStatus === "ACTIVE" ? "封存商品" : "還原商品"}</button></div>
        {product.lifecycleStatus === "ACTIVE" ? (
          <ProductForm key={`${product.productUuid}-${product.version}`} initialValue={productToInput(product)} submitLabel="儲存變更" disabled={saving} onSubmit={save} />
        ) : (
          <div className="state-card"><p>Archived Product 不接受一般修改。還原後才能編輯。</p></div>
        )}
      </section>
      <KnowledgeTab productUuid={productUuid} productArchived={product.lifecycleStatus === "ARCHIVED"} />
    </div>
  );
}
