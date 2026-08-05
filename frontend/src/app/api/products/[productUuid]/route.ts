import { NextRequest } from "next/server";
import { forwardProductRequest } from "@/lib/backend-proxy";

type RouteContext = { params: Promise<{ productUuid: string }> };

export async function GET(request: NextRequest, context: RouteContext) {
  const { productUuid } = await context.params;
  return forwardProductRequest(request, `/api/products/${productUuid}`, { method: "GET" });
}

export async function PATCH(request: NextRequest, context: RouteContext) {
  const { productUuid } = await context.params;
  return forwardProductRequest(request, `/api/products/${productUuid}`, {
    method: "PATCH",
    contentType: "application/merge-patch+json",
  });
}

export async function DELETE(request: NextRequest, context: RouteContext) {
  const { productUuid } = await context.params;
  return forwardProductRequest(request, `/api/products/${productUuid}`, { method: "DELETE" });
}
