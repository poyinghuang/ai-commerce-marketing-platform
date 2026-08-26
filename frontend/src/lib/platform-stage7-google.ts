import { NextRequest, NextResponse } from "next/server";

const UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";
const GOOGLE_PATH = new RegExp(`^/api/platforms/google/(?:campaigns(?:/preview|/${UUID}(?:/state-preview|/(?:pause|resume)|/ad-sets(?:/preview)?)?)?|ad-sets/${UUID}(?:/state-preview|/budget-preview|/budget|/(?:pause|resume)|/ads(?:/preview)?)?|ads/${UUID}(?:/state/preview|/(?:pause|resume))?|operations/${UUID}(?:/(?:retry|reconcile))?)$`);
const AD_PATH = new RegExp(`^/api/platforms/google/(?:ad-sets/${UUID}/ads(?:/preview)?|ads/${UUID}(?:/state/preview|/(?:pause|resume))?)$`);
const SAFE_REQUEST_ID = /^[A-Za-z0-9._:-]{1,128}$/;
const REQUEST_LIMIT = 16 * 1024;
const RESPONSE_LIMIT = 1024 * 1024;

export async function forwardStage7Google(request: NextRequest, backendPath: string) {
  if (process.env.PLATFORM_STAGE7_GOOGLE_ENABLED !== "true") return error("PLATFORM_STAGE7_GOOGLE_DISABLED", "Platform management is unavailable", 404);
  if (AD_PATH.test(backendPath) && process.env.PLATFORM_STAGE4C_ENABLED !== "true") return error("PLATFORM_STAGE4C_DISABLED", "Platform management is unavailable", 404);
  if (!GOOGLE_PATH.test(backendPath) || request.nextUrl.hash) return error("PLATFORM_REQUEST_INVALID", "Platform request is invalid", 400);
  if (request.nextUrl.search) return error("PLATFORM_REQUEST_INVALID", "Platform request is invalid", 400);
  const isEmpty = /\/(retry|reconcile)$/.test(backendPath);
  if (request.method === "POST") {
    const contentType=request.headers.get("content-type");
    if ((!isEmpty && contentType !== "application/json") || (isEmpty && contentType)) return error("PLATFORM_REQUEST_INVALID", "Platform request is invalid", 400);
  }
  const origin=backendOrigin(); if(!origin)return error("BACKEND_BAD_GATEWAY","Backend is unavailable",502);
  const headers=new Headers();
  for(const name of ["If-Match","X-Request-ID"]){const value=request.headers.get(name);if(value&&(name!=="X-Request-ID"||SAFE_REQUEST_ID.test(value)))headers.set(name,value);}
  if(!isEmpty&&request.method==="POST")headers.set("Content-Type","application/json");
  let body: Uint8Array<ArrayBuffer>|undefined;
  if(request.method==="POST"){try{body=await readBounded(request.body,REQUEST_LIMIT);}catch{return error("PAYLOAD_TOO_LARGE","Request body is too large",413);}if(isEmpty&&new TextDecoder().decode(body).trim())return error("PLATFORM_REQUEST_INVALID","Platform request is invalid",400);if(isEmpty||body.byteLength===0)body=undefined;}
  try{
    const timeout=AbortSignal.timeout(10_000);const signal=AbortSignal.any([request.signal,timeout]);
    const upstream=new URL(backendPath,origin);
    const response=await fetch(upstream,{method:request.method,headers,body,cache:"no-store",redirect:"manual",signal});
    if(response.status>=300&&response.status<400)return error("BACKEND_BAD_GATEWAY","Backend is unavailable",502);
    const responseContentType=response.headers.get("content-type")?.split(";",1)[0].trim().toLowerCase();
    if(responseContentType!=="application/json")return error("BACKEND_BAD_GATEWAY","Backend is unavailable",502);
    let bytes:Uint8Array<ArrayBuffer>;try{bytes=await readBounded(response.body,RESPONSE_LIMIT);}catch{return error("BACKEND_RESPONSE_TOO_LARGE","Backend response is too large",502);}
    if(bytes.byteLength===0||!safeJson(bytes))return error("BACKEND_BAD_GATEWAY","Backend is unavailable",502);
    const exposed=new Headers();for(const name of ["Content-Type","ETag","Location","X-Request-ID"]){const value=response.headers.get(name);if(value)exposed.set(name,value);}
    const responseBody=response.status===204?null:bytes.buffer;
    return new NextResponse(responseBody,{status:response.status,headers:exposed});
  }catch(cause){const timedOut=cause instanceof DOMException&&cause.name==="TimeoutError";return error(timedOut?"BACKEND_TIMEOUT":"BACKEND_BAD_GATEWAY",timedOut?"Backend request timed out":"Backend is unavailable",timedOut?504:502);}
}

async function readBounded(stream:ReadableStream<Uint8Array>|null,limit:number):Promise<Uint8Array<ArrayBuffer>>{if(!stream)return new Uint8Array(0);const reader=stream.getReader();const chunks:Uint8Array[]=[];let size=0;try{while(true){const {done,value}=await reader.read();if(done)break;if(!value)continue;size+=value.byteLength;if(size>limit){await reader.cancel();throw new Error("limit");}chunks.push(value);}const result=new Uint8Array(new ArrayBuffer(size));let offset=0;for(const chunk of chunks){result.set(chunk,offset);offset+=chunk.byteLength;}return result;}finally{reader.releaseLock();}}
function safeJson(bytes:Uint8Array){try{const value=JSON.parse(new TextDecoder("utf-8",{fatal:true}).decode(bytes));return isAllowlisted(value);}catch{return false;}}
function isAllowlisted(value:unknown):boolean{
  if(Array.isArray(value))return value.every(isAllowlisted);
  if(value&&typeof value==="object"){
    return Object.entries(value).every(([key,item])=>SAFE_KEYS.has(key)&&key!=="operation"&&key!=="replay"&&isAllowlisted(item));
  }
  return true;
}
const SAFE_KEYS=new Set([
  "clientRequestUuid","platformAdSetUuid","expectedParentVersion","productUuid","assetUuid","generationOutputUuid",
  "reviewDecisionUuid","approvedChecksumFingerprint","creativeMappingKey","parentCampaignDesiredState",
  "parentAdSetDesiredState","newAdDesiredState","evidenceEligible","warnings","confirmable","entityType","entityUuid",
  "expectedEntityVersion","previousDesiredState","targetDesiredState","platformAdUuid","desiredState","observedState",
  "externalIdFingerprint","createdAt","updatedAt","version","operationUuid","operationType","status","attemptCount",
  "reconciliationCount","maxAttempts","normalizedErrorCode","nextAttemptAt","completedAt","platformCampaignUuid",
  "campaignUuid","campaignPlanVersion","objective","accountTimezone","scheduleStart","scheduleEnd","budgetType",
  "budgetAmount","currency","optimizationGoal","targetingProfile","placementProfile","expectedCampaignPlanVersion",
  "policy","reservation","kind","previousAmount","newAmount","reservedDelta","businessDate","batchReservedAfter",
  "accountDayReservedBefore","accountDayReservedAfter","accountDayRemainingAfter","businessZone","targetingProfileKey",
  "placementProfileKey","maxEntityAmount","maxBatchAmount","maxAccountDayAmount","code","message","requestId",
  "timestamp","path","fieldErrors","field",
]);

function backendOrigin(){const value=process.env.BACKEND_INTERNAL_URL;if(!value)return null;try{const url=new URL(value);if(!["http:","https:"].includes(url.protocol)||url.username||url.password||url.search||url.hash)return null;url.pathname=url.pathname.replace(/\/*$/,"/");return url;}catch{return null;}}
function error(code:string,message:string,status:number){return NextResponse.json({code,message},{status});}
