import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { PlatformMetaManager } from "./platform-meta-manager";

afterEach(()=>{cleanup();vi.unstubAllGlobals();});

describe("PlatformMetaManager",()=>{
 it("requires preview and an explicit second confirmation",async()=>{const request="11111111-1111-4111-8111-111111111111";vi.stubGlobal("crypto",{randomUUID:()=>request});const fetch=vi.fn().mockResolvedValue(new Response(JSON.stringify({clientRequestUuid:request,operationType:"CREATE_CAMPAIGN",entityType:"CAMPAIGN",desiredState:"PAUSED",expectedCampaignPlanVersion:0,reservation:{kind:"NONE",reservedDelta:"0",accountDayRemainingAfter:"1000"},policy:{currency:"TWD",maxBatchAmount:"300",maxAccountDayAmount:"1000"},warnings:[]}),{status:200,headers:{"content-type":"application/json"}}));vi.stubGlobal("fetch",fetch);render(<PlatformMetaManager/>);expect(screen.queryByText("Confirm FAKE operation")).not.toBeInTheDocument();fireEvent.change(screen.getByLabelText("Campaign Plan UUID"),{target:{value:request}});fireEvent.click(screen.getByText("Preview paused Campaign"));await waitFor(()=>expect(screen.getByText("Confirm FAKE operation")).toBeInTheDocument());expect(fetch).toHaveBeenCalledTimes(1);});
 it("offers retry only when due and reloads the operation after stale confirmation",async()=>{const request="11111111-1111-4111-8111-111111111111",operation="22222222-2222-4222-8222-222222222222",entity="33333333-3333-4333-8333-333333333333";vi.stubGlobal("crypto",{randomUUID:()=>request});const preview={clientRequestUuid:request,operationType:"CREATE_CAMPAIGN",entityType:"CAMPAIGN",desiredState:"PAUSED",expectedCampaignPlanVersion:0,reservation:{kind:"NONE",reservedDelta:"0",accountDayRemainingAfter:"1000"},policy:{currency:"TWD"},warnings:[]};const failed={operationUuid:operation,operationType:"CREATE_CAMPAIGN",entityType:"CAMPAIGN",entityUuid:entity,status:"FAILED_RETRYABLE",attemptCount:1,reconciliationCount:0,maxAttempts:3,nextAttemptAt:"2000-01-01T00:00:00Z",createdAt:"2000-01-01T00:00:00Z",updatedAt:"2000-01-01T00:00:00Z",version:2};const fetch=vi.fn().mockResolvedValueOnce(response(preview)).mockResolvedValueOnce(response(failed,202,{etag:'W/"2"'})).mockResolvedValueOnce(response({version:0,desiredState:"PAUSED"},200,{etag:'W/"0"'})).mockResolvedValueOnce(response({code:"PLATFORM_OPERATION_STALE",message:"reload"},412)).mockResolvedValueOnce(response({...failed,status:"SUCCEEDED",version:3},200,{etag:'W/"3"'}));vi.stubGlobal("fetch",fetch);render(<PlatformMetaManager/>);fireEvent.change(screen.getByLabelText("Campaign Plan UUID"),{target:{value:request}});fireEvent.click(screen.getByText("Preview paused Campaign"));await screen.findByText("Confirm FAKE operation");fireEvent.click(screen.getByText("Confirm FAKE operation"));await screen.findByText("Retry due operation");fireEvent.click(screen.getByText("Retry due operation"));expect(fetch).toHaveBeenCalledTimes(3);fireEvent.click(screen.getByText("Confirm retry"));await waitFor(()=>expect(screen.getByText("SUCCEEDED")).toBeInTheDocument());expect(fetch).toHaveBeenCalledTimes(5);});
 it("does not expose an early retry and requires confirmation for unknown reconciliation",async()=>{const request="11111111-1111-4111-8111-111111111111",entity="33333333-3333-4333-8333-333333333333";vi.stubGlobal("crypto",{randomUUID:()=>request});const preview={clientRequestUuid:request,operationType:"CREATE_CAMPAIGN",entityType:"CAMPAIGN",desiredState:"PAUSED",expectedCampaignPlanVersion:0,reservation:{kind:"NONE",reservedDelta:"0",accountDayRemainingAfter:"1000"},policy:{currency:"TWD"},warnings:[]};const operation={operationUuid:"22222222-2222-4222-8222-222222222222",operationType:"CREATE_CAMPAIGN",entityType:"CAMPAIGN",entityUuid:entity,status:"FAILED_RETRYABLE",attemptCount:1,reconciliationCount:0,maxAttempts:3,nextAttemptAt:"2999-01-01T00:00:00Z",createdAt:"2000-01-01T00:00:00Z",updatedAt:"2000-01-01T00:00:00Z",version:2};const fetch=vi.fn().mockResolvedValueOnce(response(preview)).mockResolvedValueOnce(response(operation,202,{etag:'W/"2"'})).mockResolvedValueOnce(response({version:0,desiredState:"PAUSED"},200,{etag:'W/"0"'})).mockResolvedValueOnce(response({...operation,status:"UNKNOWN_OUTCOME",nextAttemptAt:undefined},200,{etag:'W/"3"'}));vi.stubGlobal("fetch",fetch);render(<PlatformMetaManager/>);fireEvent.change(screen.getByLabelText("Campaign Plan UUID"),{target:{value:request}});fireEvent.click(screen.getByText("Preview paused Campaign"));await screen.findByText("Confirm FAKE operation");fireEvent.click(screen.getByText("Confirm FAKE operation"));await screen.findByText(/Retry becomes available/);expect(screen.queryByText("Retry due operation")).not.toBeInTheDocument();fireEvent.click(screen.getByText("Refresh operation"));await screen.findByText("Reconcile unknown outcome");fireEvent.click(screen.getByText("Reconcile unknown outcome"));expect(screen.getByRole("dialog",{name:"Confirm reconcile"})).toBeInTheDocument();expect(fetch).toHaveBeenCalledTimes(4);});
 it("invalidates every entity preview before a stale reload and requires a fresh preview",async()=>{const request="11111111-1111-4111-8111-111111111111",campaign="22222222-2222-4222-8222-222222222222",adSet="33333333-3333-4333-8333-333333333333";vi.stubGlobal("crypto",{randomUUID:()=>request});let mutations=0,previews=0;const fetch=vi.fn().mockImplementation(async(input:RequestInfo|URL,init?:RequestInit)=>{const url=String(input),method=init?.method??"GET";if(method==="GET"&&url.includes(`/campaigns/${campaign}`))return response({platformCampaignUuid:campaign,desiredState:"PAUSED",version:1},200,{etag:'W/"1"'});if(method==="GET"&&url.includes(`/ad-sets/${adSet}`))return response({platformAdSetUuid:adSet,desiredState:"PAUSED",budgetAmount:"25",version:1},200,{etag:'W/"1"'});if(url.endsWith("/preview")||url.endsWith("state-preview")||url.endsWith("budget-preview")){previews++;const createAdSet=url.includes("ad-sets/preview");const budget=url.endsWith("budget-preview");return response({clientRequestUuid:request,operationType:createAdSet?"CREATE_AD_SET":budget?"UPDATE_BUDGET":"RESUME",entityType:createAdSet||budget||url.includes("ad-sets")?"AD_SET":"CAMPAIGN",entityUuid:createAdSet?undefined:url.includes("ad-sets")?adSet:campaign,desiredState:"ACTIVE",expectedCampaignPlanVersion:0,expectedEntityVersion:1,reservation:{kind:budget?"INCREASE":"NONE",reservedDelta:budget?"5":"0",accountDayRemainingAfter:"995"},policy:{currency:"TWD"},warnings:[]});}if(method==="POST"){mutations++;return response({code:"PLATFORM_ENTITY_STALE",message:"stale"},412);}throw new Error(`unexpected ${method} ${url}`);});vi.stubGlobal("fetch",fetch);render(<PlatformMetaManager/>);
  fireEvent.change(screen.getByLabelText("Platform Campaign UUID"),{target:{value:campaign}});fireEvent.click(screen.getByText("Load Campaign"));await screen.findByLabelText("Campaign status");fireEvent.click(screen.getByText("Preview paused Ad Set"));await screen.findByRole("dialog",{name:"Confirm platform mutation"});fireEvent.click(screen.getByText("Confirm FAKE operation"));await waitFor(()=>expect(screen.queryByRole("dialog",{name:"Confirm platform mutation"})).not.toBeInTheDocument());
  fireEvent.click(screen.getByText("Preview resume Campaign"));await screen.findByRole("dialog",{name:"Confirm platform mutation"});fireEvent.click(screen.getByText("Confirm FAKE operation"));await waitFor(()=>expect(screen.queryByRole("dialog",{name:"Confirm platform mutation"})).not.toBeInTheDocument());
  fireEvent.change(screen.getByLabelText("Platform Ad Set UUID"),{target:{value:adSet}});fireEvent.click(screen.getByText("Load Ad Set"));await screen.findByLabelText("Ad Set status");fireEvent.click(screen.getByText("Preview resume Ad Set"));await screen.findByRole("dialog",{name:"Confirm platform mutation"});fireEvent.click(screen.getByText("Confirm FAKE operation"));await waitFor(()=>expect(screen.queryByRole("dialog",{name:"Confirm platform mutation"})).not.toBeInTheDocument());
  fireEvent.change(screen.getByLabelText("Daily budget (TWD)"),{target:{value:"30"}});fireEvent.click(screen.getByText("Preview budget change"));await screen.findByRole("dialog",{name:"Confirm platform mutation"});fireEvent.click(screen.getByText("Confirm FAKE operation"));await waitFor(()=>expect(screen.queryByRole("dialog",{name:"Confirm platform mutation"})).not.toBeInTheDocument());expect(mutations).toBe(4);expect(previews).toBe(4);expect(screen.queryByText("Confirm FAKE operation")).not.toBeInTheDocument();
 });
 it("previews and explicitly confirms a paused Ad only when Stage 4C is enabled",async()=>{
  const request="11111111-1111-4111-8111-111111111111",adSet="22222222-2222-4222-8222-222222222222",ad="33333333-3333-4333-8333-333333333333";
  vi.stubGlobal("crypto",{randomUUID:()=>request});
  render(<PlatformMetaManager/>);
  expect(screen.queryByText("Preview paused Ad")).not.toBeInTheDocument();
  cleanup();
  const preview={clientRequestUuid:request,platformAdSetUuid:adSet,expectedParentVersion:1,productUuid:request,assetUuid:request,generationOutputUuid:request,reviewDecisionUuid:request,approvedChecksumFingerprint:"a".repeat(64),creativeMappingKey:"APPROVED_IMAGE_ASSET_V1",parentCampaignDesiredState:"PAUSED",parentAdSetDesiredState:"PAUSED",newAdDesiredState:"PAUSED",evidenceEligible:true,warnings:["DETERMINISTIC_FAKE_ONLY","NO_REAL_PROVIDER_OR_SPEND","EVIDENCE_DIVERGENCE_BLOCKS_CREATE_OR_RESUME"],confirmable:true};
  const fetch=vi.fn().mockResolvedValueOnce(response(preview)).mockResolvedValueOnce(response({operationUuid:ad,operationType:"CREATE_AD",entityType:"AD",entityUuid:ad,status:"SUCCEEDED",attemptCount:1,reconciliationCount:0,maxAttempts:3,createdAt:"2000-01-01T00:00:00Z",updatedAt:"2000-01-01T00:00:00Z",version:2},202,{etag:'W/"2"'})).mockResolvedValueOnce(response({platformAdUuid:ad,desiredState:"PAUSED",version:1,creativeMappingKey:"APPROVED_IMAGE_ASSET_V1",approvedChecksumFingerprint:"b".repeat(64)},200,{etag:'W/"1"'}));
  vi.stubGlobal("fetch",fetch);
  render(<PlatformMetaManager stage4c/>);
  fireEvent.change(screen.getByLabelText("Platform Ad Set UUID"),{target:{value:adSet}});
  fireEvent.change(screen.getByLabelText("Product UUID"),{target:{value:request}});
  fireEvent.change(screen.getByLabelText("Asset UUID"),{target:{value:request}});
  fireEvent.change(screen.getByLabelText("Generation output UUID"),{target:{value:request}});
  fireEvent.change(screen.getByLabelText("Review decision UUID"),{target:{value:request}});
  fireEvent.click(screen.getByText("Preview paused Ad"));
  await screen.findByRole("dialog",{name:"Confirm Ad publication"});
  fireEvent.click(screen.getByText("Confirm FAKE operation"));
  await screen.findByLabelText("Ad status");
  expect(screen.getByText("APPROVED_IMAGE_ASSET_V1")).toBeInTheDocument();
 });
 it("requires explicit Ad pause, resume, stale reload, due retry, unknown reconcile, and never auto-acts",async()=>{
  const request="11111111-1111-4111-8111-111111111111",adSet="22222222-2222-4222-8222-222222222222",ad="33333333-3333-4333-8333-333333333333",operation="55555555-5555-4555-8555-555555555555";
  vi.stubGlobal("crypto",{randomUUID:()=>request});
  let pauses=0,resumes=0,retries=0,reconciles=0,creates=0,adDesired="PAUSED",adVersion=1,lastPauseIfMatch="";
  const fetch=vi.fn().mockImplementation(async(input:RequestInfo|URL,init?:RequestInit)=>{
    const url=String(input),method=init?.method??"GET";
    if(url.includes("/ads/preview"))return response({clientRequestUuid:request,platformAdSetUuid:adSet,expectedParentVersion:1,productUuid:request,assetUuid:request,generationOutputUuid:request,reviewDecisionUuid:request,approvedChecksumFingerprint:"a".repeat(64),creativeMappingKey:"APPROVED_IMAGE_ASSET_V1",parentCampaignDesiredState:"PAUSED",parentAdSetDesiredState:"PAUSED",newAdDesiredState:"PAUSED",evidenceEligible:true,warnings:["DETERMINISTIC_FAKE_ONLY","NO_REAL_PROVIDER_OR_SPEND","EVIDENCE_DIVERGENCE_BLOCKS_CREATE_OR_RESUME"],confirmable:true});
    if(url.endsWith("/ads")&&method==="POST"){creates++;return response({operationUuid:operation,operationType:"CREATE_AD",entityType:"AD",entityUuid:ad,status:"FAILED_RETRYABLE",attemptCount:1,reconciliationCount:0,maxAttempts:3,nextAttemptAt:"2000-01-01T00:00:00Z",createdAt:"2000-01-01T00:00:00Z",updatedAt:"2000-01-01T00:00:00Z",version:2},202,{etag:'W/"2"'});}
    if(url.endsWith("/retry")&&method==="POST"){retries++;return response({operationUuid:operation,operationType:"CREATE_AD",entityType:"AD",entityUuid:ad,status:"UNKNOWN_OUTCOME",attemptCount:2,reconciliationCount:0,maxAttempts:3,createdAt:"2000-01-01T00:00:00Z",updatedAt:"2000-01-01T00:00:00Z",version:3},202,{etag:'W/"3"'});}
    if(url.endsWith("/reconcile")&&method==="POST"){reconciles++;return response({operationUuid:operation,operationType:"CREATE_AD",entityType:"AD",entityUuid:ad,status:"SUCCEEDED",attemptCount:2,reconciliationCount:1,maxAttempts:3,createdAt:"2000-01-01T00:00:00Z",updatedAt:"2000-01-01T00:00:00Z",version:4},202,{etag:'W/"4"'});}
    if(url.includes("/state/preview"))return response({clientRequestUuid:request,entityType:"AD",entityUuid:ad,expectedEntityVersion:adVersion,previousDesiredState:adDesired,targetDesiredState:adDesired==="ACTIVE"?"PAUSED":"ACTIVE",parentCampaignDesiredState:"ACTIVE",parentAdSetDesiredState:"ACTIVE",evidenceEligible:true,warnings:["DETERMINISTIC_FAKE_ONLY","NO_REAL_PROVIDER_OR_SPEND","EVIDENCE_DIVERGENCE_BLOCKS_CREATE_OR_RESUME"],confirmable:true});
    if(url.endsWith("/resume")&&method==="POST"){resumes++;return response({code:"PLATFORM_AD_EVIDENCE_INVALID",message:"The approved Ad evidence is no longer eligible"},409);}
    if(url.endsWith("/pause")&&method==="POST"){pauses++;lastPauseIfMatch=String((init?.headers as Record<string,string>|undefined)?.["If-Match"]??"");return response({code:"PLATFORM_ENTITY_STALE",message:"The platform entity changed; reload and preview again"},412);}
    if(url.includes(`/ads/${ad}`)&&method==="GET")return response({platformAdUuid:ad,platformAdSetUuid:adSet,desiredState:adDesired,version:adVersion,creativeMappingKey:"APPROVED_IMAGE_ASSET_V1",approvedChecksumFingerprint:"b".repeat(64)},200,{etag:`W/"${adVersion}"`});
    throw new Error(`unexpected ${method} ${url}`);
  });
  vi.stubGlobal("fetch",fetch);
  render(<PlatformMetaManager stage4c/>);
  fireEvent.change(screen.getByLabelText("Platform Ad Set UUID"),{target:{value:adSet}});
  fireEvent.change(screen.getByLabelText("Product UUID"),{target:{value:request}});
  fireEvent.change(screen.getByLabelText("Asset UUID"),{target:{value:request}});
  fireEvent.change(screen.getByLabelText("Generation output UUID"),{target:{value:request}});
  fireEvent.change(screen.getByLabelText("Review decision UUID"),{target:{value:request}});
  expect(creates).toBe(0);
  fireEvent.click(screen.getByText("Preview paused Ad"));
  await screen.findByRole("dialog",{name:"Confirm Ad publication"});
  expect(creates).toBe(0);
  fireEvent.click(screen.getByText("Confirm FAKE operation"));
  await screen.findByText("Retry due operation");
  expect(creates).toBe(1);
  fireEvent.click(screen.getByText("Retry due operation"));
  expect(retries).toBe(0);
  fireEvent.click(screen.getByText("Confirm retry"));
  await screen.findByText("Reconcile unknown outcome");
  expect(retries).toBe(1);
  fireEvent.click(screen.getByText("Reconcile unknown outcome"));
  expect(reconciles).toBe(0);
  fireEvent.click(screen.getByText("Confirm reconcile"));
  await waitFor(()=>expect(screen.getByText("SUCCEEDED")).toBeInTheDocument());
  expect(reconciles).toBe(1);
  fireEvent.change(screen.getByLabelText("Platform Ad UUID"),{target:{value:ad}});
  fireEvent.click(screen.getByText("Load Ad"));
  await screen.findByLabelText("Ad status");
  expect(screen.getByText("create and resume then stay blocked", { exact: false })).toBeInTheDocument();
  fireEvent.click(screen.getByText("Preview resume Ad"));
  await screen.findByRole("dialog",{name:"Confirm Ad state"});
  fireEvent.click(screen.getByText("Confirm FAKE operation"));
  await waitFor(()=>expect(screen.getByRole("alert")).toHaveTextContent("no longer eligible"));
  expect(resumes).toBe(1);
  expect(pauses).toBe(0);
  adDesired="ACTIVE";adVersion=2;
  fireEvent.click(screen.getByText("Load Ad"));
  await screen.findByText("Preview pause Ad");
  fireEvent.click(screen.getByText("Preview pause Ad"));
  await screen.findByRole("dialog",{name:"Confirm Ad state"});
  expect(pauses).toBe(0);
  fireEvent.click(screen.getByText("Confirm FAKE operation"));
  await waitFor(()=>expect(screen.getByRole("alert")).toHaveTextContent("reload and preview again"));
  expect(pauses).toBe(1);
  expect(lastPauseIfMatch).toBe('W/"2"');
 });
 it("loads delivery and metrics only after explicit GET and requires a second confirmation for refresh",async()=>{
  const entity="11111111-1111-4111-8111-111111111111";
  let posts=0;
  const fetch=vi.fn().mockImplementation(async(input:RequestInfo|URL,init?:RequestInit)=>{
    const url=String(input),method=init?.method??"GET";
    if(method==="POST"){posts++;return response({syncEligible:true,refreshEligible:true,confirmable:true,warnings:["DETERMINISTIC_FAKE_ONLY","NO_REAL_PROVIDER_OR_SPEND","NULL_METRICS_MEAN_UNKNOWN"],entityType:"CAMPAIGN",entityUuid:entity,desiredState:"PAUSED",observedState:"PAUSED",present:true,freshnessStatus:"FRESH",windowStart:"2026-08-21T16:00:00Z",windowEnd:"2026-08-22T16:00:00Z",impressions:10000,spend:"25.000000",roas:"4.000000"});}
    if(url.endsWith("/delivery"))return response({entityType:"CAMPAIGN",entityUuid:entity,desiredState:"PAUSED",observedState:"PAUSED",updatedAt:"2026-08-22T00:00:00Z",version:1});
    if(url.includes("/metrics"))return response({entityType:"CAMPAIGN",entityUuid:entity,present:true,freshnessStatus:"FRESH",windowStart:"2026-08-21T16:00:00Z",windowEnd:"2026-08-22T16:00:00Z",impressions:10000,spend:"25.000000",roas:"4.000000",warnings:["DETERMINISTIC_FAKE_ONLY","NO_REAL_PROVIDER_OR_SPEND","NULL_METRICS_MEAN_UNKNOWN"]});
    throw new Error(`unexpected ${method} ${url}`);
  });
  vi.stubGlobal("fetch",fetch);
  render(<PlatformMetaManager/>);
  expect(screen.queryByText("Load delivery and metrics")).not.toBeInTheDocument();
  cleanup();
  render(<PlatformMetaManager stage4d/>);
  expect(posts).toBe(0);
  expect(screen.getByText("Null metrics mean unknown",{exact:false})).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText("Platform entity UUID"),{target:{value:entity}});
  fireEvent.click(screen.getByText("Load delivery and metrics"));
  await screen.findByLabelText("Delivery status");
  expect(posts).toBe(0);
  fireEvent.click(screen.getByText("Preview metrics refresh"));
  await screen.findByRole("dialog",{name:"Confirm metrics refresh"});
  expect(posts).toBe(1);
  fireEvent.click(screen.getByText("Confirm metrics refresh"));
  await waitFor(()=>expect(posts).toBe(2));
  const confirm=fetch.mock.calls.find(call=>String(call[0]).endsWith("/metrics-refresh")&&(call[1]?.method??"GET")==="POST");
  expect(confirm?.[1]?.headers).toBeUndefined();
 });
 it("posts Google preview-confirm and operations to the Google base paths",async()=>{
  const request="11111111-1111-4111-8111-111111111111",operation="22222222-2222-4222-8222-222222222222",entity="33333333-3333-4333-8333-333333333333";
  vi.stubGlobal("crypto",{randomUUID:()=>request});
  const preview={clientRequestUuid:request,operationType:"CREATE_CAMPAIGN",entityType:"CAMPAIGN",desiredState:"PAUSED",expectedCampaignPlanVersion:0,reservation:{kind:"NONE",reservedDelta:"0",accountDayRemainingAfter:"1000"},policy:{currency:"TWD"},warnings:[]};
  const created={operationUuid:operation,operationType:"CREATE_CAMPAIGN",entityType:"CAMPAIGN",entityUuid:entity,status:"SUCCEEDED",attemptCount:1,reconciliationCount:0,maxAttempts:3,createdAt:"2000-01-01T00:00:00Z",updatedAt:"2000-01-01T00:00:00Z",version:2};
  const fetch=vi.fn().mockResolvedValueOnce(response(preview)).mockResolvedValueOnce(response(created,202,{etag:'W/"2"'})).mockResolvedValueOnce(response({version:0,desiredState:"PAUSED"},200,{etag:'W/"0"'}));
  vi.stubGlobal("fetch",fetch);
  render(<PlatformMetaManager title="Google platform operations" apiBase="/api/platforms/google" operationsBase="/api/platforms/google/operations"/>);
  expect(screen.getByRole("heading",{name:"Google platform operations"})).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText("Campaign Plan UUID"),{target:{value:request}});
  fireEvent.click(screen.getByText("Preview paused Campaign"));
  await screen.findByText("Confirm FAKE operation");
  fireEvent.click(screen.getByText("Confirm FAKE operation"));
  await screen.findByText("SUCCEEDED");
  expect(String(fetch.mock.calls[0][0])).toBe("/api/platforms/google/campaigns/preview");
  expect(String(fetch.mock.calls[1][0])).toBe("/api/platforms/google/campaigns");
  expect(String(fetch.mock.calls[2][0])).toBe(`/api/platforms/google/campaigns/${entity}`);
 });
});

function response(body:unknown,status=200,headers:Record<string,string>={}){return new Response(JSON.stringify(body),{status,headers:{"content-type":"application/json",...headers}});}
