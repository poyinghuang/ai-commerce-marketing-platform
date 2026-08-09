import { NextRequest } from "next/server";
import { forwardAiGenerationRequest } from "@/lib/backend-proxy";

export async function GET(request: NextRequest, { params }: { params: Promise<{ jobUuid: string }> }) {
  const { jobUuid } = await params;
  return forwardAiGenerationRequest(request, `/api/ai-generation-jobs/${jobUuid}`, { method: "GET" });
}
