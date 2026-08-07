import { NextRequest } from "next/server";
import { forwardCampaignRequest } from "@/lib/backend-proxy";

type Context = { params: Promise<{ campaignUuid: string }> };
export async function GET(request: NextRequest, { params }: Context) {
  const { campaignUuid } = await params;
  return forwardCampaignRequest(request, `/api/campaigns/${campaignUuid}/products`, { method: "GET" });
}
export async function POST(request: NextRequest, { params }: Context) {
  const { campaignUuid } = await params;
  return forwardCampaignRequest(request, `/api/campaigns/${campaignUuid}/products`, { method: "POST", contentType: "application/json" });
}
