import { NextRequest } from "next/server";
import { forwardDecision } from "@/lib/decision";

export async function POST(request: NextRequest, { params }: { params: Promise<{ recommendationUuid: string }> }) {
  const { recommendationUuid } = await params;
  return forwardDecision(request, `/api/decision-recommendations/${recommendationUuid}/reject`);
}
