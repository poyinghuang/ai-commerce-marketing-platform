import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { QualityTab } from "./quality-tab";

const productUuid = "d4476a19-30ed-48d9-a518-f9b111bd0911";
const quality = {
  productUuid,
  productMasterScore: 35,
  productKnowledgeScore: 20,
  creativePlanScore: 20,
  assetMetadataScore: 10,
  campaignReadinessScore: 5,
  systemScore: 90,
  aiSuggestedScore: null,
  manualAdjustment: 0,
  manualAdjustmentReason: null,
  manualAdjustedBy: null,
  manualAdjustedAt: null,
  finalScore: 90,
  blockers: [{ code: "KNOWLEDGE_MISSING", field: "knowledge", message: "Product knowledge is missing" }],
  readinessStatus: "NEEDS_REVIEW",
  statusReason: "Blocked: KNOWLEDGE_MISSING",
  calculatedAt: "2026-08-08T00:00:00Z",
  version: 2,
};

describe("QualityTab", () => {
  afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

  it("renders score breakdown, readiness, and blockers", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response(quality, 'W/"2"')));
    render(<QualityTab productUuid={productUuid} productArchived={false} />);

    expect(await screen.findByRole("heading", { name: "Quality Score" })).toBeInTheDocument();
    expect(screen.getByLabelText("Final score 90 out of 100")).toHaveTextContent("Needs review");
    expect(screen.getByLabelText("Product Master 35 out of 35")).toBeInTheDocument();
    expect(screen.getByText("KNOWLEDGE_MISSING")).toBeInTheDocument();
    expect(screen.getByText(/Manual adjustment 不會移除/)).toBeInTheDocument();
  });

  it("submits a trimmed adjustment with the current ETag and updates the view", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response(quality, 'W/"2"'))
      .mockResolvedValueOnce(response({ ...quality, manualAdjustment: 5, manualAdjustmentReason: "Reviewed", manualAdjustedBy: "local-admin", finalScore: 95, version: 3 }, 'W/"3"'));
    vi.stubGlobal("fetch", fetchMock);
    render(<QualityTab productUuid={productUuid} productArchived={false} />);
    await screen.findByRole("heading", { name: "Quality Score" });
    fireEvent.change(screen.getByLabelText("調整分數"), { target: { value: "5" } });
    fireEvent.change(screen.getByLabelText("調整理由"), { target: { value: "  Reviewed  " } });
    fireEvent.click(screen.getByRole("button", { name: "儲存調整" }));

    await waitFor(() => expect(screen.getByLabelText("Final score 95 out of 100")).toBeInTheDocument());
    const [, init] = fetchMock.mock.calls[1] as [string, RequestInit];
    expect(init.headers).toEqual(expect.objectContaining({ "If-Match": 'W/"2"', "Content-Type": "application/merge-patch+json" }));
    expect(JSON.parse(String(init.body))).toEqual({ manualAdjustment: 5, reason: "Reviewed" });
  });

  it.each([
    [409, "商品已封存"],
    [412, "已被其他操作更新"],
    [428, "缺少有效版本資訊"],
  ])("provides recovery for HTTP %s", async (status, message) => {
    const fetchMock = vi.fn().mockResolvedValueOnce(response(quality, 'W/"2"'))
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: "ERROR" }), { status }));
    vi.stubGlobal("fetch", fetchMock);
    render(<QualityTab productUuid={productUuid} productArchived={false} />);
    await screen.findByRole("heading", { name: "Quality Score" });
    fireEvent.change(screen.getByLabelText("調整分數"), { target: { value: "1" } });
    fireEvent.change(screen.getByLabelText("調整理由"), { target: { value: "Review" } });
    fireEvent.click(screen.getByRole("button", { name: "儲存調整" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(message);
    expect(screen.getByRole("button", { name: "重新載入" })).toBeInTheDocument();
  });

  it("keeps archived products read-only", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response({ ...quality, readinessStatus: "DRAFT" }, 'W/"2"')));
    render(<QualityTab productUuid={productUuid} productArchived />);
    expect(await screen.findByText(/商品已封存；Quality 可檢視/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "儲存調整" })).not.toBeInTheDocument();
  });

  it("becomes read-only after a 409 reload reveals the archived blocker", async () => {
    const archived = {
      ...quality,
      readinessStatus: "DRAFT",
      blockers: [...quality.blockers, { code: "PRODUCT_ARCHIVED", field: "product.lifecycleStatus", message: "Product is archived" }],
    };
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response(quality, 'W/"2"'))
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: "PRODUCT_ARCHIVED" }), { status: 409 }))
      .mockResolvedValueOnce(response(archived, 'W/"3"'));
    vi.stubGlobal("fetch", fetchMock);
    render(<QualityTab productUuid={productUuid} productArchived={false} />);
    await screen.findByRole("heading", { name: "Quality Score" });
    fireEvent.change(screen.getByLabelText("調整分數"), { target: { value: "1" } });
    fireEvent.change(screen.getByLabelText("調整理由"), { target: { value: "Review" } });
    fireEvent.click(screen.getByRole("button", { name: "儲存調整" }));
    fireEvent.click(await screen.findByRole("button", { name: "重新載入" }));

    expect(await screen.findByText(/商品已封存；Quality 可檢視/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "儲存調整" })).not.toBeInTheDocument();
  });
});

function response(body: object, etag: string) {
  return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json", ETag: etag } });
}
