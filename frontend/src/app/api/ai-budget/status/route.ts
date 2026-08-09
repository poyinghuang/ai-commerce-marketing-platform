import { NextRequest } from "next/server";
import { forwardAiGenerationRequest } from "@/lib/backend-proxy";

export async function GET(request: NextRequest) {
  return forwardAiGenerationRequest(request, "/api/ai-budget/status", { method: "GET" });
}
