import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { forwardCampaignRequest, forwardCreativePlanRequest, forwardKnowledgeRequest, forwardProductRequest } from "./backend-proxy";

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

  it("keeps creative plan paths, queries, headers, and response metadata allowlisted", async () => {
    process.env.BACKEND_INTERNAL_URL = "http://backend:8080";
    const fetchMock = vi.fn().mockResolvedValue(new Response("{}", { status: 201, headers: { ETag: 'W/"0"', Location: "/created", "X-Request-ID": "safe" } }));
    vi.stubGlobal("fetch", fetchMock);
    const product = "d4476a19-30ed-48d9-a518-f9b111bd0911";
    const request = new NextRequest(`http://localhost/api/products/${product}/creative-plans?status=ALL&target=http://evil`, {
      method: "POST", headers: { Cookie: "secret", Authorization: "Bearer secret", "X-Actor-ID": "evil", "X-Request-ID": "caller" }, body: "{}",
    });
    const response = await forwardCreativePlanRequest(request, `/api/products/${product}/creative-plans`, { method: "POST", contentType: "application/json" });
    const [target, init] = fetchMock.mock.calls[0] as [URL, RequestInit];
    expect(target.toString()).toBe(`http://backend:8080/api/products/${product}/creative-plans?status=ALL`);
    const headers = new Headers(init.headers); expect(headers.get("Cookie")).toBeNull(); expect(headers.get("Authorization")).toBeNull(); expect(headers.get("X-Actor-ID")).toBeNull();
    expect(response.status).toBe(201); expect(response.headers.get("ETag")).toBe('W/"0"'); expect(response.headers.get("Location")).toBe("/created");
    expect(response.headers.get("X-Request-ID")).toBe("safe"); expect(await response.json()).toEqual({});
  });

  it("rejects arbitrary creative plan paths and oversized bodies", async () => {
    process.env.BACKEND_INTERNAL_URL = "http://backend:8080";
    const request = new NextRequest("http://localhost/api", { method: "POST", body: "x".repeat(65537) });
    expect((await forwardCreativePlanRequest(request, "http://evil.test", { method: "POST" })).status).toBe(400);
    const product = "d4476a19-30ed-48d9-a518-f9b111bd0911";
    expect((await forwardCreativePlanRequest(request, `/api/products/${product}/creative-plans`, { method: "POST" })).status).toBe(413);
  });

  it("preserves backend error status and body and sanitizes timeout failures", async () => {
    process.env.BACKEND_INTERNAL_URL = "http://backend:8080";
    const product = "d4476a19-30ed-48d9-a518-f9b111bd0911";
    const plan = "79be8758-1f0d-4ca5-bad6-f51aa923cdb9";
    const fetchMock = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ code: "PRECONDITION_FAILED" }), { status: 412, headers: { "Content-Type": "application/json", ETag: 'W/"2"' } })).mockRejectedValueOnce(new DOMException("timed out", "TimeoutError"));
    vi.stubGlobal("fetch", fetchMock);
    const request = new NextRequest(`http://localhost/api/products/${product}/creative-plans/${plan}?status=ALL`, { method: "PATCH", headers: { "If-Match": 'W/"1"' }, body: "{}" });
    const conflict = await forwardCreativePlanRequest(request, `/api/products/${product}/creative-plans/${plan}`, { method: "PATCH", contentType: "application/merge-patch+json" });
    expect((fetchMock.mock.calls[0][0] as URL).search).toBe("");
    expect(conflict.status).toBe(412); expect(await conflict.json()).toEqual({ code: "PRECONDITION_FAILED" }); expect(conflict.headers.get("ETag")).toBe('W/"2"');
    const unavailable = await forwardCreativePlanRequest(new NextRequest(`http://localhost/api/products/${product}/creative-plans`), `/api/products/${product}/creative-plans`, { method: "GET" });
    expect(unavailable.status).toBe(503); expect(await unavailable.json()).toEqual({ code: "BACKEND_UNAVAILABLE", message: "Backend is unavailable" });
  });

  it("allows only structured knowledge paths and never forwards credentials", async () => {
    process.env.BACKEND_INTERNAL_URL = "http://backend:8080";
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ content: [] }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    const productUuid = "d4476a19-30ed-48d9-a518-f9b111bd0911";
    const knowledgeUuid = "5cf53b23-eabe-4b51-b565-62dbe4333721";
    const request = new NextRequest(`http://localhost:3000/api/products/${productUuid}/knowledge?status=ALL&target=http://evil`, {
      headers: { Cookie: "secret=1", Authorization: "Bearer secret", "X-Actor-ID": "evil" },
    });
    await forwardKnowledgeRequest(request, `/api/products/${productUuid}/knowledge/${knowledgeUuid}`, { method: "GET" });
    const [target, init] = fetchMock.mock.calls[0] as [URL, RequestInit];
    expect(target.toString()).toBe(`http://backend:8080/api/products/${productUuid}/knowledge/${knowledgeUuid}`);
    const headers = new Headers(init.headers);
    expect(headers.has("Cookie")).toBe(false);
    expect(headers.has("Authorization")).toBe(false);
    expect(headers.has("X-Actor-ID")).toBe(false);
    const invalid = await forwardKnowledgeRequest(request, `/api/products/${productUuid}/knowledge/${knowledgeUuid}/restore/extra`, { method: "POST" });
    expect(invalid.status).toBe(400);
  });

  it("rejects oversized Knowledge payloads before contacting the Backend", async () => {
    process.env.BACKEND_INTERNAL_URL = "http://backend:8080";
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    const productUuid = "d4476a19-30ed-48d9-a518-f9b111bd0911";
    const request = new NextRequest(`http://localhost:3000/api/products/${productUuid}/knowledge`, {
      method: "POST",
      body: JSON.stringify({ content: "x".repeat(65 * 1024) }),
    });

    const response = await forwardKnowledgeRequest(
      request,
      `/api/products/${productUuid}/knowledge`,
      { method: "POST", contentType: "application/json" },
    );

    expect(response.status).toBe(413);
    expect(await response.json()).toEqual({ code: "PAYLOAD_TOO_LARGE", message: "Request body is too large" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("maps Backend timeout or network failure to a sanitized unavailable error", async () => {
    process.env.BACKEND_INTERNAL_URL = "http://backend:8080";
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new DOMException("timed out", "TimeoutError")));
    const productUuid = "d4476a19-30ed-48d9-a518-f9b111bd0911";

    const response = await forwardKnowledgeRequest(
      new NextRequest(`http://localhost:3000/api/products/${productUuid}/knowledge`),
      `/api/products/${productUuid}/knowledge`,
      { method: "GET" },
    );

    expect(response.status).toBe(503);
    expect(await response.json()).toEqual({ code: "BACKEND_UNAVAILABLE", message: "Backend is unavailable" });
  });

  it("preserves Backend status, body and approved Knowledge response headers", async () => {
    process.env.BACKEND_INTERNAL_URL = "http://backend:8080";
    const productUuid = "d4476a19-30ed-48d9-a518-f9b111bd0911";
    const knowledgeUuid = "5cf53b23-eabe-4b51-b565-62dbe4333721";
    const backendBody = { knowledgeUuid, title: "Created" };
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(backendBody), {
      status: 201,
      headers: {
        "Content-Type": "application/json",
        ETag: 'W/"0"',
        Location: `/api/products/${productUuid}/knowledge/${knowledgeUuid}`,
        "X-Request-ID": "knowledge-create-request",
        "X-Internal-Header": "must-not-leak",
      },
    }));
    vi.stubGlobal("fetch", fetchMock);
    const request = new NextRequest(`http://localhost:3000/api/products/${productUuid}/knowledge`, {
      method: "POST",
      body: JSON.stringify({ knowledgeType: "FEATURE", title: "Created", content: "Content" }),
    });

    const response = await forwardKnowledgeRequest(
      request,
      `/api/products/${productUuid}/knowledge`,
      { method: "POST", contentType: "application/json" },
    );

    expect(response.status).toBe(201);
    expect(await response.json()).toEqual(backendBody);
    expect(response.headers.get("ETag")).toBe('W/"0"');
    expect(response.headers.get("Location")).toBe(`/api/products/${productUuid}/knowledge/${knowledgeUuid}`);
    expect(response.headers.get("X-Request-ID")).toBe("knowledge-create-request");
    expect(response.headers.has("X-Internal-Header")).toBe(false);
  });

  it("keeps Campaign collection queries and credentials isolated", async () => {
    process.env.BACKEND_INTERNAL_URL = "http://backend:8080";
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ content: [] }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    const productUuid = "d4476a19-30ed-48d9-a518-f9b111bd0911";
    const request = new NextRequest(`http://localhost/api/campaigns?productUuid=${productUuid}&associationStatus=ALL&target=http://evil`, {
      headers: { Cookie: "secret", Authorization: "Bearer secret", "X-Actor-ID": "evil" },
    });

    await forwardCampaignRequest(request, "/api/campaigns", { method: "GET" });

    const [target, init] = fetchMock.mock.calls[0] as [URL, RequestInit];
    expect(target.toString()).toBe(`http://backend:8080/api/campaigns?productUuid=${productUuid}&associationStatus=ALL`);
    const headers = new Headers(init.headers);
    expect(headers.has("Cookie")).toBe(false);
    expect(headers.has("Authorization")).toBe(false);
    expect(headers.has("X-Actor-ID")).toBe(false);
  });

  it("rejects arbitrary Campaign paths and strips collection queries from detail paths", async () => {
    process.env.BACKEND_INTERNAL_URL = "http://backend:8080";
    const fetchMock = vi.fn().mockResolvedValue(new Response("{}", { status: 200, headers: { ETag: 'W/"1"' } }));
    vi.stubGlobal("fetch", fetchMock);
    const campaignUuid = "79be8758-1f0d-4ca5-bad6-f51aa923cdb9";
    const productUuid = "d4476a19-30ed-48d9-a518-f9b111bd0911";
    const invalid = await forwardCampaignRequest(new NextRequest("http://localhost/api/campaigns"), "http://evil.test", { method: "GET" });
    expect(invalid.status).toBe(400);
    await forwardCampaignRequest(new NextRequest(`http://localhost/api/campaigns/${campaignUuid}/products/${productUuid}?status=ALL`), `/api/campaigns/${campaignUuid}/products/${productUuid}`, { method: "GET" });
    expect((fetchMock.mock.calls[0][0] as URL).search).toBe("");
  });

  it("enforces the Campaign payload limit and preserves approved response metadata", async () => {
    process.env.BACKEND_INTERNAL_URL = "http://backend:8080";
    const campaignUuid = "79be8758-1f0d-4ca5-bad6-f51aa923cdb9";
    const oversized = new NextRequest("http://localhost/api/campaigns", { method: "POST", body: "x".repeat(65537) });
    expect((await forwardCampaignRequest(oversized, "/api/campaigns", { method: "POST", contentType: "application/json" })).status).toBe(413);
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ campaignUuid }), { status: 201, headers: { "Content-Type": "application/json", ETag: 'W/"0"', Location: `/api/campaigns/${campaignUuid}`, "X-Request-ID": "campaign-request", "X-Internal": "hidden" } }));
    vi.stubGlobal("fetch", fetchMock);
    const response = await forwardCampaignRequest(new NextRequest("http://localhost/api/campaigns", { method: "POST", body: "{}" }), "/api/campaigns", { method: "POST", contentType: "application/json" });
    expect(response.status).toBe(201);
    expect(response.headers.get("ETag")).toBe('W/"0"');
    expect(response.headers.get("Location")).toBe(`/api/campaigns/${campaignUuid}`);
    expect(response.headers.get("X-Request-ID")).toBe("campaign-request");
    expect(response.headers.has("X-Internal")).toBe(false);
  });
});
