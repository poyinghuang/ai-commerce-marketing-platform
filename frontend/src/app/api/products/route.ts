import { NextRequest } from "next/server";
import { forwardProductRequest } from "@/lib/backend-proxy";

export function GET(request: NextRequest) {
  return forwardProductRequest(request, "/api/products", { method: "GET" });
}

export function POST(request: NextRequest) {
  return forwardProductRequest(request, "/api/products", {
    method: "POST",
    contentType: "application/json",
  });
}
