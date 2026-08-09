import { NextRequest } from "next/server";
import { forwardAiGenerationRequest } from "@/lib/backend-proxy";

export async function GET(request: NextRequest, { params }: { params: Promise<{ outputUuid: string }> }) {
  const { outputUuid } = await params;
  return forwardAiGenerationRequest(request, `/api/ai-generation-outputs/${outputUuid}`, { method: "GET" });
}
