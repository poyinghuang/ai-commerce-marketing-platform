import { NextRequest } from "next/server";
import { forwardStage4B } from "@/lib/platform-stage4b";
export async function POST(request:NextRequest,{params}:{params:Promise<{operationUuid:string,action:string}>}){const {operationUuid,action}=await params;return forwardStage4B(request,`/api/platform-operations/${operationUuid}/${action}`);}
