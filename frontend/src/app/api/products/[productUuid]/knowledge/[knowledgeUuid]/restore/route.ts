import { NextRequest } from "next/server";
import { forwardKnowledgeRequest } from "@/lib/backend-proxy";
type Context = { params: Promise<{ productUuid: string; knowledgeUuid: string }> };
export async function POST(request: NextRequest, context: Context) {
  const { productUuid, knowledgeUuid } = await context.params;
  return forwardKnowledgeRequest(request, `/api/products/${productUuid}/knowledge/${knowledgeUuid}/restore`, { method: "POST" });
}
