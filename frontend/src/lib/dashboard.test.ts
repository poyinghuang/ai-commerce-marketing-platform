import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { forwardDashboard } from "./dashboard";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  delete process.env.PLATFORM_STAGE5_ENABLED;
  delete process.env.BACKEND_INTERNAL_URL;
});

describe("Dashboard fail-closed proxy", () => {
  it("returns a local 404 while disabled without contacting Backend", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    const result = await forwardDashboard(new NextRequest("http://local/api/dashboard"), "/api/dashboard");
    expect(result.status).toBe(404);
    expect(await result.json()).toEqual({ code: "DASHBOARD_DISABLED", message: "Dashboard is unavailable" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("forwards summary GET and rejects query strings and extra segments", async () => {
    process.env.PLATFORM_STAGE5_ENABLED = "true";
    process.env.BACKEND_INTERNAL_URL = "http://backend:8080";
    const payload = '{"generatedAt":"2026-08-24T00:00:00Z","todos":{"available":true,"items":[],"truncated":false,"totalElements":0},"products":{"available":true,"items":[],"truncated":false,"totalElements":0},"reviews":{"available":true,"items":[],"truncated":false,"totalElements":0},"campaigns":{"available":true,"items":[],"truncated":false,"totalElements":0},"platformCampaigns":{"available":true,"items":[],"truncated":false,"totalElements":0},"anomalies":{"available":true,"items":[],"truncated":false,"totalElements":0},"kpis":{"available":false}}';
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(jsonResponse(payload)));
    vi.stubGlobal("fetch", fetchMock);
    const ok = await forwardDashboard(new NextRequest("http://local/api/dashboard"), "/api/dashboard");
    expect(ok.status).toBe(200);
    expect(fetchMock.mock.calls[0][0].toString()).toBe("http://backend:8080/api/dashboard");
    expect(fetchMock.mock.calls[0][1].method).toBe("GET");
    expect(fetchMock.mock.calls[0][1].headers.get("Authorization")).toBeNull();
    const query = await forwardDashboard(new NextRequest("http://local/api/dashboard?asOf=forbidden"), "/api/dashboard");
    expect(query.status).toBe(400);
    const extra = await forwardDashboard(new NextRequest("http://local/api/dashboard/todos/extra"), "/api/dashboard/todos/extra");
    expect(extra.status).toBe(400);
    const section = await forwardDashboard(new NextRequest("http://local/api/dashboard/todos?page=0&size=20"), "/api/dashboard/todos");
    expect(section.status).toBe(200);
    expect(fetchMock.mock.calls.at(-1)?.[0].toString()).toBe("http://backend:8080/api/dashboard/todos?page=0&size=20");
    const duplicate = await forwardDashboard(new NextRequest("http://local/api/dashboard/todos?page=0&page=1"), "/api/dashboard/todos");
    expect(duplicate.status).toBe(400);
    const posted = await forwardDashboard(new NextRequest("http://local/api/dashboard", { method: "POST" }), "/api/dashboard");
    expect(posted.status).toBe(400);
  });

  it("closes forbidden DTO fields and oversize responses", async () => {
    process.env.PLATFORM_STAGE5_ENABLED = "true";
    process.env.BACKEND_INTERNAL_URL = "http://backend:8080";
    const request = new NextRequest("http://local/api/dashboard");
    for (const field of ["platformAccountUuid", "accountReference", "externalId", "safeProviderTraceId", "sourceFingerprint", "secret"]) {
      vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(JSON.stringify({ [field]: "sentinel" }))));
      const result = await forwardDashboard(request, "/api/dashboard");
      expect(result.status, field).toBe(502);
      expect(await result.text()).not.toContain("sentinel");
    }
    let cancelled = false;
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(new Uint8Array(1024 * 1024));
        controller.enqueue(new Uint8Array(1));
      },
      cancel() {
        cancelled = true;
      },
    });
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(stream, { headers: { "content-type": "application/json" } })));
    const large = await forwardDashboard(request, "/api/dashboard");
    expect(large.status).toBe(502);
    expect(cancelled).toBe(true);
  });
});

function jsonResponse(body: string, status = 200) {
  return new Response(body, { status, headers: { "content-type": "application/json" } });
}
