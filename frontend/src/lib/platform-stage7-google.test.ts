import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { forwardStage7Google } from "./platform-stage7-google";

afterEach(()=>{vi.useRealTimers();vi.unstubAllGlobals();vi.restoreAllMocks();delete process.env.PLATFORM_STAGE7_GOOGLE_ENABLED;delete process.env.PLATFORM_STAGE4C_ENABLED;delete process.env.BACKEND_INTERNAL_URL;});

function jsonResponse(body:string,status=200,headers:Record<string,string>={}){return new Response(body,{status,headers:{"content-type":"application/json",...headers}});}

describe("forwardStage7Google",()=>{
 it("returns a local 404 while disabled without contacting Backend",async()=>{
  const fetch=vi.fn();vi.stubGlobal("fetch",fetch);
  const result=await forwardStage7Google(new NextRequest("http://local/api/platforms/google/campaigns/preview",{method:"POST",headers:{"content-type":"application/json"},body:"{}"}),"/api/platforms/google/campaigns/preview");
  expect(result.status).toBe(404);expect(fetch).not.toHaveBeenCalled();
 });
 it("forwards only the fixed path and safe headers",async()=>{
  process.env.PLATFORM_STAGE7_GOOGLE_ENABLED="true";process.env.BACKEND_INTERNAL_URL="http://backend:8080";
  const fetch=vi.fn().mockResolvedValue(jsonResponse('{"status":"SUCCEEDED"}',202,{ETag:'W/"2"',Location:"/api/platforms/google/operations/a"}));
  vi.stubGlobal("fetch",fetch);
  const request=new NextRequest("http://local/api/platforms/google/campaigns",{method:"POST",headers:{"content-type":"application/json","x-request-id":"safe-1",authorization:"secret"},body:'{"clientRequestUuid":"x"}'});
  const result=await forwardStage7Google(request,"/api/platforms/google/campaigns");
  expect(result.status).toBe(202);
  const init=fetch.mock.calls[0][1];
  expect(init.headers.get("Authorization")).toBeNull();
  expect(init.headers.get("X-Request-ID")).toBe("safe-1");
  expect(init.redirect).toBe("manual");
  expect(String(fetch.mock.calls[0][0])).toBe("http://backend:8080/api/platforms/google/campaigns");
 });
 it("gates Ad routes on Stage 4C and allows Google operations",async()=>{
  process.env.PLATFORM_STAGE7_GOOGLE_ENABLED="true";process.env.BACKEND_INTERNAL_URL="http://backend:8080";
  const id="11111111-1111-4111-8111-111111111111";
  const disabled=await forwardStage7Google(new NextRequest(`http://local/api/platforms/google/ads/${id}`),`/api/platforms/google/ads/${id}`);
  expect(disabled.status).toBe(404);
  process.env.PLATFORM_STAGE4C_ENABLED="true";
  vi.stubGlobal("fetch",vi.fn().mockImplementation(()=>jsonResponse('{"version":1,"desiredState":"PAUSED"}')));
  const enabled=await forwardStage7Google(new NextRequest(`http://local/api/platforms/google/ads/${id}`),`/api/platforms/google/ads/${id}`);
  expect(enabled.status).toBe(200);
  const op=await forwardStage7Google(new NextRequest(`http://local/api/platforms/google/operations/${id}`),`/api/platforms/google/operations/${id}`);
  expect(op.status).toBe(200);
 });
 it("rejects query strings, Meta paths, and oversized bodies locally",async()=>{
  process.env.PLATFORM_STAGE7_GOOGLE_ENABLED="true";process.env.BACKEND_INTERNAL_URL="http://backend:8080";
  const query=await forwardStage7Google(new NextRequest("http://local/api/platforms/google/campaigns?account=forbidden"),"/api/platforms/google/campaigns");
  expect(query.status).toBe(400);
  const meta=await forwardStage7Google(new NextRequest("http://local/api/platforms/meta/campaigns"),"/api/platforms/meta/campaigns");
  expect(meta.status).toBe(400);
  const large=await forwardStage7Google(new NextRequest("http://local/api/platforms/google/campaigns",{method:"POST",headers:{"content-type":"application/json"},body:"x".repeat(16385)}),"/api/platforms/google/campaigns");
  expect(large.status).toBe(413);
 });
 it("fails closed for redirect, empty, invalid, forbidden, and oversized Backend responses",async()=>{
  process.env.PLATFORM_STAGE7_GOOGLE_ENABLED="true";process.env.BACKEND_INTERNAL_URL="http://backend:8080";
  const request=new NextRequest("http://local/api/platforms/google/campaigns/00000000-0000-4000-8000-000000000001");
  for(const response of [new Response(null,{status:302}),jsonResponse(""),jsonResponse("not-json"),jsonResponse('{"externalId":"secret"}'),jsonResponse(JSON.stringify({value:"x".repeat(1024*1024)}))]){
    vi.stubGlobal("fetch",vi.fn().mockResolvedValue(response));
    const result=await forwardStage7Google(request,"/api/platforms/google/campaigns/00000000-0000-4000-8000-000000000001");
    expect(result.status).toBe(502);
  }
 });
});
