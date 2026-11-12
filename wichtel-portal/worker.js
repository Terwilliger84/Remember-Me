// ==========================================================================
// Cloudflare Worker für Remember-Me Online-Wichtelportal
// Stellt das mobile Familien-Wichtelportal (ohne App-Zwang) bereit und leitet
// /v1/* CORS-frei an das bestehende Appwrite-Projekt weiter (0 Zusatzkosten).
// ==========================================================================

const APPWRITE_ORIGIN = "https://fra.cloud.appwrite.io";
const APPWRITE_PROJECT_ID = "6981eb5a00304f3ed7a3";

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, PUT, PATCH, DELETE, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization, x-appwrite-project, x-appwrite-response-format, x-sdk-name, x-sdk-platform, x-sdk-language, x-sdk-version"
};

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: CORS_HEADERS });
    }

    // Appwrite REST Proxy unter /v1/* (verhindert jegliche Browser-/iOS-/WhatsApp-CORS-Blockaden)
    if (url.pathname.startsWith("/v1/")) {
      const targetUrl = APPWRITE_ORIGIN + url.pathname + url.search;
      const proxyHeaders = new Headers();
      proxyHeaders.set("x-appwrite-project", APPWRITE_PROJECT_ID);
      const ct = request.headers.get("content-type");
      if (ct) proxyHeaders.set("content-type", ct);
      const respFormat = request.headers.get("x-appwrite-response-format");
      if (respFormat) proxyHeaders.set("x-appwrite-response-format", respFormat);

      const init = {
        method: request.method,
        headers: proxyHeaders
      };
      if (request.method !== "GET" && request.method !== "HEAD") {
        init.body = await request.arrayBuffer();
      }

      const upstream = await fetch(targetUrl, init);
      const outHeaders = new Headers(upstream.headers);
      for (const [k, v] of Object.entries(CORS_HEADERS)) {
        outHeaders.set(k, v);
      }
      outHeaders.delete("set-cookie");
      return new Response(upstream.body, {
        status: upstream.status,
        headers: outHeaders
      });
    }

    // Statische Auslieferung des Wichtelportals
    if (url.pathname === "/wichteln.html") {
      const rootUrl = new URL("/" + url.search, url.origin);
      return env.ASSETS.fetch(new Request(rootUrl.toString(), request));
    }

    return env.ASSETS.fetch(request);
  }
};
