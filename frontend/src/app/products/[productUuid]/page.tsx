import { ProductDetailView } from "@/components/product-detail-view";

export default async function ProductDetailPage({ params }: { params: Promise<{ productUuid: string }> }) {
  const { productUuid } = await params;
  return <main className="app-main"><ProductDetailView productUuid={productUuid} /></main>;
}
