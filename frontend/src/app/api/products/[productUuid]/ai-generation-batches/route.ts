import { NextRequest } from "next/server";
import { forwardAiGenerationRequest } from "@/lib/backend-proxy";

type Context = { params: Promise<{ productUuid: string }> };

export async function GET(request: NextRequest, context: Context) {
  const { productUuid } = await context.params;
  return forwardAiGenerationRequest(request, `/api/products/${productUuid}/ai-generation-batches`, { method: "GET" });
}

export async function POST(request: NextRequest, context: Context) {
  const { productUuid } = await context.params;
  return forwardAiGenerationRequest(request, `/api/products/${productUuid}/ai-generation-batches`, {
    method: "POST", contentType: "application/json",
  });
}
