"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { FormEvent, useEffect, useState } from "react";
import type { ApiError, ProductPage } from "@/lib/products";

export function ProductListView() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const query = searchParams.toString();
  const [data, setData] = useState<ProductPage | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const controller = new AbortController();
    fetch(`/api/products${query ? `?${query}` : ""}`, { cache: "no-store", signal: controller.signal })
      .then(async (response) => {
        if (!response.ok) {
          const failure = (await response.json()) as ApiError;
          throw new Error(failure.message ?? "商品資料讀取失敗");
        }
        return response.json() as Promise<ProductPage>;
      })
      .then(setData)
      .catch((failure: unknown) => {
        if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : "商品資料讀取失敗");
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [query]);

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const next = new URLSearchParams();
    for (const key of ["keyword", "status", "category", "sort"]) {
      const value = form.get(key)?.toString().trim();
      if (value) next.set(key, value);
    }
    next.set("page", "0");
    next.set("size", searchParams.get("size") ?? "20");
    router.push(`/products?${next.toString()}`);
  }

  function goToPage(page: number) {
    const next = new URLSearchParams(searchParams);
    next.set("page", page.toString());
    router.push(`/products?${next.toString()}`);
  }

  return (
    <div className="page-stack">
      <header className="page-header">
        <div>
          <p className="eyebrow">Product Knowledge Center</p>
          <h1>商品主檔</h1>
          <p className="summary">管理可追蹤、可封存且具備版本控制的正式商品資料。</p>
        </div>
        <Link className="primary-button link-button" href="/products/new">新增商品</Link>
      </header>

      <form className="filter-bar" onSubmit={applyFilters}>
        <input name="keyword" defaultValue={searchParams.get("keyword") ?? ""} placeholder="搜尋名稱、SKU、品牌…" />
        <select name="status" defaultValue={searchParams.get("status") ?? "ACTIVE"}>
          <option value="ACTIVE">使用中</option>
          <option value="ARCHIVED">已封存</option>
          <option value="ALL">全部</option>
        </select>
        <input name="category" defaultValue={searchParams.get("category") ?? ""} placeholder="分類" />
        <select name="sort" defaultValue={searchParams.get("sort") ?? "updatedAt,desc"}>
          <option value="updatedAt,desc">最近更新</option>
          <option value="productName,asc">名稱 A–Z</option>
          <option value="productId,asc">Product ID</option>
          <option value="salePrice,desc">售價高至低</option>
          <option value="stock,asc">庫存低至高</option>
        </select>
        <button className="secondary-button" type="submit">套用</button>
      </form>

      {loading && <div className="state-card" role="status">正在載入商品…</div>}
      {error && <div className="state-card error-state" role="alert">{error}</div>}
      {!loading && !error && data?.content.length === 0 && (
        <div className="state-card"><h2>尚無符合條件的商品</h2><p>調整篩選條件，或建立第一筆商品。</p></div>
      )}
      {!loading && !error && data && data.content.length > 0 && (
        <>
          <div className="table-shell">
            <table>
              <thead><tr><th>Product ID</th><th>商品</th><th>分類</th><th>售價</th><th>庫存</th><th>狀態</th></tr></thead>
              <tbody>
                {data.content.map((product) => (
                  <tr key={product.productUuid}>
                    <td className="mono">{product.productId}</td>
                    <td><Link href={`/products/${product.productUuid}`}>{product.productName || "未命名商品"}</Link><small>{product.sku ?? "無 SKU"}</small></td>
                    <td>{product.category ?? "—"}</td>
                    <td>{product.salePrice ? `${product.currency ?? ""} ${product.salePrice}` : "—"}</td>
                    <td>{product.stock ?? "—"}</td>
                    <td><span className={`status-badge ${product.lifecycleStatus.toLowerCase()}`}>{product.lifecycleStatus === "ACTIVE" ? "使用中" : "已封存"}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="pagination">
            <button className="secondary-button" disabled={data.page === 0} onClick={() => goToPage(data.page - 1)}>上一頁</button>
            <span>第 {data.page + 1} / {Math.max(data.totalPages, 1)} 頁 · 共 {data.totalElements} 筆</span>
            <button className="secondary-button" disabled={data.page + 1 >= data.totalPages} onClick={() => goToPage(data.page + 1)}>下一頁</button>
          </div>
        </>
      )}
    </div>
  );
}
