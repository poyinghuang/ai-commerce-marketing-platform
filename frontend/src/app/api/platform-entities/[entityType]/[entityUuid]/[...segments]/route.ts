import { NextRequest } from "next/server";
import { forwardStage4B } from "@/lib/platform-stage4b";

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ entityType: string; entityUuid: string; segments: string[] }> },
) {
  const { entityType, entityUuid, segments } = await params;
  return forwardStage4B(request, `/api/platform-entities/${entityType}/${entityUuid}/${segments.join("/")}`);
}

export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ entityType: string; entityUuid: string; segments: string[] }> },
) {
  const { entityType, entityUuid, segments } = await params;
  return forwardStage4B(request, `/api/platform-entities/${entityType}/${entityUuid}/${segments.join("/")}`);
}
