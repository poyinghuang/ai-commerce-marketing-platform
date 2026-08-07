import { NextRequest } from "next/server";
import { forwardCreativePlanRequest } from "@/lib/backend-proxy";
type Context={params:Promise<{productUuid:string;creativePlanUuid:string}>};
export async function GET(request:NextRequest,{params}:Context){const p=await params;return forwardCreativePlanRequest(request,`/api/products/${p.productUuid}/creative-plans/${p.creativePlanUuid}`,{method:"GET"});}
export async function PATCH(request:NextRequest,{params}:Context){const p=await params;return forwardCreativePlanRequest(request,`/api/products/${p.productUuid}/creative-plans/${p.creativePlanUuid}`,{method:"PATCH",contentType:"application/merge-patch+json"});}
export async function DELETE(request:NextRequest,{params}:Context){const p=await params;return forwardCreativePlanRequest(request,`/api/products/${p.productUuid}/creative-plans/${p.creativePlanUuid}`,{method:"DELETE"});}
