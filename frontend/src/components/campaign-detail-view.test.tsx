import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CampaignDetailView } from "./campaign-detail-view";

const campaign = { campaignUuid: "79be8758-1f0d-4ca5-bad6-f51aa923cdb9", campaignName: "Launch", activityType: null, startDate: null, endDate: null, objective: null, platform: null, budgetDaily: null, budgetTotal: null, currency: null, promotion: null, landingPage: null, lifecycleStatus: "ACTIVE", archivedAt: null, createdAt: "2026-08-07T00:00:00Z", updatedAt: "2026-08-07T00:00:00Z", version: 0 };
const associations = { content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, sort: { field: "updatedAt", direction: "desc" } };

describe("CampaignDetailView", () => {
  afterEach(() => { cleanup(); vi.unstubAllGlobals(); });
  it.each([409, 412, 428])("shows reload recovery for HTTP %s", async (status) => {
    const fetchMock = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify(campaign), { status: 200, headers: { ETag: 'W/"0"' } })).mockResolvedValueOnce(new Response(JSON.stringify(associations), { status: 200 })).mockResolvedValueOnce(new Response(JSON.stringify({ code: "PRECONDITION_FAILED" }), { status }));
    vi.stubGlobal("fetch", fetchMock);
    render(<CampaignDetailView campaignUuid={campaign.campaignUuid} />);
    await waitFor(() => screen.getByDisplayValue("Launch"));
    fireEvent.change(screen.getByLabelText("活動名稱"), { target: { value: "Updated" } });
    fireEvent.click(screen.getByRole("button", { name: "儲存 Campaign" }));
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("資料已由其他使用者變更"));
    expect(fetchMock.mock.calls[2][1]).toEqual(expect.objectContaining({ method: "PATCH", headers: expect.objectContaining({ "If-Match": 'W/"0"' }) }));
  });
  it("renders archived Campaign read-only state", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ ...campaign, lifecycleStatus: "ARCHIVED" }), { status: 200, headers: { ETag: 'W/"1"' } })).mockResolvedValueOnce(new Response(JSON.stringify(associations), { status: 200 })));
    render(<CampaignDetailView campaignUuid={campaign.campaignUuid} />);
    await waitFor(() => expect(screen.getAllByText(/Archived Campaign 僅供閱讀/).length).toBeGreaterThan(0));
    expect(screen.getByRole("button", { name: "儲存 Campaign" })).toBeDisabled();
  });
});
