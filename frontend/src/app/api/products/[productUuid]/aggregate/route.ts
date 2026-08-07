import { NextRequest } from "next/server";
import { forwardProductAggregateRequest } from "@/lib/backend-proxy";

export async function GET(request: NextRequest, context: { params: Promise<{ productUuid: string }> }) {
  const { productUuid } = await context.params;
  return forwardProductAggregateRequest(request, `/api/products/${productUuid}/aggregate`);
}
