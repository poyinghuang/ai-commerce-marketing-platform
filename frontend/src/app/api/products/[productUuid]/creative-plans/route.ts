import { NextRequest } from "next/server";
import { forwardCreativePlanRequest } from "@/lib/backend-proxy";
type Context={params:Promise<{productUuid:string}>};
export async function GET(request:NextRequest,{params}:Context){const {productUuid}=await params;return forwardCreativePlanRequest(request,`/api/products/${productUuid}/creative-plans`,{method:"GET"});}
export async function POST(request:NextRequest,{params}:Context){const {productUuid}=await params;return forwardCreativePlanRequest(request,`/api/products/${productUuid}/creative-plans`,{method:"POST",contentType:"application/json"});}
