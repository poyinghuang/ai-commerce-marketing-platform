import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { PlatformMetaManager } from "./platform-meta-manager";

describe("PlatformMetaManager",()=>{
 it("requires preview and an explicit second confirmation",async()=>{const request="11111111-1111-4111-8111-111111111111";vi.stubGlobal("crypto",{randomUUID:()=>request});const fetch=vi.fn().mockResolvedValue(new Response(JSON.stringify({clientRequestUuid:request,operationType:"CREATE_CAMPAIGN",entityType:"CAMPAIGN",desiredState:"PAUSED",expectedCampaignPlanVersion:0,reservation:{kind:"NONE",reservedDelta:"0",accountDayRemainingAfter:"1000"},policy:{currency:"TWD",maxBatchAmount:"300",maxAccountDayAmount:"1000"},warnings:[]}),{status:200,headers:{"content-type":"application/json"}}));vi.stubGlobal("fetch",fetch);render(<PlatformMetaManager/>);expect(screen.queryByText("Confirm FAKE operation")).not.toBeInTheDocument();fireEvent.change(screen.getByLabelText("Campaign Plan UUID"),{target:{value:request}});fireEvent.click(screen.getByText("Preview paused Campaign"));await waitFor(()=>expect(screen.getByText("Confirm FAKE operation")).toBeInTheDocument());expect(fetch).toHaveBeenCalledTimes(1);});
});
