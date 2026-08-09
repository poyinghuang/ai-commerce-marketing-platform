import { NextRequest } from "next/server";
import { forwardSheetConnectorRequest } from "@/lib/backend-proxy";

type Context = { params: Promise<{ importJobUuid: string }> };

export async function GET(request: NextRequest, { params }: Context) {
  const { importJobUuid } = await params;
  return forwardSheetConnectorRequest(request, `/api/connectors/google-sheets/imports/${importJobUuid}`, { method: "GET" });
}
