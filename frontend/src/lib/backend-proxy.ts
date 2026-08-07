import { NextRequest, NextResponse } from "next/server";

const TIMEOUT_MS = 10_000;
const MAX_BODY_BYTES = 64 * 1024;
const SAFE_REQUEST_ID = /^[A-Za-z0-9._:-]{1,128}$/;
const PRODUCT_UUID_PATH = "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";
const SAFE_BACKEND_PATH = new RegExp(
  `^/api/products(?:/${PRODUCT_UUID_PATH}(?:/restore|/knowledge(?:/${PRODUCT_UUID_PATH}(?:/restore)?)?)?)?$`,
  "i",
);

type ProxyOptions = {
  method: "GET" | "POST" | "PATCH" | "DELETE";
  contentType?: string;
};

export async function forwardProductRequest(
  request: NextRequest,
  backendPath: string,
  options: ProxyOptions,
) {
  if (!SAFE_BACKEND_PATH.test(backendPath)) {
    return proxyError("INVALID_PRODUCT_PATH", "Product path is invalid", 400);
  }

  const backendOrigin = resolveBackendOrigin();
  if (!backendOrigin) {
    return proxyError("BACKEND_UNAVAILABLE", "Backend is unavailable", 503);
  }

  const headers = new Headers();
  if (options.contentType) headers.set("Content-Type", options.contentType);
  const ifMatch = request.headers.get("If-Match");
  if (ifMatch) headers.set("If-Match", ifMatch);
  const requestId = request.headers.get("X-Request-ID");
  if (requestId && SAFE_REQUEST_ID.test(requestId)) headers.set("X-Request-ID", requestId);

  let body: string | undefined;
  if (options.method === "POST" || options.method === "PATCH") {
    body = await request.text();
    if (new TextEncoder().encode(body).byteLength > MAX_BODY_BYTES) {
      return proxyError("PAYLOAD_TOO_LARGE", "Request body is too large", 413);
    }
  }

  try {
    const backendUrl = new URL(backendPath, backendOrigin);
    for (const [key, value] of allowlistedProductQuery(request.nextUrl.searchParams, backendPath)) {
      backendUrl.searchParams.append(key, value);
    }
    const response = await fetch(backendUrl, {
      method: options.method,
      headers,
      body,
      cache: "no-store",
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
    const responseHeaders = new Headers();
    for (const header of ["Content-Type", "ETag", "Location", "X-Request-ID"]) {
      const value = response.headers.get(header);
      if (value) responseHeaders.set(header, value);
    }
    const responseBody = response.status === 204 ? null : await response.text();
    return new NextResponse(responseBody, { status: response.status, headers: responseHeaders });
  } catch {
    return proxyError("BACKEND_UNAVAILABLE", "Backend is unavailable", 503);
  }
}

function resolveBackendOrigin() {
  const configured = process.env.BACKEND_INTERNAL_URL;
  if (!configured) return null;
  try {
    const url = new URL(configured);
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash) {
      return null;
    }
    url.pathname = url.pathname.replace(/\/*$/, "/");
    return url;
  } catch {
    return null;
  }
}

function allowlistedProductQuery(searchParams: URLSearchParams, backendPath: string) {
  const allowed = backendPath.endsWith("/knowledge")
    ? new Set(["page", "size", "status", "sort"])
    : backendPath === "/api/products"
      ? new Set(["page", "size", "status", "category", "keyword", "sku", "productId", "sort"])
      : new Set<string>();
  return Array.from(searchParams.entries()).filter(([key]) => allowed.has(key));
}

function proxyError(code: string, message: string, status: number) {
  return NextResponse.json({ code, message }, { status });
}
