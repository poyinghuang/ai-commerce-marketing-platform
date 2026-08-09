import { NextRequest } from "next/server";
import { forwardSheetConnectorRequest } from "@/lib/backend-proxy";

type Context = { params: Promise<{ importJobUuid: string }> };

export async function POST(request: NextRequest, { params }: Context) {
  const { importJobUuid } = await params;
  return forwardSheetConnectorRequest(request, `/api/connectors/google-sheets/imports/${importJobUuid}/execute`, { method: "POST" });
}
