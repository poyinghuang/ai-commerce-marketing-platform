import { ProductDetailView } from "@/components/product-detail-view";

export default async function ProductDetailPage({ params, searchParams }: { params: Promise<{ productUuid: string }>; searchParams: Promise<{tab?:string}> }) {
  const { productUuid } = await params;
  const tab = (await searchParams).tab === "creative-plans" ? "creative-plans" : "product";
  return <main className="app-main"><ProductDetailView productUuid={productUuid} initialTab={tab} /></main>;
}
