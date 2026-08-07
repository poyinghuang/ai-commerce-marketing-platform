import { NextRequest } from "next/server";
import { forwardProductQualityRequest } from "@/lib/backend-proxy";

type RouteContext = { params: Promise<{ productUuid: string }> };

export async function GET(request: NextRequest, context: RouteContext) {
  const { productUuid } = await context.params;
  return forwardProductQualityRequest(request, `/api/products/${productUuid}/quality`, { method: "GET" });
}
