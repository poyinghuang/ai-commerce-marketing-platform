import { NextRequest } from "next/server";
import { forwardCampaignRequest } from "@/lib/backend-proxy";

type Context = { params: Promise<{ campaignUuid: string; productUuid: string }> };
export async function POST(request: NextRequest, { params }: Context) {
  const value = await params;
  return forwardCampaignRequest(request, `/api/campaigns/${value.campaignUuid}/products/${value.productUuid}/restore`, { method: "POST" });
}
