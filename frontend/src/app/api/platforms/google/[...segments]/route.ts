import { NextRequest } from "next/server";
import { forwardStage7Google } from "@/lib/platform-stage7-google";
export async function GET(request:NextRequest,{params}:{params:Promise<{segments:string[]}>}){const {segments}=await params;return forwardStage7Google(request,"/api/platforms/google/"+segments.join("/"));}
export async function POST(request:NextRequest,{params}:{params:Promise<{segments:string[]}>}){const {segments}=await params;return forwardStage7Google(request,"/api/platforms/google/"+segments.join("/"));}
