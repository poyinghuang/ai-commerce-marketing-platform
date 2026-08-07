import { NextRequest, NextResponse } from "next/server";

const TIMEOUT_MS = 10_000;
const MAX_BODY_BYTES = 64 * 1024;
const SAFE_REQUEST_ID = /^[A-Za-z0-9._:-]{1,128}$/;
const PRODUCT_UUID_PATH = "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";
const SAFE_PRODUCT_PATH = new RegExp(`^/api/products(?:/${PRODUCT_UUID_PATH}(?:/restore)?)?$`, "i");
const SAFE_KNOWLEDGE_PATH = new RegExp(
  `^/api/products/${PRODUCT_UUID_PATH}/knowledge(?:/${PRODUCT_UUID_PATH}(?:/restore)?)?$`, "i",
);
const SAFE_CREATIVE_PLAN_PATH = new RegExp(
  `^/api/products/${PRODUCT_UUID_PATH}/creative-plans(?:/${PRODUCT_UUID_PATH}(?:/restore)?)?$`, "i",
);
const SAFE_ASSET_PATH = new RegExp(
  `^/api/products/${PRODUCT_UUID_PATH}/assets(?:/${PRODUCT_UUID_PATH}(?:/restore)?)?$`, "i",
);
const SAFE_AGGREGATE_PATH = new RegExp(`^/api/products/${PRODUCT_UUID_PATH}/aggregate$`, "i");
const SAFE_CAMPAIGN_PATH = new RegExp(
  `^/api/campaigns(?:/${PRODUCT_UUID_PATH}(?:/restore|/products(?:/${PRODUCT_UUID_PATH}(?:/restore)?)?)?)?$`, "i",
);

type ProxyOptions = {
  method: "GET" | "POST" | "PATCH" | "DELETE";
  contentType?: string;
  responseHeaders?: readonly string[];
};

export async function forwardProductRequest(
  request: NextRequest,
  backendPath: string,
  options: ProxyOptions,
) {
  if (!SAFE_PRODUCT_PATH.test(backendPath)) {
    return proxyError("INVALID_PRODUCT_PATH", "Product path is invalid", 400);
  }
  const allowedQuery = backendPath === "/api/products"
    ? new Set(["page", "size", "status", "category", "keyword", "sku", "productId", "sort"])
    : new Set<string>();
  return forwardAllowlistedRequest(request, backendPath, options, allowedQuery);
}

export async function forwardKnowledgeRequest(request: NextRequest, backendPath: string, options: ProxyOptions) {
  if (!SAFE_KNOWLEDGE_PATH.test(backendPath)) {
    return proxyError("INVALID_KNOWLEDGE_PATH", "Knowledge path is invalid", 400);
  }
  const allowedQuery = backendPath.endsWith("/knowledge")
    ? new Set(["page", "size", "status", "sort"])
    : new Set<string>();
  return forwardAllowlistedRequest(request, backendPath, options, allowedQuery);
}

export async function forwardCreativePlanRequest(request: NextRequest, backendPath: string, options: ProxyOptions) {
  if (!SAFE_CREATIVE_PLAN_PATH.test(backendPath)) {
    return proxyError("INVALID_CREATIVE_PLAN_PATH", "Creative plan path is invalid", 400);
  }
  const allowedQuery = backendPath.endsWith("/creative-plans")
    ? new Set(["page", "size", "status", "sort"])
    : new Set<string>();
  return forwardAllowlistedRequest(request, backendPath, options, allowedQuery);
}

export async function forwardAssetRequest(request: NextRequest, backendPath: string, options: ProxyOptions) {
  if (!SAFE_ASSET_PATH.test(backendPath)) {
    return proxyError("INVALID_ASSET_PATH", "Asset path is invalid", 400);
  }
  const allowedQuery = backendPath.endsWith("/assets")
    ? new Set(["page", "size", "status", "assetType", "creativePlanUuid", "campaignUuid", "storageProvider", "sort"])
    : new Set<string>();
  return forwardAllowlistedRequest(request, backendPath, options, allowedQuery);
}

export async function forwardProductAggregateRequest(request: NextRequest, backendPath: string) {
  if (!SAFE_AGGREGATE_PATH.test(backendPath)) {
    return proxyError("INVALID_AGGREGATE_PATH", "Aggregate path is invalid", 400);
  }
  const keys = [...request.nextUrl.searchParams.keys()];
  if (keys.some((key) => key !== "includeArchived")) {
    return proxyError("INVALID_AGGREGATE_QUERY", "Only includeArchived is allowed", 400);
  }
  const values = request.nextUrl.searchParams.getAll("includeArchived");
  if (values.length > 1 || (values.length === 1 && !["true", "false"].includes(values[0]))) {
    return proxyError("INVALID_AGGREGATE_QUERY", "includeArchived must be exactly true or false", 400);
  }
  return forwardAllowlistedRequest(
    request,
    backendPath,
    { method: "GET", responseHeaders: ["Content-Type", "X-Request-ID", "Cache-Control"] },
    new Set(["includeArchived"]),
  );
}

export async function forwardCampaignRequest(request: NextRequest, backendPath: string, options: ProxyOptions) {
  if (!SAFE_CAMPAIGN_PATH.test(backendPath)) {
    return proxyError("INVALID_CAMPAIGN_PATH", "Campaign path is invalid", 400);
  }
  const isCampaignCollection = backendPath === "/api/campaigns";
  const isAssociationCollection = /\/products$/i.test(backendPath);
  const allowedQuery = isCampaignCollection
    ? new Set(["page", "size", "status", "keyword", "productUuid", "associationStatus", "sort"])
    : isAssociationCollection
      ? new Set(["page", "size", "status", "sort"])
      : new Set<string>();
  return forwardAllowlistedRequest(request, backendPath, options, allowedQuery);
}

async function forwardAllowlistedRequest(
  request: NextRequest,
  backendPath: string,
  options: ProxyOptions,
  allowedQuery: Set<string>,
) {
  const backendOrigin = resolveBackendOrigin();
  if (!backendOrigin) return proxyError("BACKEND_UNAVAILABLE", "Backend is unavailable", 503);
  const headers = new Headers();
  if (options.contentType) headers.set("Content-Type", options.contentType);
  for (const header of ["If-Match", "X-Request-ID"]) {
    const value = request.headers.get(header);
    if (value && (header !== "X-Request-ID" || SAFE_REQUEST_ID.test(value))) headers.set(header, value);
  }
  let body: string | undefined;
  if (options.method === "POST" || options.method === "PATCH") {
    body = await request.text();
    if (new TextEncoder().encode(body).byteLength > MAX_BODY_BYTES) return proxyError("PAYLOAD_TOO_LARGE", "Request body is too large", 413);
  }
  try {
    const backendUrl = new URL(backendPath, backendOrigin);
    for (const [key, value] of request.nextUrl.searchParams.entries()) if (allowedQuery.has(key)) backendUrl.searchParams.append(key, value);
    const response = await fetch(backendUrl, { method: options.method, headers, body, cache: "no-store", signal: AbortSignal.timeout(TIMEOUT_MS) });
    const responseHeaders = new Headers();
    for (const header of options.responseHeaders ?? ["Content-Type", "ETag", "Location", "X-Request-ID"]) {
      const value = response.headers.get(header); if (value) responseHeaders.set(header, value);
    }
    return new NextResponse(response.status === 204 ? null : await response.text(), { status: response.status, headers: responseHeaders });
  } catch { return proxyError("BACKEND_UNAVAILABLE", "Backend is unavailable", 503); }
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

function proxyError(code: string, message: string, status: number) {
  return NextResponse.json({ code, message }, { status });
}
