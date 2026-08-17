import { NextRequest } from "next/server";
import { forwardStage4B } from "@/lib/platform-stage4b";
export async function GET(request:NextRequest,{params}:{params:Promise<{segments:string[]}>}){const {segments}=await params;return forwardStage4B(request,"/api/platforms/meta/"+segments.join("/"));}
export async function POST(request:NextRequest,{params}:{params:Promise<{segments:string[]}>}){const {segments}=await params;return forwardStage4B(request,"/api/platforms/meta/"+segments.join("/"));}
