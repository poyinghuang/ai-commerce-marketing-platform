import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ProductDetailView } from "./product-detail-view";

const product = {
  productUuid: "d4476a19-30ed-48d9-a518-f9b111bd0911",
  productId: "PROD-00000001",
  sku: "SKU-1",
  productName: "Product One",
  brand: "Brand One",
  category: null,
  subcategory: null,
  shortDescription: null,
  cost: null,
  salePrice: "20.0000",
  currency: "TWD",
  stock: "5",
  productUrl: null,
  lifecycleStatus: "ACTIVE",
  archivedAt: null,
  createdAt: "2026-08-02T00:00:00Z",
  updatedAt: "2026-08-02T00:00:00Z",
  version: 0,
};

describe("ProductDetailView", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it.each([409, 412, 428])("shows a reloadable conflict for HTTP %s", async (status) => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url.includes("/aggregate")) return Promise.resolve(aggregateResponse());
      if (init?.method === "PATCH") return Promise.resolve(new Response(JSON.stringify({ code: "PRECONDITION_FAILED" }), { status }));
      return Promise.resolve(new Response(JSON.stringify(product), { status: 200, headers: { ETag: 'W/"0"' } }));
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<ProductDetailView productUuid={product.productUuid} />);
    await waitFor(() => expect(screen.getByDisplayValue("Brand One")).toBeInTheDocument());
    fireEvent.change(screen.getByLabelText("品牌"), { target: { value: "Brand Two" } });
    fireEvent.click(screen.getByRole("button", { name: "儲存變更" }));

    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("資料已被其他操作更新"));
    const patchCall = fetchMock.mock.calls.find((call) => call[1]?.method === "PATCH");
    expect(patchCall?.[0]).toBe(`/api/products/${product.productUuid}`);
    expect(patchCall?.[1]).toEqual(expect.objectContaining({
      method: "PATCH",
      headers: expect.objectContaining({ "If-Match": 'W/"0"' }),
    }));
  });

  it("reports a missing ETag instead of silently ignoring a save", async () => {
    const fetchMock = vi.fn((url: string) => Promise.resolve(
      url.includes("/aggregate") ? aggregateResponse() : new Response(JSON.stringify(product), { status: 200 }),
    ));
    vi.stubGlobal("fetch", fetchMock);

    render(<ProductDetailView productUuid={product.productUuid} />);
    await waitFor(() => expect(screen.getByDisplayValue("Brand One")).toBeInTheDocument());
    fireEvent.change(screen.getByLabelText("品牌"), { target: { value: "Brand Two" } });
    fireEvent.click(screen.getByRole("button", { name: "儲存變更" }));

    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("缺少版本資訊"));
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("keeps the Aggregate summary readable for an archived Product", async () => {
    const archived = { ...product, lifecycleStatus: "ARCHIVED", archivedAt: "2026-08-07T00:00:00Z" };
    const fetchMock = vi.fn((url: string) => Promise.resolve(
      url.includes("/aggregate")
        ? new Response(JSON.stringify({ product: archived, knowledge: [], creativePlans: [], campaigns: [], assets: [] }))
        : new Response(JSON.stringify(archived), { headers: { ETag: 'W/"1"' } }),
    ));
    vi.stubGlobal("fetch", fetchMock);

    render(<ProductDetailView productUuid={product.productUuid} />);

    expect(await screen.findByText(/Archived Product 不接受一般修改/)).toBeInTheDocument();
    expect(await screen.findByRole("heading", { name: "Product Center 整合摘要" })).toBeInTheDocument();
    expect(screen.getAllByText("尚無資料")).toHaveLength(4);
  });
});

function aggregateResponse() {
  return new Response(JSON.stringify({ product, knowledge: [], creativePlans: [], campaigns: [], assets: [] }), { status: 200 });
}
