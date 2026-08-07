import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CampaignListView } from "./campaign-list-view";

const push = vi.fn();
let currentSearch = new URLSearchParams();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }), useSearchParams: () => currentSearch }));

const campaign = (status: "ACTIVE" | "ARCHIVED" = "ACTIVE") => ({
  campaignUuid: "79be8758-1f0d-4ca5-bad6-f51aa923cdb9", campaignName: "Launch", activityType: null,
  startDate: null, endDate: null, objective: null, platform: null, budgetDaily: null,
  budgetTotal: null, currency: null, promotion: null, landingPage: null, lifecycleStatus: status,
  archivedAt: status === "ARCHIVED" ? "2026-08-07T00:00:00Z" : null,
  createdAt: "2026-08-07T00:00:00Z", updatedAt: "2026-08-07T00:00:00Z", version: 0,
});
const page = (content: ReturnType<typeof campaign>[], number = 0, totalPages = 1) => ({
  content, page: number, size: 20, totalElements: content.length, totalPages,
  sort: { field: "updatedAt", direction: "desc" },
});

describe("CampaignListView", () => {
  afterEach(() => { cleanup(); vi.unstubAllGlobals(); push.mockReset(); currentSearch = new URLSearchParams(); });
  it("renders loading then empty state", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, sort: { field: "updatedAt", direction: "desc" } }), { status: 200 })));
    render(<CampaignListView />);
    expect(screen.getByRole("status")).toHaveTextContent("載入 Campaigns");
    await waitFor(() => expect(screen.getByText("目前沒有 Campaign。")).toBeInTheDocument());
  });
  it("renders backend errors", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: "offline" }), { status: 503 })));
    render(<CampaignListView />);
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("offline"));
  });

  it("applies allowlisted filters and pagination through router state", async () => {
    currentSearch = new URLSearchParams("page=1&size=20");
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify(page([campaign()], 1, 3)), { status: 200 })));
    render(<CampaignListView />);
    await waitFor(() => screen.getByText("Launch"));
    fireEvent.change(screen.getByPlaceholderText("搜尋 Campaign"), { target: { value: "summer" } });
    fireEvent.change(screen.getByDisplayValue("ACTIVE"), { target: { value: "ALL" } });
    fireEvent.click(screen.getByRole("button", { name: "套用" }));
    expect(push).toHaveBeenCalledWith(expect.stringContaining("keyword=summer"));
    expect(push).toHaveBeenCalledWith(expect.stringContaining("status=ALL"));
    fireEvent.click(screen.getByRole("button", { name: "下一頁" }));
    expect(push).toHaveBeenLastCalledWith("/campaigns?page=2&size=20");
  });

  it.each([409, 412, 428])("offers reload recovery when archive returns %s", async (status) => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(page([campaign()])), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(campaign()), { status: 200, headers: { ETag: 'W/"0"' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: "CONFLICT" }), { status }))
      .mockResolvedValueOnce(new Response(JSON.stringify(page([campaign()])), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    render(<CampaignListView />);
    await waitFor(() => screen.getByText("Launch"));
    fireEvent.click(screen.getByRole("button", { name: "封存" }));
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("Campaign 已變更"));
    fireEvent.click(screen.getByRole("button", { name: "重新載入" }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(4));
  });

  it("restores an archived Campaign with the latest ETag", async () => {
    const archived = campaign("ARCHIVED");
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(page([archived])), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(archived), { status: 200, headers: { ETag: 'W/"2"' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify(campaign()), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(page([campaign()])), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    render(<CampaignListView />);
    await waitFor(() => screen.getByText("Launch"));
    fireEvent.click(screen.getByRole("button", { name: "還原" }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(4));
    expect(fetchMock.mock.calls[2]).toEqual([
      `/api/campaigns/${archived.campaignUuid}/restore`,
      expect.objectContaining({ method: "POST", headers: { "If-Match": 'W/"2"' } }),
    ]);
  });
});
