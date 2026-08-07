import { NextRequest } from "next/server";
import { forwardAssetRequest } from "@/lib/backend-proxy";
type Context={params:Promise<{productUuid:string;assetUuid:string}>};
export async function GET(request:NextRequest,{params}:Context){const p=await params;return forwardAssetRequest(request,`/api/products/${p.productUuid}/assets/${p.assetUuid}`,{method:"GET"});}
export async function PATCH(request:NextRequest,{params}:Context){const p=await params;return forwardAssetRequest(request,`/api/products/${p.productUuid}/assets/${p.assetUuid}`,{method:"PATCH",contentType:"application/merge-patch+json"});}
export async function DELETE(request:NextRequest,{params}:Context){const p=await params;return forwardAssetRequest(request,`/api/products/${p.productUuid}/assets/${p.assetUuid}`,{method:"DELETE"});}
