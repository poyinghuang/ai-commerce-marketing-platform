import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ProductListView } from "./product-list-view";

const push = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push }),
  useSearchParams: () => new URLSearchParams(),
}));

describe("ProductListView", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    push.mockReset();
  });

  it("renders the empty state through the same-origin products route", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      content: [], page: 0, size: 20, totalElements: 0, totalPages: 0,
      sort: { field: "updatedAt", direction: "desc" },
    }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    render(<ProductListView />);

    expect(screen.getByRole("status")).toHaveTextContent("正在載入商品");
    await waitFor(() => expect(screen.getByText("尚無符合條件的商品")).toBeInTheDocument());
    expect(fetchMock).toHaveBeenCalledWith("/api/products", expect.objectContaining({ cache: "no-store" }));
  });

  it("renders a sanitized error state", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ message: "Backend is unavailable" }), { status: 503 }),
    ));

    render(<ProductListView />);

    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("Backend is unavailable"));
  });
});
