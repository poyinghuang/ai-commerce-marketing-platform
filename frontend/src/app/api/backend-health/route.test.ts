import { afterEach, describe, expect, it, vi } from "vitest";
import { GET } from "./route";

describe("GET /api/backend-health", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    delete process.env.BACKEND_INTERNAL_URL;
  });

  it("calls the internal Actuator endpoint and returns only status", async () => {
    process.env.BACKEND_INTERNAL_URL = "http://backend:8080";
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ status: "UP", components: { db: {} } }), {
        status: 200,
        headers: { "X-Request-ID": "backend-request" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const response = await GET();

    expect(fetchMock).toHaveBeenCalledWith(
      "http://backend:8080/actuator/health",
      expect.objectContaining({ cache: "no-store" }),
    );
    expect(await response.json()).toEqual({ status: "UP" });
    expect(response.headers.get("X-Request-ID")).toBe("backend-request");
  });

  it("returns a sanitized unavailable response when configuration is missing", async () => {
    const response = await GET();
    expect(response.status).toBe(503);
    expect(await response.json()).toEqual({ status: "DOWN" });
  });
});
