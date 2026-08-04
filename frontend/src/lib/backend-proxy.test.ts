import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { forwardProductRequest } from "./backend-proxy";

describe("product backend proxy", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    delete process.env.BACKEND_INTERNAL_URL;
  });

  it("uses only the server configured origin and allowlisted product query", async () => {
    process.env.BACKEND_INTERNAL_URL = "http://backend:8080";
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ content: [] }), {
        status: 200,
        headers: { "Content-Type": "application/json", "X-Request-ID": "backend-request" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);
    const request = new NextRequest(
      "http://localhost:3000/api/products?keyword=serum&target=http://attacker.example",
      { headers: { "X-Request-ID": "caller-request", "X-Actor-ID": "attacker" } },
    );

    const response = await forwardProductRequest(request, "/api/products", { method: "GET" });

    const [target, init] = fetchMock.mock.calls[0] as [URL, RequestInit];
    expect(target.toString()).toBe("http://backend:8080/api/products?keyword=serum");
    expect(new Headers(init.headers).get("X-Request-ID")).toBe("caller-request");
    expect(new Headers(init.headers).has("X-Actor-ID")).toBe(false);
    expect(response.headers.get("X-Request-ID")).toBe("backend-request");
  });

  it("forwards concurrency headers but rejects paths outside the product allowlist", async () => {
    process.env.BACKEND_INTERNAL_URL = "http://backend:8080";
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204, headers: { ETag: 'W/"1"' } }));
    vi.stubGlobal("fetch", fetchMock);
    const request = new NextRequest("http://localhost:3000/api/products/id", {
      method: "DELETE",
      headers: { "If-Match": 'W/"0"' },
    });

    const invalid = await forwardProductRequest(request, "http://attacker.example", { method: "DELETE" });
    expect(invalid.status).toBe(400);
    const missingUuid = await forwardProductRequest(request, "/api/products/restore", { method: "POST" });
    expect(missingUuid.status).toBe(400);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("returns a sanitized unavailable response for invalid server configuration", async () => {
    process.env.BACKEND_INTERNAL_URL = "file:///etc/passwd";
    const response = await forwardProductRequest(
      new NextRequest("http://localhost:3000/api/products"),
      "/api/products",
      { method: "GET" },
    );
    expect(response.status).toBe(503);
    expect(await response.json()).toEqual({ code: "BACKEND_UNAVAILABLE", message: "Backend is unavailable" });
  });
});
