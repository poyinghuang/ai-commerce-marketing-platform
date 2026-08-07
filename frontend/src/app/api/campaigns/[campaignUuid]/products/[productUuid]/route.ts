import { NextRequest } from "next/server";
import { forwardCampaignRequest } from "@/lib/backend-proxy";

type Context = { params: Promise<{ campaignUuid: string; productUuid: string }> };
export async function GET(request: NextRequest, { params }: Context) {
  const value = await params;
  return forwardCampaignRequest(request, `/api/campaigns/${value.campaignUuid}/products/${value.productUuid}`, { method: "GET" });
}
export async function PATCH(request: NextRequest, { params }: Context) {
  const value = await params;
  return forwardCampaignRequest(request, `/api/campaigns/${value.campaignUuid}/products/${value.productUuid}`, { method: "PATCH", contentType: "application/merge-patch+json" });
}
export async function DELETE(request: NextRequest, { params }: Context) {
  const value = await params;
  return forwardCampaignRequest(request, `/api/campaigns/${value.campaignUuid}/products/${value.productUuid}`, { method: "DELETE" });
}
