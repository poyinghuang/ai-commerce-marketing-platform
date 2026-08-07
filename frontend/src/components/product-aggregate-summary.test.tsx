import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ProductAggregateSummary } from "./product-aggregate-summary";

const PRODUCT = "d4476a19-30ed-48d9-a518-f9b111bd0911";
const aggregate = {
  product: { productUuid: PRODUCT },
  knowledge: [{ title: "Product benefit" }],
  creativePlans: [{ planName: "Launch visual" }],
  campaigns: [{ campaignName: "Summer launch" }],
  assets: [{ assetType: "IMAGE", purpose: "Hero", originalFilename: "hero.jpg" }],
};

describe("ProductAggregateSummary", () => {
  afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

  it("renders loading and populated member labels", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify(aggregate), { status: 200 })));
    render(<ProductAggregateSummary productUuid={PRODUCT} />);
    expect(screen.getByRole("status")).toHaveTextContent("正在載入整合摘要");
    expect(await screen.findByText("Product benefit")).toBeInTheDocument();
    expect(screen.getByText("Launch visual")).toBeInTheDocument();
    expect(screen.getByText("Summer launch")).toBeInTheDocument();
    expect(screen.getByText("hero.jpg")).toBeInTheDocument();
  });

  it("renders empty groups", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({
      ...aggregate, knowledge: [], creativePlans: [], campaigns: [], assets: [],
    }), { status: 200 })));
    render(<ProductAggregateSummary productUuid={PRODUCT} />);
    await waitFor(() => expect(screen.getAllByText("尚無資料")).toHaveLength(4));
  });

  it("reloads with includeArchived and supports error retry", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ message: "Backend unavailable" }), { status: 503 }))
      .mockResolvedValue(new Response(JSON.stringify(aggregate), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    render(<ProductAggregateSummary productUuid={PRODUCT} />);
    expect(await screen.findByRole("alert")).toHaveTextContent("Backend unavailable");
    fireEvent.click(screen.getByRole("button", { name: "重試" }));
    expect(await screen.findByText("Product benefit")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("checkbox", { name: "包含已封存項目" }));
    await waitFor(() => expect(fetchMock.mock.calls.some((call) =>
      String(call[0]).includes("includeArchived=true"))).toBe(true));
  });
});
