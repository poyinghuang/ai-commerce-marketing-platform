import { NextRequest } from "next/server";
import { forwardCampaignRequest } from "@/lib/backend-proxy";

type Context = { params: Promise<{ campaignUuid: string }> };

export async function GET(request: NextRequest, { params }: Context) {
  const { campaignUuid } = await params;
  return forwardCampaignRequest(request, `/api/campaigns/${campaignUuid}`, { method: "GET" });
}
export async function PATCH(request: NextRequest, { params }: Context) {
  const { campaignUuid } = await params;
  return forwardCampaignRequest(request, `/api/campaigns/${campaignUuid}`, { method: "PATCH", contentType: "application/merge-patch+json" });
}
export async function DELETE(request: NextRequest, { params }: Context) {
  const { campaignUuid } = await params;
  return forwardCampaignRequest(request, `/api/campaigns/${campaignUuid}`, { method: "DELETE" });
}
