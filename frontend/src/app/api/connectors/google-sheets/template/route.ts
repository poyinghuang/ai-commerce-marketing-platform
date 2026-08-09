import { NextRequest } from "next/server";
import { forwardSheetConnectorRequest } from "@/lib/backend-proxy";

export async function GET(request: NextRequest) {
  return forwardSheetConnectorRequest(request, "/api/connectors/google-sheets/template", {
    method: "GET",
    responseHeaders: ["Content-Type", "Content-Disposition", "X-Request-ID"],
  });
}
