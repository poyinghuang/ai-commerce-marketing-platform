import { NextRequest } from "next/server";
import { forwardProductRequest } from "@/lib/backend-proxy";

type RouteContext = { params: Promise<{ productUuid: string }> };

export async function POST(request: NextRequest, context: RouteContext) {
  const { productUuid } = await context.params;
  return forwardProductRequest(request, `/api/products/${productUuid}/restore`, { method: "POST" });
}
