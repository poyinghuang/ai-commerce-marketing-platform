import { NextRequest } from "next/server";
import { forwardProductRequest } from "@/lib/backend-proxy";
type Context = { params: Promise<{ productUuid: string; knowledgeUuid: string }> };
export async function GET(request: NextRequest, context: Context) {
  const { productUuid, knowledgeUuid } = await context.params;
  return forwardProductRequest(request, `/api/products/${productUuid}/knowledge/${knowledgeUuid}`, { method: "GET" });
}
export async function PATCH(request: NextRequest, context: Context) {
  const { productUuid, knowledgeUuid } = await context.params;
  return forwardProductRequest(request, `/api/products/${productUuid}/knowledge/${knowledgeUuid}`, { method: "PATCH", contentType: "application/merge-patch+json" });
}
export async function DELETE(request: NextRequest, context: Context) {
  const { productUuid, knowledgeUuid } = await context.params;
  return forwardProductRequest(request, `/api/products/${productUuid}/knowledge/${knowledgeUuid}`, { method: "DELETE" });
}
