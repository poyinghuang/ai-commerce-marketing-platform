export type ProductLifecycleStatus = "ACTIVE" | "ARCHIVED";

export type Product = {
  productUuid: string;
  productId: string;
  sku: string | null;
  productName: string | null;
  brand: string | null;
  category: string | null;
  subcategory: string | null;
  shortDescription: string | null;
  cost: string | null;
  salePrice: string | null;
  currency: string | null;
  stock: string | null;
  productUrl: string | null;
  lifecycleStatus: ProductLifecycleStatus;
  archivedAt: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type ProductPage = {
  content: Product[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  sort: { field: string; direction: "asc" | "desc" };
};

export type ProductInput = {
  sku: string;
  productName: string;
  brand: string;
  category: string;
  subcategory: string;
  shortDescription: string;
  cost: string;
  salePrice: string;
  currency: string;
  stock: string;
  productUrl: string;
};

export type ApiError = {
  code?: string;
  message?: string;
  requestId?: string;
  fieldErrors?: Array<{ field: string; message: string }>;
};

export function productToInput(product: Product): ProductInput {
  return {
    sku: product.sku ?? "",
    productName: product.productName ?? "",
    brand: product.brand ?? "",
    category: product.category ?? "",
    subcategory: product.subcategory ?? "",
    shortDescription: product.shortDescription ?? "",
    cost: product.cost ?? "",
    salePrice: product.salePrice ?? "",
    currency: product.currency ?? "",
    stock: product.stock ?? "",
    productUrl: product.productUrl ?? "",
  };
}

export function compactCreateInput(input: ProductInput) {
  return Object.fromEntries(
    Object.entries(input).map(([key, value]) => [key, value.trim() === "" ? null : value.trim()]),
  );
}

export function createMergePatch(before: ProductInput, after: ProductInput) {
  return Object.fromEntries(
    Object.entries(after)
      .filter(([key, value]) => value !== before[key as keyof ProductInput])
      .map(([key, value]) => [key, value.trim() === "" ? null : value.trim()]),
  );
}
