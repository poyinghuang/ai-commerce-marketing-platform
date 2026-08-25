import { NextRequest } from "next/server";
import { forwardDashboard } from "@/lib/dashboard";

export async function GET(request: NextRequest, { params }: { params: Promise<{ section: string }> }) {
  const { section } = await params;
  return forwardDashboard(request, `/api/dashboard/${section}`);
}
