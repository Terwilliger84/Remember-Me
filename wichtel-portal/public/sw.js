// ============================================================================
// Service Worker für automatische Handy-Meldungen (Web-Push) beim Wichteln
// Weckt das Handy auch bei geschlossenem Browser auf!
// ============================================================================

self.addEventListener("install", (event) => {
  event.waitUntil(self.skipWaiting());
});

self.addEventListener("activate", (event) => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener("push", (event) => {
  let payload = {
    title: "🎄 Neuigkeit beim Wichteln!",
    body: "Tippe hier, um deine Wichtel-Seite zu öffnen.",
    url: "/",
    tag: "wichtel-update"
  };

  if (event.data) {
    try {
      const parsed = event.data.json();
      if (parsed && typeof parsed === "object") {
        payload = { ...payload, ...parsed };
      }
    } catch (_) {
      try {
        payload.body = event.data.text() || payload.body;
      } catch (_) {}
    }
  }

  const notifyPromise = self.registration.showNotification(payload.title, {
    body: payload.body,
    icon: "/icon.svg",
    badge: "/icon.svg",
    tag: payload.tag || "wichtel-update",
    renotify: true,
    requireInteraction: true,
    vibrate: [250, 120, 250, 120, 300],
    data: {
      url: payload.url || "/"
    }
  });

  const refreshClientsPromise = self.clients
    .matchAll({ type: "window", includeUncontrolled: true })
    .then((windowClients) => {
      for (const client of windowClients) {
        try {
          client.postMessage({ type: "WICHTEL_PUSH_RECEIVED", payload });
        } catch (_) {}
      }
    });

  event.waitUntil(Promise.all([notifyPromise, refreshClientsPromise]));
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const relUrl = (event.notification && event.notification.data && event.notification.data.url) || "/";
  const targetUrl = new URL(relUrl, self.location.origin).href;

  event.waitUntil(
    self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((windowClients) => {
      for (const client of windowClients) {
        if (client.url === targetUrl && "focus" in client) {
          return client.focus();
        }
      }
      for (const client of windowClients) {
        if (client.url.startsWith(self.location.origin) && "navigate" in client && "focus" in client) {
          return client.navigate(targetUrl).then((c) => (c ? c.focus() : null));
        }
      }
      if (self.clients.openWindow) {
        return self.clients.openWindow(targetUrl);
      }
      return null;
    })
  );
});
