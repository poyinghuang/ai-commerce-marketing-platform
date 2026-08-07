import { NextRequest } from "next/server";
import { forwardAssetRequest } from "@/lib/backend-proxy";
type Context={params:Promise<{productUuid:string}>};
export async function GET(request:NextRequest,{params}:Context){const {productUuid}=await params;return forwardAssetRequest(request,`/api/products/${productUuid}/assets`,{method:"GET"});}
export async function POST(request:NextRequest,{params}:Context){const {productUuid}=await params;return forwardAssetRequest(request,`/api/products/${productUuid}/assets`,{method:"POST",contentType:"application/json"});}
