// EnergyGuard Link Guard: content script (all http/https pages, top frame).
// On a click on a link to ANOTHER site: hold the navigation, ask the EnergyGuard app (via the background worker),
// then Allow -> open, Warn -> "Go back" / "Open anyway", Block -> red screen, only "Go back".
// Same-site links (SPA navigation) are never touched. Fail open on any problem. Server strings only via textContent.
(() => {
  "use strict";
  const TOTAL_TIMEOUT_MS = 800; // service worker wake-up + 300 ms app timeout
  const seenAtPointerDown = new WeakMap(); // <a> -> href before page scripts rewrite it (Gmail swaps in its redirector)
  let busy = false;

  const linkOf = (e) => {
    for (const el of e.composedPath()) {
      if (el instanceof HTMLAnchorElement && el.hasAttribute("href")) return el;
    }
    return null;
  };

  /** Real destination: the href seen at pointerdown, unwrapped from google.com/url?q=... redirectors. */
  const realHref = (a) => {
    try {
      const u = new URL(seenAtPointerDown.get(a) || a.href, location.href);
      if (/(^|\.)google\.[a-z.]+$/.test(u.hostname) && u.pathname === "/url") {
        const q = u.searchParams.get("q") || u.searchParams.get("url");
        if (q && /^https?:\/\//i.test(q)) return new URL(q).href;
      }
      return u.href;
    } catch {
      return null;
    }
  };

  const textOf = (a) =>
    (a.innerText || a.getAttribute("aria-label") || a.title || "").replace(/\s+/g, " ").trim().slice(0, 1000);

  window.addEventListener("pointerdown", (e) => {
    const a = linkOf(e);
    if (a) seenAtPointerDown.set(a, a.href);
  }, true);

  const onClick = (e) => {
    if (e.button !== 0 && e.button !== 1) return;
    const a = linkOf(e);
    if (!a || a.hasAttribute("download")) return;
    const href = realHref(a);
    if (!href || !/^https?:\/\//i.test(href)) return;
    let target;
    try { target = new URL(href); } catch { return; }
    if (target.origin === location.origin) return; // same site: leave SPA routing alone

    e.preventDefault();
    e.stopImmediatePropagation();
    if (busy) return;
    busy = true;
    const newTab = e.button === 1 || e.ctrlKey || e.metaKey || e.shiftKey ||
      (!!a.target && !["_self", "_top", "_parent"].includes(a.target.toLowerCase()));
    const go = () => {
      if (newTab) window.open(href, "_blank", "noopener,noreferrer");
      else location.assign(href);
    };
    ask(textOf(a), href).then((v) => {
      busy = false;
      if (v.failOpen) console.info(`[EnergyGuard] ${v.why || "no answer"}: link allowed (fail open)`);
      if (v.verdict === "block") showOverlay("block", v, target.hostname, null);
      else if (v.verdict === "warn") showOverlay("warn", v, target.hostname, go);
      else go();
    });
  };
  window.addEventListener("click", onClick, true);
  window.addEventListener("auxclick", onClick, true);

  const ask = (displayText, href) => {
    const timeout = new Promise((resolve) =>
      setTimeout(() => resolve({ verdict: "allow", failOpen: true, why: "no answer in time" }), TOTAL_TIMEOUT_MS));
    let msg;
    try {
      msg = chrome.runtime.sendMessage({ type: "eg-guard", displayText, href })
        .then((v) => v || { verdict: "allow", failOpen: true })
        .catch(() => ({ verdict: "allow", failOpen: true, why: "extension reloaded" }));
    } catch {
      msg = Promise.resolve({ verdict: "allow", failOpen: true, why: "extension reloaded" });
    }
    return Promise.race([msg, timeout]);
  };

  // --- Overlay (Shadow DOM so the page's CSS can't touch it, and it can't touch the page) ----------------------

  function showOverlay(kind, v, host, openAnyway) {
    document.getElementById("energyguard-overlay")?.remove();
    const root = document.createElement("div");
    root.id = "energyguard-overlay";
    const shadow = root.attachShadow({ mode: "closed" });
    const block = kind === "block";

    const style = document.createElement("style");
    style.textContent = `
      .bg { position: fixed; inset: 0; z-index: 2147483647; display: flex; align-items: center; justify-content: center;
            background: ${block ? "rgba(140, 16, 16, 0.97)" : "rgba(40, 28, 10, 0.92)"};
            font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; color: #fff; }
      .card { max-width: 860px; width: calc(100% - 64px); padding: 40px 48px; border-radius: 20px;
              background: ${block ? "#b71c1c" : "#3b2a12"}; box-shadow: 0 20px 60px rgba(0,0,0,.5);
              border: 3px solid ${block ? "#ff8a80" : "#ffb74d"}; }
      .tag { font-size: 18px; letter-spacing: .08em; text-transform: uppercase; opacity: .85; }
      h1 { font-size: 40px; line-height: 1.15; margin: 10px 0 16px; }
      .reason { font-size: 24px; line-height: 1.35; margin: 0 0 18px; }
      .host { font-size: 20px; margin: 0 0 12px; opacity: .9; }
      .host b { font-family: ui-monospace, Consolas, monospace; background: rgba(0,0,0,.25); padding: 2px 8px; border-radius: 6px; }
      ul { font-size: 20px; line-height: 1.4; margin: 0 0 28px; padding-left: 26px; }
      li { margin: 4px 0; }
      .row { display: flex; gap: 16px; flex-wrap: wrap; }
      button { font: inherit; font-size: 22px; font-weight: 600; padding: 14px 28px; border-radius: 12px; cursor: pointer; border: 0; }
      .back { background: #fff; color: ${block ? "#b71c1c" : "#3b2a12"}; }
      .anyway { background: transparent; color: #fff; border: 2px solid rgba(255,255,255,.6); }
    `;
    const bg = el("div", "bg");
    const card = el("div", "card");
    card.setAttribute("role", "alertdialog");
    card.append(
      el("div", "tag", `EnergyGuard${v.level ? " · energy level " + v.level : ""}`),
      el("h1", null, block ? "Link blocked" : "Are you sure?"),
      el("p", "reason", v.reason || (block ? "This link looks dangerous." : "This link looks suspicious.")),
    );
    const hostLine = el("p", "host", "It really goes to ");
    hostLine.append(el("b", null, host));
    card.append(hostLine);
    if (v.signals && v.signals.length) {
      const ul = el("ul");
      for (const s of v.signals) ul.append(el("li", null, s));
      card.append(ul);
    }
    const row = el("div", "row");
    const close = () => { root.remove(); document.removeEventListener("keydown", onKey, true); };
    const onKey = (e) => { if (e.key === "Escape") { e.stopPropagation(); close(); } };
    const back = el("button", "back", "Go back");
    back.addEventListener("click", close);
    row.append(back);
    if (!block && openAnyway) {
      const anyway = el("button", "anyway", "Open anyway");
      anyway.addEventListener("click", () => { close(); openAnyway(); });
      row.append(anyway);
    }
    card.append(row);
    bg.append(card);
    shadow.append(style, bg);
    (document.body || document.documentElement).append(root);
    document.addEventListener("keydown", onKey, true);
    back.focus();
  }

  function el(tag, cls, text) {
    const n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text != null) n.textContent = String(text);
    return n;
  }
})();
