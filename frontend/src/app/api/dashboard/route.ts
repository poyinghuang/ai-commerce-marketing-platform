import { NextRequest } from "next/server";
import { forwardDashboard } from "@/lib/dashboard";

export function GET(request: NextRequest) {
  return forwardDashboard(request, "/api/dashboard");
}
