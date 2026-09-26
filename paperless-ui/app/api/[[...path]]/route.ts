import { NextRequest, NextResponse } from "next/server";

export const dynamic = "force-dynamic";

function getBackendBaseUrl(): string {
  const url =
    process.env.BACKEND_URL ||
    process.env.NEXT_PUBLIC_API_URL ||
    "http://127.0.0.1:8080";

  // If running on host with localhost, map to 127.0.0.1 for IPv4 reliability
  if (url.startsWith("http://localhost:") || url === "http://localhost") {
    return url.replace("http://localhost", "http://127.0.0.1");
  }

  return url;
}

async function proxyRequest(
  request: NextRequest,
  context: { params: Promise<{ path?: string[] }> }
) {
  const { path } = await context.params;
  const pathSegment = path && path.length > 0 ? path.join("/") : "";
  const backendBase = getBackendBaseUrl().replace(/\/+$/, "");

  const urlObj = new URL(request.url);
  const tokenParam = urlObj.searchParams.get("token");
  const isPreview = urlObj.searchParams.get("preview") === "true";

  // Strip token and preview query params before forwarding to the backend
  urlObj.searchParams.delete("token");
  urlObj.searchParams.delete("preview");
  const cleanSearch = urlObj.search || "";
  const targetUrl = `${backendBase}/${pathSegment}${cleanSearch}`;

  const forwardHeaders = new Headers();

  // Forward all request headers except host/connection
  request.headers.forEach((value, key) => {
    const lowerKey = key.toLowerCase();
    if (
      lowerKey !== "host" &&
      lowerKey !== "connection" &&
      lowerKey !== "content-length"
    ) {
      forwardHeaders.set(key, value);
    }
  });

  // Promote ?token= to Authorization header if not already present
  if (!forwardHeaders.has("authorization") && tokenParam) {
    forwardHeaders.set("authorization", `Bearer ${tokenParam}`);
  }

  const method = request.method;
  let body: BodyInit | undefined;

  if (method !== "GET" && method !== "HEAD") {
    try {
      const buffer = await request.arrayBuffer();
      if (buffer.byteLength > 0) {
        body = buffer;
        forwardHeaders.set("content-length", buffer.byteLength.toString());
      }
    } catch (e) {
      console.warn("Could not read request body:", e);
    }
  }

  try {
    const response = await fetch(targetUrl, {
      method,
      headers: forwardHeaders,
      body,
      redirect: "manual",
    });

    const responseHeaders = new Headers();
    response.headers.forEach((value, key) => {
      const lower = key.toLowerCase();
      // Drop transport headers that Next.js or fetch manages
      if (
        lower !== "content-encoding" &&
        lower !== "content-length" &&
        lower !== "transfer-encoding"
      ) {
        responseHeaders.set(key, value);
      }
    });

    // If preview was requested, ensure content-disposition is inline so iframe displays it
    if (isPreview) {
      const cd = responseHeaders.get("content-disposition");
      if (cd && cd.toLowerCase().startsWith("attachment")) {
        responseHeaders.set(
          "content-disposition",
          cd.replace(/^attachment/i, "inline")
        );
      } else if (!cd) {
        responseHeaders.set("content-disposition", "inline");
      }
    }

    if (response.status === 204) {
      return new NextResponse(null, {
        status: 204,
        headers: responseHeaders,
      });
    }

    return new NextResponse(response.body, {
      status: response.status,
      statusText: response.statusText,
      headers: responseHeaders,
    });
  } catch (err: unknown) {
    console.error(`[API Proxy] Failed to proxy ${method} ${targetUrl}:`, err);
    return NextResponse.json(
      {
        type: "urn:problem-type:gateway-error",
        title: "Bad Gateway",
        status: 502,
        detail: `Failed to connect to backend at ${backendBase}. Please check that the backend service is running.`,
      },
      { status: 502 }
    );
  }
}

export const GET = proxyRequest;
export const POST = proxyRequest;
export const PUT = proxyRequest;
export const DELETE = proxyRequest;
export const PATCH = proxyRequest;
export const HEAD = proxyRequest;
