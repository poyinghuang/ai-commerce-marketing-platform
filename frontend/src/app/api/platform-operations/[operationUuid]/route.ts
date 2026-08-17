import { NextRequest } from "next/server";
import { forwardStage4B } from "@/lib/platform-stage4b";
export async function GET(request:NextRequest,{params}:{params:Promise<{operationUuid:string}>}){const {operationUuid}=await params;return forwardStage4B(request,"/api/platform-operations/"+operationUuid);}
