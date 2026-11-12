// ==========================================================================
// Cloudflare Worker für Remember-Me Online-Wichtelportal
// 1. CORS-freier Appwrite-Proxy unter /v1/*
// 2. Echter RFC 8291 / RFC 8292 VAPID Web-Push-Server unter /api/push-*
//    -> Sendet automatische Handy-Meldungen bei neuen Nachrichten,
//       aktualisierten Wunschzetteln & Auflösung (100 % anonym & Admin-blind!)
// ==========================================================================

const APPWRITE_ORIGIN = "https://fra.cloud.appwrite.io";
const APPWRITE_PROJECT_ID = "6981eb5a00304f3ed7a3";
const DB_ID = "remember_me_db";
const COL_ID = "wichteln";

const VAPID_PUBLIC_KEY = "BPSNSFGH96cugWU6gCB6-I9XR255jVK_Of4sjgOzFB6BltxySWnic1wvU6xQmhHwPZjZ8_EdILafRwnIs0bhjFE";
const VAPID_SUBJECT = "mailto:wichteln@kirchnerapp.de";

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, PUT, PATCH, DELETE, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization, x-appwrite-project, x-appwrite-response-format, x-sdk-name, x-sdk-platform, x-sdk-language, x-sdk-version"
};

function jsonResponse(obj, status = 200) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      ...CORS_HEADERS
    }
  });
}

function bytesToB64Url(bytes) {
  let bin = "";
  const u8 = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  for (let i = 0; i < u8.length; i++) bin += String.fromCharCode(u8[i]);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function b64UrlToBytes(b64u) {
  const norm = String(b64u || "").replace(/-/g, "+").replace(/_/g, "/");
  const pad = norm.length % 4 === 0 ? "" : "=".repeat(4 - (norm.length % 4));
  const bin = atob(norm + pad);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

function concatBytes(...arrays) {
  const total = arrays.reduce((acc, a) => acc + a.length, 0);
  const out = new Uint8Array(total);
  let offset = 0;
  for (const a of arrays) {
    out.set(a, offset);
    offset += a.length;
  }
  return out;
}

async function sha256BytesAsync(dataBytes) {
  const buf = await crypto.subtle.digest("SHA-256", dataBytes);
  return new Uint8Array(buf);
}

async function sha256HexAsync(str) {
  const bytes = await sha256BytesAsync(new TextEncoder().encode(String(str || "")));
  return Array.from(bytes).map((b) => b.toString(16).padStart(2, "0")).join("");
}

async function unsealWichtelBoxAsync(cipherB64, roundId, pid) {
  const binStr = atob(String(cipherB64 || "").trim());
  const cipher = new Uint8Array(binStr.length);
  for (let i = 0; i < binStr.length; i++) cipher[i] = binStr.charCodeAt(i);
  const plain = new Uint8Array(cipher.length);
  let blockIdx = 0;
  const enc = new TextEncoder();
  for (let offset = 0; offset < cipher.length; offset += 32) {
    const keyBlock = await sha256BytesAsync(enc.encode(`RM_WICHTEL_V1:${roundId}:${pid}:${blockIdx++}`));
    const chunk = Math.min(32, cipher.length - offset);
    for (let j = 0; j < chunk; j++) plain[offset + j] = cipher[offset + j] ^ keyBlock[j];
  }
  return JSON.parse(new TextDecoder().decode(plain));
}

async function hkdfDerive(salt, ikm, info, byteLen) {
  const baseKey = await crypto.subtle.importKey("raw", ikm, "HKDF", false, ["deriveBits"]);
  const bits = await crypto.subtle.deriveBits(
    { name: "HKDF", hash: "SHA-256", salt, info },
    baseKey,
    byteLen * 8
  );
  return new Uint8Array(bits);
}

async function createVapidAuthHeader(endpointUrl, vapidPrivateJwkJson) {
  const aud = new URL(endpointUrl).origin;
  const enc = new TextEncoder();
  const headerB64 = bytesToB64Url(enc.encode(JSON.stringify({ typ: "JWT", alg: "ES256" })));
  const payloadB64 = bytesToB64Url(
    enc.encode(
      JSON.stringify({
        aud,
        exp: Math.floor(Date.now() / 1000) + 12 * 3600,
        sub: VAPID_SUBJECT
      })
    )
  );
  const unsignedToken = `${headerB64}.${payloadB64}`;
  const jwk = typeof vapidPrivateJwkJson === "string" ? JSON.parse(vapidPrivateJwkJson) : vapidPrivateJwkJson;
  const privKey = await crypto.subtle.importKey(
    "jwk",
    jwk,
    { name: "ECDSA", namedCurve: "P-256" },
    false,
    ["sign"]
  );
  const sigBuf = await crypto.subtle.sign(
    { name: "ECDSA", hash: "SHA-256" },
    privKey,
    enc.encode(unsignedToken)
  );
  const jwt = `${unsignedToken}.${bytesToB64Url(new Uint8Array(sigBuf))}`;
  return `vapid t=${jwt}, k=${VAPID_PUBLIC_KEY}`;
}

async function encryptWebPushAes128Gcm(subscription, payloadObj) {
  const uaPubBytes = b64UrlToBytes(subscription.keys.p256dh);
  const authSecret = b64UrlToBytes(subscription.keys.auth);
  const uaPubKey = await crypto.subtle.importKey(
    "raw",
    uaPubBytes,
    { name: "ECDH", namedCurve: "P-256" },
    false,
    []
  );

  const asKeyPair = await crypto.subtle.generateKey({ name: "ECDH", namedCurve: "P-256" }, true, ["deriveBits"]);
  const asPubBytes = new Uint8Array(await crypto.subtle.exportKey("raw", asKeyPair.publicKey));
  const sharedSecret = new Uint8Array(
    await crypto.subtle.deriveBits({ name: "ECDH", public: uaPubKey }, asKeyPair.privateKey, 256)
  );

  const enc = new TextEncoder();
  const keyInfo = concatBytes(enc.encode("WebPush: info\0"), uaPubBytes, asPubBytes);
  const ikm = await hkdfDerive(authSecret, sharedSecret, keyInfo, 32);

  const salt = crypto.getRandomValues(new Uint8Array(16));
  const cekBytes = await hkdfDerive(salt, ikm, enc.encode("Content-Encoding: aes128gcm\0"), 16);
  const nonce = await hkdfDerive(salt, ikm, enc.encode("Content-Encoding: nonce\0"), 12);

  const cek = await crypto.subtle.importKey("raw", cekBytes, "AES-GCM", false, ["encrypt"]);
  const plaintext = enc.encode(JSON.stringify(payloadObj));
  const recordPlain = concatBytes(plaintext, new Uint8Array([0x02]));
  const ciphertext = new Uint8Array(
    await crypto.subtle.encrypt({ name: "AES-GCM", iv: nonce }, cek, recordPlain)
  );

  // RFC 8188 Header: salt(16) || rs(4, 4096) || idlen(1, 65) || keyid(65)
  const rsHeader = new Uint8Array([0x00, 0x00, 0x10, 0x00, asPubBytes.length]);
  return concatBytes(salt, rsHeader, asPubBytes, ciphertext);
}

async function sendWebPushNotification(subscription, payloadObj, vapidPrivateJwk) {
  const bodyBytes = await encryptWebPushAes128Gcm(subscription, payloadObj);
  const authHeader = await createVapidAuthHeader(subscription.endpoint, vapidPrivateJwk);
  const res = await fetch(subscription.endpoint, {
    method: "POST",
    headers: {
      Authorization: authHeader,
      "Content-Type": "application/octet-stream",
      "Content-Encoding": "aes128gcm",
      TTL: "604800",
      Urgency: "high"
    },
    body: bodyBytes
  });
  return { status: res.status, ok: res.status >= 200 && res.status < 300 };
}

function qEqual(attr, val) {
  return encodeURIComponent(JSON.stringify({ method: "equal", attribute: attr, values: [val] }));
}
function qLimit(n) {
  return encodeURIComponent(JSON.stringify({ method: "limit", values: [n] }));
}

async function appwriteFetch(method, path, bodyObj) {
  const res = await fetch(APPWRITE_ORIGIN + "/v1" + path, {
    method,
    headers: {
      "Content-Type": "application/json",
      "x-appwrite-project": APPWRITE_PROJECT_ID
    },
    body: bodyObj ? JSON.stringify(bodyObj) : undefined
  });
  if (res.status === 204) return { status: 204, json: null };
  const json = await res.json().catch(() => null);
  return { status: res.status, json };
}

async function upsertPushDoc(roundId, pid, payloadObj) {
  const docId = `ps_${roundId}_${pid}`;
  const data = {
    roundId: `push_${roundId}`,
    docType: "PUSH",
    slotKey: `P_${pid}`,
    payload: JSON.stringify(payloadObj)
  };
  const postRes = await appwriteFetch("POST", `/databases/${DB_ID}/collections/${COL_ID}/documents`, {
    documentId: docId,
    data
  });
  if (postRes.status === 409) {
    await appwriteFetch("PATCH", `/databases/${DB_ID}/collections/${COL_ID}/documents/${docId}`, { data });
  }
}

async function handlePushSubscribe(request, env) {
  if (!env.VAPID_PRIVATE_JWK) {
    return jsonResponse({ ok: false, error: "VAPID key not configured" }, 500);
  }
  const body = await request.json().catch(() => null);
  if (!body || !body.roundId || !body.pid || !body.subscription || !body.subscription.endpoint || !body.subscription.keys) {
    return jsonResponse({ ok: false, error: "Ungültige Push-Daten" }, 400);
  }
  const roundId = String(body.roundId).trim();
  const pid = Number(body.pid);
  const partDocRes = await appwriteFetch("GET", `/databases/${DB_ID}/collections/${COL_ID}/documents/p_${roundId}_${pid}`);
  if (partDocRes.status < 200 || partDocRes.status >= 300 || !partDocRes.json) {
    return jsonResponse({ ok: false, error: "Teilnehmer nicht gefunden" }, 404);
  }
  const partPayload = JSON.parse(partDocRes.json.payload || "{}");
  const okToken = body.authToken && partPayload.tokenHash && (await sha256HexAsync(body.authToken)) === partPayload.tokenHash;
  const okPin = body.pinHash && partPayload.pinHash && body.pinHash === partPayload.pinHash;
  if (!okToken && !okPin) {
    return jsonResponse({ ok: false, error: "Nicht autorisiert" }, 403);
  }

  const pushDocId = `ps_${roundId}_${pid}`;
  const existingRes = await appwriteFetch("GET", `/databases/${DB_ID}/collections/${COL_ID}/documents/${pushDocId}`);
  let existingSubs = [];
  if (existingRes.status >= 200 && existingRes.status < 300 && existingRes.json) {
    try {
      const parsed = JSON.parse(existingRes.json.payload || "{}");
      if (Array.isArray(parsed.subs)) existingSubs = parsed.subs;
    } catch (_) {}
  }

  const cleanOpenPath = String(body.openPath || `/?r=${encodeURIComponent(roundId)}`).startsWith("/")
    ? String(body.openPath)
    : `/?r=${encodeURIComponent(roundId)}`;

  const newEntry = {
    endpoint: String(body.subscription.endpoint),
    keys: {
      p256dh: String(body.subscription.keys.p256dh || ""),
      auth: String(body.subscription.keys.auth || "")
    },
    openPath: cleanOpenPath,
    updatedAt: new Date().toISOString()
  };

  const filtered = existingSubs.filter((s) => s && s.endpoint && s.endpoint !== newEntry.endpoint);
  filtered.push(newEntry);
  const keptSubs = filtered.slice(-5);

  await upsertPushDoc(roundId, pid, { pid, subs: keptSubs });

  if (body.sendWelcome) {
    try {
      await sendWebPushNotification(
        newEntry,
        {
          title: "✅ Handy-Meldungen sind eingeschaltet!",
          body: "Dein Handy klingelt ab jetzt automatisch bei neuen Wichtel-Nachrichten oder Wünschen.",
          url: cleanOpenPath,
          tag: `wichtel-welcome-${roundId}-${pid}`
        },
        env.VAPID_PRIVATE_JWK
      );
    } catch (_) {}
  }

  return jsonResponse({ ok: true, activeDevices: keptSubs.length });
}

async function handlePushStatus(url) {
  const roundId = (url.searchParams.get("r") || "").trim();
  if (!roundId) return jsonResponse({ enabledPids: [] });
  const res = await appwriteFetch(
    "GET",
    `/databases/${DB_ID}/collections/${COL_ID}/documents?queries[]=${qEqual("roundId", `push_${roundId}`)}&queries[]=${qLimit(100)}`
  );
  const docs = (res.json && res.json.documents) || [];
  const enabledPids = [];
  for (const d of docs) {
    try {
      const p = JSON.parse(d.payload || "{}");
      if (p.pid && Array.isArray(p.subs) && p.subs.length > 0) {
        enabledPids.push(Number(p.pid));
      }
    } catch (_) {}
  }
  return jsonResponse({ enabledPids });
}

async function handlePushEvent(request, env) {
  if (!env.VAPID_PRIVATE_JWK) {
    return jsonResponse({ ok: false, error: "VAPID key not configured" }, 500);
  }
  const body = await request.json().catch(() => null);
  if (!body || !body.roundId || !body.eventType) {
    return jsonResponse({ ok: false, error: "Ungültiges Event" }, 400);
  }
  const roundId = String(body.roundId).trim();
  const eventType = String(body.eventType);

  const [roundDocsRes, pushDocsRes] = await Promise.all([
    appwriteFetch(
      "GET",
      `/databases/${DB_ID}/collections/${COL_ID}/documents?queries[]=${qEqual("roundId", roundId)}&queries[]=${qLimit(250)}`
    ),
    appwriteFetch(
      "GET",
      `/databases/${DB_ID}/collections/${COL_ID}/documents?queries[]=${qEqual("roundId", `push_${roundId}`)}&queries[]=${qLimit(100)}`
    )
  ]);

  const roundDocs = (roundDocsRes.json && roundDocsRes.json.documents) || [];
  const pushDocs = (pushDocsRes.json && pushDocsRes.json.documents) || [];

  const subsByPid = new Map();
  for (const pd of pushDocs) {
    try {
      const parsed = JSON.parse(pd.payload || "{}");
      if (parsed.pid && Array.isArray(parsed.subs) && parsed.subs.length > 0) {
        subsByPid.set(Number(parsed.pid), parsed.subs);
      }
    } catch (_) {}
  }

  const parts = [];
  for (const d of roundDocs) {
    if (d.docType === "PART") {
      try {
        const pObj = JSON.parse(d.payload || "{}");
        const box = await unsealWichtelBoxAsync(pObj.sealedBox, roundId, Number(pObj.pid));
        parts.push({ pid: Number(pObj.pid), name: String(pObj.name || ""), box });
      } catch (_) {}
    }
  }

  // Ziel-Empfänger rein im Arbeitsspeicher ermitteln (100 % anonym!)
  const deliveries = []; // { pid, title, body, tag }
  if (eventType === "NEW_MESSAGE") {
    const slotKey = String(body.slotKey || "");
    const fromRole = String(body.fromRole || "");
    if (fromRole === "SANTA") {
      const targetPart = parts.find((p) => p.box && p.box.inThreadId === slotKey);
      if (targetPart) {
        deliveries.push({
          pid: targetPart.pid,
          title: "🎄 Neue Nachricht von deinem Wichtel!",
          body: "Dein geheimer Wichtel hat dir geschrieben. Tippe hier zum Lesen & Antworten!",
          tag: `wichtel-msg-${roundId}-${targetPart.pid}`
        });
      }
    } else if (fromRole === "TARGET") {
      const santaPart = parts.find((p) => p.box && p.box.outThreadId === slotKey);
      const targetPart = parts.find((p) => p.box && p.box.inThreadId === slotKey);
      const senderFirst = targetPart ? targetPart.name.trim().split(" ")[0] : "Dein Wichtelkind";
      if (santaPart) {
        deliveries.push({
          pid: santaPart.pid,
          title: `🎁 Nachricht von ${senderFirst}!`,
          body: `${senderFirst} hat dir beim Wichteln geschrieben. Tippe hier zum Lesen!`,
          tag: `wichtel-msg-${roundId}-${santaPart.pid}`
        });
      }
    }
  } else if (eventType === "WISH_UPDATED") {
    const fromPid = Number(body.fromPid || 0);
    const wishOwner = parts.find((p) => p.pid === fromPid);
    const santaPart = parts.find((p) => p.box && Number(p.box.targetPid) === fromPid);
    const ownerFirst = wishOwner ? wishOwner.name.trim().split(" ")[0] : "Dein Wichtelkind";
    if (santaPart) {
      deliveries.push({
        pid: santaPart.pid,
        title: `📝 Wunschzettel von ${ownerFirst}!`,
        body: `${ownerFirst} hat Wünsche fürs Wichteln gespeichert. Tippe hier zum Ansehen!`,
        tag: `wichtel-wish-${roundId}-${santaPart.pid}`
      });
    }
  } else if (eventType === "ROUND_REVEALED") {
    for (const p of parts) {
      deliveries.push({
        pid: p.pid,
        title: "🎉 Wichtel-Auflösung ist da!",
        body: "Alle Paarungen eurer Wichtelrunde sind jetzt aufgedeckt. Tippe hier zum Ansehen!",
        tag: `wichtel-reveal-${roundId}`
      });
    }
  } else if (eventType === "TEST_SELF") {
    const selfPid = Number(body.fromPid || 0);
    if (selfPid > 0) {
      deliveries.push({
        pid: selfPid,
        title: "🔔 Test erfolgreich!",
        body: "Dein Handy empfängt Wichtel-Meldungen einwandfrei – auch bei geschlossenem Browser!",
        tag: `wichtel-test-${roundId}-${selfPid}`
      });
    }
  }

  let deliveredCount = 0;
  for (const item of deliveries) {
    const subs = subsByPid.get(item.pid);
    if (!subs || subs.length === 0) continue;
    const stillValid = [];
    let pruned = false;
    for (const sub of subs) {
      try {
        const res = await sendWebPushNotification(
          sub,
          {
            title: item.title,
            body: item.body,
            url: sub.openPath || `/?r=${encodeURIComponent(roundId)}`,
            tag: item.tag
          },
          env.VAPID_PRIVATE_JWK
        );
        if (res.status === 404 || res.status === 410) {
          pruned = true;
        } else {
          stillValid.push(sub);
          if (res.ok) deliveredCount++;
        }
      } catch (_) {
        stillValid.push(sub);
      }
    }
    if (pruned) {
      await upsertPushDoc(roundId, item.pid, { pid: item.pid, subs: stillValid });
    }
  }

  return jsonResponse({ ok: true, delivered: deliveredCount });
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: CORS_HEADERS });
    }

    if (url.pathname === "/api/push-config" && request.method === "GET") {
      return jsonResponse({ publicKey: VAPID_PUBLIC_KEY });
    }
    if (url.pathname === "/api/push-status" && request.method === "GET") {
      return handlePushStatus(url);
    }
    if (url.pathname === "/api/push-subscribe" && request.method === "POST") {
      return handlePushSubscribe(request, env);
    }
    if (url.pathname === "/api/push-event" && request.method === "POST") {
      return handlePushEvent(request, env);
    }

    // Appwrite REST Proxy unter /v1/*
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

    if (url.pathname === "/wichteln.html") {
      const rootUrl = new URL("/" + url.search, url.origin);
      return env.ASSETS.fetch(new Request(rootUrl.toString(), request));
    }

    return env.ASSETS.fetch(request);
  }
};
