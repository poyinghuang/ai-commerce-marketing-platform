import { NextRequest } from "next/server";
import { forwardCampaignRequest } from "@/lib/backend-proxy";

type Context = { params: Promise<{ campaignUuid: string }> };
export async function POST(request: NextRequest, { params }: Context) {
  const { campaignUuid } = await params;
  return forwardCampaignRequest(request, `/api/campaigns/${campaignUuid}/restore`, { method: "POST" });
}
