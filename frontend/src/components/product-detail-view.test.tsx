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
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(product), { status: 200, headers: { ETag: 'W/"0"' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: "PRECONDITION_FAILED" }), { status }));
    vi.stubGlobal("fetch", fetchMock);

    render(<ProductDetailView productUuid={product.productUuid} />);
    await waitFor(() => expect(screen.getByDisplayValue("Brand One")).toBeInTheDocument());
    fireEvent.change(screen.getByLabelText("品牌"), { target: { value: "Brand Two" } });
    fireEvent.click(screen.getByRole("button", { name: "儲存變更" }));

    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("資料已被其他操作更新"));
    expect(fetchMock.mock.calls[2][0]).toBe(`/api/products/${product.productUuid}`);
    expect(fetchMock.mock.calls[2][1]).toEqual(expect.objectContaining({
      method: "PATCH",
      headers: expect.objectContaining({ "If-Match": 'W/"0"' }),
    }));
  });

  it("reports a missing ETag instead of silently ignoring a save", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(product), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    render(<ProductDetailView productUuid={product.productUuid} />);
    await waitFor(() => expect(screen.getByDisplayValue("Brand One")).toBeInTheDocument());
    fireEvent.change(screen.getByLabelText("品牌"), { target: { value: "Brand Two" } });
    fireEvent.click(screen.getByRole("button", { name: "儲存變更" }));

    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("缺少版本資訊"));
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});
