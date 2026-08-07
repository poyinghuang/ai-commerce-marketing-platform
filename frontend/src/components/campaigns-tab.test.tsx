import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CampaignsTab } from "./campaigns-tab";

const PRODUCT = "d4476a19-30ed-48d9-a518-f9b111bd0911";
const CAMPAIGN = "79be8758-1f0d-4ca5-bad6-f51aa923cdb9";
const relation = (status: "ACTIVE" | "ARCHIVED" = "ACTIVE") => ({
  campaignProductUuid: "715561c4-2e66-48b4-96bb-c565726fe348", campaignUuid: CAMPAIGN,
  productUuid: PRODUCT, role: "Hero", priority: 1, budgetWeight: "50.00",
  lifecycleStatus: status, archivedAt: status === "ARCHIVED" ? "2026-08-07T00:00:00Z" : null,
  createdAt: "2026-08-07T00:00:00Z", updatedAt: "2026-08-07T00:00:00Z", version: 0,
});
const campaign = (association = relation()) => ({
  campaignUuid: CAMPAIGN, campaignName: "Launch", activityType: null, startDate: null, endDate: null,
  objective: null, platform: null, budgetDaily: null, budgetTotal: null, currency: null,
  promotion: null, landingPage: null, lifecycleStatus: "ACTIVE", archivedAt: null,
  createdAt: "2026-08-07T00:00:00Z", updatedAt: "2026-08-07T00:00:00Z", version: 0,
  association,
});
const page = (content: ReturnType<typeof campaign>[], number = 0, totalPages = 1) => ({
  content, page: number, size: 10, totalElements: content.length, totalPages,
  sort: { field: "updatedAt", direction: "desc" },
});

describe("CampaignsTab", () => {
  afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

  it("renders loading, empty and error states", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify(page([])), { status: 200 })));
    const view = render(<CampaignsTab productUuid={PRODUCT} productArchived={false} />);
    expect(screen.getByRole("status")).toHaveTextContent("載入 Campaign 關聯");
    await waitFor(() => screen.getByText("此商品目前沒有 Campaign 關聯。"));
    view.unmount();
    cleanup();
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: "offline" }), { status: 503 })));
    render(<CampaignsTab productUuid={PRODUCT} productArchived={false} />);
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("offline"));
  });

  it("adds and edits a Campaign association", async () => {
    const item = campaign();
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(page([])), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(relation()), { status: 201 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(page([item])), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(relation()), { status: 200, headers: { ETag: 'W/"0"' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ ...relation(), role: "Support" }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(page([campaign({ ...relation(), role: "Support" })])), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    render(<CampaignsTab productUuid={PRODUCT} productArchived={false} />);
    await waitFor(() => screen.getByText("此商品目前沒有 Campaign 關聯。"));
    fireEvent.change(screen.getByLabelText("Campaign UUID"), { target: { value: CAMPAIGN } });
    fireEvent.click(screen.getByRole("button", { name: "儲存關聯" }));
    await waitFor(() => screen.getByText("Launch"));
    fireEvent.click(screen.getByRole("button", { name: "編輯" }));
    await waitFor(() => screen.getByText("編輯 Launch"));
    fireEvent.change(screen.getByLabelText("角色"), { target: { value: "Support" } });
    fireEvent.click(screen.getByRole("button", { name: "儲存關聯" }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(6));
    expect(fetchMock.mock.calls[4][1]).toEqual(expect.objectContaining({ method: "PATCH", headers: expect.objectContaining({ "If-Match": 'W/"0"' }) }));
  });

  it.each([
    ["ACTIVE", "封存", "DELETE", `/api/campaigns/${CAMPAIGN}/products/${PRODUCT}`],
    ["ARCHIVED", "還原", "POST", `/api/campaigns/${CAMPAIGN}/products/${PRODUCT}/restore`],
  ] as const)("changes Product-side association lifecycle from %s", async (status, label, method, target) => {
    const association = relation(status);
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(page([campaign(association)])), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(association), { status: 200, headers: { ETag: 'W/"2"' } }))
      .mockResolvedValueOnce(new Response(method === "DELETE" ? null : JSON.stringify(association), { status: method === "DELETE" ? 204 : 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(page([])), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    render(<CampaignsTab productUuid={PRODUCT} productArchived={false} />);
    await waitFor(() => screen.getByText("Launch"));
    fireEvent.click(screen.getByRole("button", { name: label }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(4));
    expect(fetchMock.mock.calls[2]).toEqual([target, expect.objectContaining({ method, headers: { "If-Match": 'W/"2"' } })]);
  });

  it("supports association status filtering and pagination", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(page([campaign()], 0, 2)), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(page([campaign(relation("ARCHIVED"))], 0, 2)), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(page([], 1, 2)), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    render(<CampaignsTab productUuid={PRODUCT} productArchived={false} />);
    await waitFor(() => screen.getByText("Launch"));
    fireEvent.change(screen.getByLabelText("Campaign 關聯狀態"), { target: { value: "ARCHIVED" } });
    await waitFor(() => expect(fetchMock.mock.calls[1][0]).toContain("associationStatus=ARCHIVED"));
    fireEvent.click(screen.getByRole("button", { name: "下一頁" }));
    await waitFor(() => expect(fetchMock.mock.calls[2][0]).toContain("page=1"));
  });

  it.each([409, 412, 428])("offers reload recovery for association save status %s", async (status) => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(page([])), { status: 200 }))
      .mockResolvedValueOnce(new Response(null, { status }));
    vi.stubGlobal("fetch", fetchMock);
    render(<CampaignsTab productUuid={PRODUCT} productArchived={false} />);
    await waitFor(() => screen.getByText("此商品目前沒有 Campaign 關聯。"));
    fireEvent.change(screen.getByLabelText("Campaign UUID"), { target: { value: CAMPAIGN } });
    fireEvent.click(screen.getByRole("button", { name: "儲存關聯" }));
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("請重新載入"));
  });

  it("is read-only when the Product is archived", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify(page([campaign()])), { status: 200 })));
    render(<CampaignsTab productUuid={PRODUCT} productArchived />);
    await waitFor(() => screen.getByText("Launch"));
    expect(screen.getByText(/Archived Product 僅供閱讀/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "編輯" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "封存" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "儲存關聯" })).not.toBeInTheDocument();
  });
});
