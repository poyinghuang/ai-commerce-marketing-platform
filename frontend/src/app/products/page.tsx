import { Suspense } from "react";
import { ProductListView } from "@/components/product-list-view";

export default function ProductsPage() {
  return <main className="app-main"><Suspense fallback={<div className="state-card">正在載入商品…</div>}><ProductListView /></Suspense></main>;
}
