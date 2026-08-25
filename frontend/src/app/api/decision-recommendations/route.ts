import { NextRequest } from "next/server";
import { forwardDecision } from "@/lib/decision";

export function GET(request: NextRequest) {
  return forwardDecision(request, "/api/decision-recommendations");
}
