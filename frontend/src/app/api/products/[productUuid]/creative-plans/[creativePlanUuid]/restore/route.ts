import { NextRequest } from "next/server";
import { forwardCreativePlanRequest } from "@/lib/backend-proxy";
type Context={params:Promise<{productUuid:string;creativePlanUuid:string}>};
export async function POST(request:NextRequest,{params}:Context){const p=await params;return forwardCreativePlanRequest(request,`/api/products/${p.productUuid}/creative-plans/${p.creativePlanUuid}/restore`,{method:"POST"});}
