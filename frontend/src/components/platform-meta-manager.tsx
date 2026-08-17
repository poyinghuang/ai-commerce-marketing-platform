"use client";

import { FormEvent, useState } from "react";
import type { PlatformOperationView } from "@/lib/platform-stage4b";

type Preview={clientRequestUuid:string;operationType:string;entityType:string;desiredState:string;budgetType?:string;budgetAmount?:string;reservation:{kind:string;reservedDelta:string;accountDayRemainingAfter:string};policy:{currency:string;maxBatchAmount:string;maxAccountDayAmount:string};warnings:string[];expectedCampaignPlanVersion?:number;expectedEntityVersion?:number};
const uuid=()=>crypto.randomUUID();

export function PlatformMetaManager(){
 const [campaignPlan,setCampaignPlan]=useState("");const [campaign,setCampaign]=useState("");const [campaignEtag,setCampaignEtag]=useState("");
 const [budget,setBudget]=useState("50");const [preview,setPreview]=useState<Preview|null>(null);const [operation,setOperation]=useState<PlatformOperationView|null>(null);const [error,setError]=useState("");
 async function call(path:string,body?:unknown,etag?:string){setError("");const headers:Record<string,string>={};if(body)headers["Content-Type"]="application/json";if(etag)headers["If-Match"]=etag;const response=await fetch(path,{method:body?"POST":"GET",headers,body:body?JSON.stringify(body):undefined});const data=await response.json();if(!response.ok){setError(data.message??"Request failed");throw new Error(data.code);}return {data,response};}
 async function campaignPreview(e:FormEvent){e.preventDefault();const request=uuid();const {data}=await call("/api/platforms/meta/campaigns/preview",{clientRequestUuid:request,campaignUuid:campaignPlan});setPreview(data);}
 async function confirmCampaign(){if(!preview)return;const {data}=await call("/api/platforms/meta/campaigns",{clientRequestUuid:preview.clientRequestUuid,campaignUuid:campaignPlan,expectedCampaignPlanVersion:preview.expectedCampaignPlanVersion});setOperation(data);setCampaign(data.entityUuid);const read=await fetch(`/api/platforms/meta/campaigns/${data.entityUuid}`);setCampaignEtag(read.headers.get("etag")??"");}
 async function adSetPreview(e:FormEvent){e.preventDefault();const {data}=await call(`/api/platforms/meta/campaigns/${campaign}/ad-sets/preview`,{clientRequestUuid:uuid(),budgetType:"DAILY",budgetAmount:budget});setPreview(data);}
 async function confirmAdSet(){if(!preview)return;const {data}=await call(`/api/platforms/meta/campaigns/${campaign}/ad-sets`,{clientRequestUuid:preview.clientRequestUuid,budgetType:"DAILY",budgetAmount:budget,expectedCampaignPlanVersion:preview.expectedCampaignPlanVersion},campaignEtag);setOperation(data);}
 return <main className="page-shell"><header className="page-header"><div><p className="eyebrow">Deterministic FAKE · Local/Test</p><h1>Meta platform operations</h1><p>Create paused Campaign and Ad Set operations with explicit preview and confirmation.</p></div></header>
  {error&&<div className="error-banner" role="alert">{error}</div>}
  <section className="panel"><h2>1. Campaign</h2><form onSubmit={campaignPreview}><label>Campaign Plan UUID<input value={campaignPlan} onChange={e=>setCampaignPlan(e.target.value)} required /></label><button type="submit">Preview paused Campaign</button></form>{preview?.operationType==="CREATE_CAMPAIGN"&&<Confirmation preview={preview} onConfirm={confirmCampaign}/>}</section>
  <section className="panel"><h2>2. Ad Set</h2><form onSubmit={adSetPreview}><label>Platform Campaign UUID<input value={campaign} onChange={e=>setCampaign(e.target.value)} required /></label><label>Daily budget (TWD)<input value={budget} onChange={e=>setBudget(e.target.value)} inputMode="decimal" required /></label><button type="submit">Preview paused Ad Set</button></form>{preview?.operationType==="CREATE_AD_SET"&&<Confirmation preview={preview} onConfirm={confirmAdSet}/>}</section>
  {operation&&<section className="panel" aria-live="polite"><h2>Operation status</h2><dl><dt>Operation</dt><dd>{operation.operationUuid}</dd><dt>Entity</dt><dd>{operation.entityUuid}</dd><dt>Status</dt><dd><strong>{operation.status}</strong></dd><dt>Attempts</dt><dd>{operation.attemptCount} / {operation.maxAttempts}</dd></dl></section>}
 </main>;
}
function Confirmation({preview,onConfirm}:{preview:Preview,onConfirm:()=>void}){return <div className="confirmation-card" role="dialog" aria-label="Confirm platform mutation"><h3>Explicit confirmation required</h3><p>Desired state: <strong>{preview.desiredState}</strong></p><p>Authorization: {preview.policy.currency} {preview.reservation.reservedDelta}; remaining today {preview.reservation.accountDayRemainingAfter}</p><p>Capacity is conservative: decreases and failures do not release it.</p><button type="button" onClick={onConfirm}>Confirm FAKE operation</button></div>}
