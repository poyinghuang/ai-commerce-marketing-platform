import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CampaignProducts } from "./campaign-products";

const CAMPAIGN = "79be8758-1f0d-4ca5-bad6-f51aa923cdb9";
const PRODUCT = "d4476a19-30ed-48d9-a518-f9b111bd0911";
const association = (status: "ACTIVE" | "ARCHIVED" = "ACTIVE") => ({
  campaignProductUuid: "715561c4-2e66-48b4-96bb-c565726fe348", campaignUuid: CAMPAIGN,
  productUuid: PRODUCT, role: "Hero", priority: 1, budgetWeight: "50.00",
  lifecycleStatus: status, archivedAt: status === "ARCHIVED" ? "2026-08-07T00:00:00Z" : null,
  createdAt: "2026-08-07T00:00:00Z", updatedAt: "2026-08-07T00:00:00Z", version: 0,
});
const page = (content: ReturnType<typeof association>[], pageNumber = 0, totalPages = 1) => ({
  content, page: pageNumber, size: 10, totalElements: content.length, totalPages,
  sort: { field: "updatedAt", direction: "desc" },
});

describe("CampaignProducts", () => {
  afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

  it("renders loading then empty state", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify(page([])), { status: 200 })));
    render(<CampaignProducts campaignUuid={CAMPAIGN} campaignArchived={false} />);
    expect(screen.getByRole("status")).toHaveTextContent("載入商品關聯");
    await waitFor(() => expect(screen.getByText("目前沒有商品關聯。")).toBeInTheDocument());
  });

  it("renders initial load errors", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: "offline" }), { status: 503 })));
    render(<CampaignProducts campaignUuid={CAMPAIGN} campaignArchived={false} />);
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("offline"));
  });

  it("adds a product association and reloads the collection", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(page([])), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(association()), { status: 201 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(page([association()])), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    render(<CampaignProducts campaignUuid={CAMPAIGN} campaignArchived={false} />);
    await waitFor(() => screen.getByText("目前沒有商品關聯。"));
    fireEvent.change(screen.getByLabelText("Product UUID"), { target: { value: PRODUCT } });
    fireEvent.change(screen.getByLabelText("角色"), { target: { value: "Hero" } });
    fireEvent.click(screen.getByRole("button", { name: "儲存關聯" }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(3));
    expect(fetchMock.mock.calls[1][0]).toBe(`/api/campaigns/${CAMPAIGN}/products`);
    expect(fetchMock.mock.calls[1][1]).toEqual(expect.objectContaining({ method: "POST" }));
  });

  it("edits a relationship with its ETag", async () => {
    const item = association();
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(page([item])), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(item), { status: 200, headers: { ETag: 'W/"0"' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ ...item, role: "Support", version: 1 }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(page([{ ...item, role: "Support", version: 1 }])), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    render(<CampaignProducts campaignUuid={CAMPAIGN} campaignArchived={false} />);
    await waitFor(() => screen.getByText(PRODUCT));
    fireEvent.click(screen.getByRole("button", { name: new RegExp(PRODUCT) }));
    await waitFor(() => expect(screen.getByText("編輯商品關聯")).toBeInTheDocument());
    fireEvent.change(screen.getByLabelText("角色"), { target: { value: "Support" } });
    fireEvent.click(screen.getByRole("button", { name: "儲存關聯" }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(4));
    expect(fetchMock.mock.calls[2][1]).toEqual(expect.objectContaining({ method: "PATCH", headers: expect.objectContaining({ "If-Match": 'W/"0"' }) }));
  });

  it.each([
    ["ACTIVE", "封存", "DELETE", `/api/campaigns/${CAMPAIGN}/products/${PRODUCT}`],
    ["ARCHIVED", "還原", "POST", `/api/campaigns/${CAMPAIGN}/products/${PRODUCT}/restore`],
  ] as const)("changes association lifecycle from %s", async (status, label, method, target) => {
    const item = association(status);
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(page([item])), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(item), { status: 200, headers: { ETag: 'W/"1"' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify(item), { status: 200, headers: { ETag: 'W/"1"' } }))
      .mockResolvedValueOnce(new Response(method === "DELETE" ? null : JSON.stringify(item), { status: method === "DELETE" ? 204 : 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(page([])), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    render(<CampaignProducts campaignUuid={CAMPAIGN} campaignArchived={false} />);
    await waitFor(() => screen.getByText(PRODUCT));
    fireEvent.click(screen.getByRole("button", { name: label }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(5));
    expect(fetchMock.mock.calls[3]).toEqual([target, expect.objectContaining({ method, headers: { "If-Match": 'W/"1"' } })]);
  });

  it.each([409, 412, 428])("offers reload recovery for save status %s", async (status) => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(page([])), { status: 200 }))
      .mockResolvedValueOnce(new Response(null, { status }));
    vi.stubGlobal("fetch", fetchMock);
    render(<CampaignProducts campaignUuid={CAMPAIGN} campaignArchived={false} />);
    await waitFor(() => screen.getByText("目前沒有商品關聯。"));
    fireEvent.change(screen.getByLabelText("Product UUID"), { target: { value: PRODUCT } });
    fireEvent.click(screen.getByRole("button", { name: "儲存關聯" }));
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("資料已變更"));
  });

  it("is read-only when the Campaign is archived", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify(page([association()])), { status: 200 })));
    render(<CampaignProducts campaignUuid={CAMPAIGN} campaignArchived />);
    await waitFor(() => screen.getByText(PRODUCT));
    expect(screen.getByText(/Archived Campaign 僅供閱讀/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "封存" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "儲存關聯" })).not.toBeInTheDocument();
  });
});
