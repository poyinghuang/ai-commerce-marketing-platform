"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { ProductForm } from "@/components/product-form";
import type { ApiError, Product, ProductInput } from "@/lib/products";
import { compactCreateInput } from "@/lib/products";

export function ProductCreateView() {
  const router = useRouter();
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function create(input: ProductInput) {
    setSaving(true);
    setError(null);
    try {
      const response = await fetch("/api/products", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(compactCreateInput(input)),
      });
      const body = (await response.json()) as Product & ApiError;
      if (!response.ok) throw new Error(body.message ?? "商品建立失敗");
      router.push(`/products/${body.productUuid}`);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "商品建立失敗");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="page-stack narrow-page">
      <header className="page-header"><div><p className="eyebrow">Product Master</p><h1>新增商品</h1><p className="summary">Product UUID 與 Product ID 將由系統建立。</p></div><Link href="/products">返回列表</Link></header>
      {error && <div className="state-card error-state" role="alert">{error}</div>}
      <section className="content-card"><ProductForm submitLabel="建立商品" disabled={saving} onSubmit={create} /></section>
    </div>
  );
}
