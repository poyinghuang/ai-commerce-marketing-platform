import { NextRequest } from "next/server";
import { forwardAssetRequest } from "@/lib/backend-proxy";
type Context={params:Promise<{productUuid:string;assetUuid:string}>};
export async function POST(request:NextRequest,{params}:Context){const p=await params;return forwardAssetRequest(request,`/api/products/${p.productUuid}/assets/${p.assetUuid}/restore`,{method:"POST"});}
