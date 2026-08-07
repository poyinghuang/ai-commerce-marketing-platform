import { NextRequest } from "next/server";
import { forwardProductRequest } from "@/lib/backend-proxy";
type Context = { params: Promise<{ productUuid: string }> };
export async function GET(request: NextRequest, context: Context) {
  const { productUuid } = await context.params;
  return forwardProductRequest(request, `/api/products/${productUuid}/knowledge`, { method: "GET" });
}
export async function POST(request: NextRequest, context: Context) {
  const { productUuid } = await context.params;
  return forwardProductRequest(request, `/api/products/${productUuid}/knowledge`, { method: "POST", contentType: "application/json" });
}
