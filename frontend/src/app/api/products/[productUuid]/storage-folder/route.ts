import { NextRequest } from "next/server";
import { forwardStorageFolderRequest } from "@/lib/backend-proxy";

type Context = { params: Promise<{ productUuid: string }> };

export async function GET(request: NextRequest, { params }: Context) {
  const { productUuid } = await params;
  return forwardStorageFolderRequest(request, `/api/products/${productUuid}/storage-folder`, { method: "GET" });
}

export async function POST(request: NextRequest, { params }: Context) {
  const { productUuid } = await params;
  return forwardStorageFolderRequest(request, `/api/products/${productUuid}/storage-folder`, { method: "POST" });
}
