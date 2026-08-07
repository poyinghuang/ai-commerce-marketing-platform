import { ProductDetailView } from "@/components/product-detail-view";

export default async function ProductDetailPage({ params, searchParams }: { params: Promise<{ productUuid: string }>; searchParams: Promise<{tab?:string}> }) {
  const { productUuid } = await params;
  const requestedTab = (await searchParams).tab;
  const tab = requestedTab === "knowledge" || requestedTab === "creative-plans" || requestedTab === "campaigns" || requestedTab === "assets" || requestedTab === "quality" ? requestedTab : "product";
  return <main className="app-main"><ProductDetailView productUuid={productUuid} initialTab={tab} /></main>;
}
