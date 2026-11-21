// EnergyGuard Link Guard: background service worker.
// The ONLY place that talks to the network, and only to the EnergyGuard app on 127.0.0.1 (contract: docs/API.md, #43).
// Fail open: app closed, slow (> 300 ms) or any error -> the link opens normally. Never break browsing.

const GUARD_URL = "http://127.0.0.1:8766/guard"; // change the port here if you set EG_GUARD_PORT
const TIMEOUT_MS = 300;
const ALLOW = { verdict: "allow" };

chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  if (sender.id !== chrome.runtime.id || !msg || msg.type !== "eg-guard") return false;
  check(msg.displayText, msg.href).then(sendResponse);
  return true; // async sendResponse
});

async function check(displayText, href) {
  if (typeof href !== "string" || !/^https?:\/\//i.test(href)) return ALLOW;
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), TIMEOUT_MS);
  try {
    const res = await fetch(GUARD_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      // Only the link: visible text + real href. No page content, no context.
      body: JSON.stringify({ displayText: String(displayText || "").slice(0, 1000), href, context: "" }),
      signal: ctrl.signal,
      credentials: "omit",
      cache: "no-store",
    });
    if (!res.ok) return failOpen(`app answered ${res.status}`);
    const v = await res.json();
    if (!v || !["allow", "warn", "block"].includes(v.verdict)) return failOpen("unexpected answer");
    return {
      verdict: v.verdict,
      reason: String(v.reason || "").slice(0, 500),
      signals: Array.isArray(v.signals) ? v.signals.slice(0, 8).map((s) => String(s).slice(0, 300)) : [],
      level: String(v.level || "").slice(0, 20),
    };
  } catch (e) {
    return failOpen(e && e.name === "AbortError" ? "no answer in 300 ms" : "app not running");
  } finally {
    clearTimeout(timer);
  }
}

function failOpen(why) {
  console.info(`[EnergyGuard] ${why}: link allowed (fail open)`);
  return { verdict: "allow", failOpen: true, why };
}
