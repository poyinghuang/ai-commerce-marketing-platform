import { NextRequest } from "next/server";
import { forwardSheetConnectorRequest } from "@/lib/backend-proxy";

export async function POST(request: NextRequest) {
  return forwardSheetConnectorRequest(request, "/api/connectors/google-sheets/imports/preview", {
    method: "POST",
    contentType: "application/json",
  });
}
