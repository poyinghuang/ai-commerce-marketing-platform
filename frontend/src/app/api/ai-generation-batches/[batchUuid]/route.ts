import { NextRequest } from "next/server";
import { forwardAiGenerationRequest } from "@/lib/backend-proxy";

export async function GET(request: NextRequest, { params }: { params: Promise<{ batchUuid: string }> }) {
  const { batchUuid } = await params;
  return forwardAiGenerationRequest(request, `/api/ai-generation-batches/${batchUuid}`, { method: "GET" });
}
