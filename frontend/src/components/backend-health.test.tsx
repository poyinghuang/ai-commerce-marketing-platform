import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { BackendHealth } from "./backend-health";

describe("BackendHealth", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("uses the same-origin health route", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ status: "UP" }), { status: 200 }),
    );
    vi.stubGlobal("fetch", fetchMock);

    render(<BackendHealth />);

    await waitFor(
      () => expect(screen.getByRole("status")).toHaveTextContent("Backend 正常"),
      { timeout: 5_000 },
    );
    expect(fetchMock).toHaveBeenCalledWith("/api/backend-health", expect.any(Object));
  });
});
