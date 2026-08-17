import { NextRequest, NextResponse } from "next/server";

const UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";
const META_PATH = new RegExp(`^/api/platforms/meta/(?:campaigns(?:/preview|/${UUID}(?:/state-preview|/(?:pause|resume)|/ad-sets(?:/preview)?)?)?|ad-sets/${UUID}(?:/state-preview|/budget-preview|/budget|/(?:pause|resume)))$`);
const OP_PATH = new RegExp(`^/api/platform-operations/${UUID}(?:/(?:retry|reconcile))?$`);
const SAFE_REQUEST_ID = /^[A-Za-z0-9._:-]{1,128}$/;
const REQUEST_LIMIT = 16 * 1024;
const RESPONSE_LIMIT = 1024 * 1024;

export type PlatformOperationView = {
  operationUuid: string; operationType: string; entityType: "CAMPAIGN" | "AD_SET";
  entityUuid: string; status: string; attemptCount: number; reconciliationCount: number;
  maxAttempts: number; normalizedErrorCode?: string; nextAttemptAt?: string;
  completedAt?: string; createdAt: string; updatedAt: string; version: number;
};

export async function forwardStage4B(request: NextRequest, backendPath: string) {
  if (process.env.PLATFORM_STAGE4B_ENABLED !== "true") return error("PLATFORM_STAGE4B_DISABLED", "Platform management is unavailable", 404);
  if ((!META_PATH.test(backendPath) && !OP_PATH.test(backendPath)) || request.nextUrl.search || request.nextUrl.hash) return error("PLATFORM_REQUEST_INVALID", "Platform request is invalid", 400);
  const isEmpty = /\/(retry|reconcile)$/.test(backendPath);
  if (request.method === "POST") {
    const contentType=request.headers.get("content-type");
    if ((!isEmpty && contentType !== "application/json") || (isEmpty && contentType)) return error("PLATFORM_REQUEST_INVALID", "Platform request is invalid", 400);
  }
  const origin=backendOrigin(); if(!origin)return error("BACKEND_BAD_GATEWAY","Backend is unavailable",502);
  const headers=new Headers();
  for(const name of ["If-Match","X-Request-ID"]){const value=request.headers.get(name);if(value&&(name!=="X-Request-ID"||SAFE_REQUEST_ID.test(value)))headers.set(name,value);}
  if(!isEmpty&&request.method==="POST")headers.set("Content-Type","application/json");
  let body: string|undefined;
  if(request.method==="POST"){body=await request.text();if(new TextEncoder().encode(body).byteLength>REQUEST_LIMIT)return error("PAYLOAD_TOO_LARGE","Request body is too large",413);if(isEmpty&&body.trim())return error("PLATFORM_REQUEST_INVALID","Platform request is invalid",400);if(isEmpty)body=undefined;}
  try{
    const response=await fetch(new URL(backendPath,origin),{method:request.method,headers,body,cache:"no-store",redirect:"manual",signal:AbortSignal.timeout(10_000)});
    if(response.status>=300&&response.status<400)return error("BACKEND_BAD_GATEWAY","Backend is unavailable",502);
    const bytes=new Uint8Array(await response.arrayBuffer());if(bytes.byteLength>RESPONSE_LIMIT)return error("BACKEND_RESPONSE_TOO_LARGE","Backend response is too large",502);
    const exposed=new Headers();for(const name of ["Content-Type","ETag","Location","X-Request-ID"]){const value=response.headers.get(name);if(value)exposed.set(name,value);}
    return new NextResponse(response.status===204?null:bytes,{status:response.status,headers:exposed});
  }catch(cause){return error(cause instanceof DOMException&&cause.name==="TimeoutError"?"BACKEND_TIMEOUT":"BACKEND_BAD_GATEWAY",cause instanceof DOMException&&cause.name==="TimeoutError"?"Backend request timed out":"Backend is unavailable",cause instanceof DOMException&&cause.name==="TimeoutError"?504:502);}
}

function backendOrigin(){const value=process.env.BACKEND_INTERNAL_URL;if(!value)return null;try{const url=new URL(value);if(!["http:","https:"].includes(url.protocol)||url.username||url.password||url.search||url.hash)return null;url.pathname=url.pathname.replace(/\/*$/,"/");return url;}catch{return null;}}
function error(code:string,message:string,status:number){return NextResponse.json({code,message},{status});}
