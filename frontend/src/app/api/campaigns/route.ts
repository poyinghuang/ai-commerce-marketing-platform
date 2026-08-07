import { NextRequest } from "next/server";
import { forwardCampaignRequest } from "@/lib/backend-proxy";

export async function GET(request: NextRequest) {
  return forwardCampaignRequest(request, "/api/campaigns", { method: "GET" });
}

export async function POST(request: NextRequest) {
  return forwardCampaignRequest(request, "/api/campaigns", { method: "POST", contentType: "application/json" });
}
