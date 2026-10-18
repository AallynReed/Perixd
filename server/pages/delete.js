// Derives the space id and token from the pairing secret exactly as the app does (HKDF-SHA256, 32-byte zero salt).
function perixdDelete(text) {
  const form = document.getElementById("form");
  const result = document.getElementById("result");

  function fromBase64Url(s) {
    const b64 = s.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - (s.length % 4)) % 4);
    return Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
  }

  function toBase64Url(bytes) {
    return btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  }

  async function hkdf(secret, info, length) {
    const key = await crypto.subtle.importKey("raw", secret, "HKDF", false, ["deriveBits"]);
    const bits = await crypto.subtle.deriveBits(
      { name: "HKDF", hash: "SHA-256", salt: new Uint8Array(32), info: new TextEncoder().encode(info) },
      key,
      length * 8,
    );
    return new Uint8Array(bits);
  }

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    let secret;
    try {
      const url = new URL(document.getElementById("link").value.trim());
      if (url.protocol !== "perixd:") throw new Error();
      secret = fromBase64Url(url.searchParams.get("k") || "");
      if (secret.length !== 32) throw new Error();
    } catch {
      result.textContent = text.wrong;
      return;
    }
    result.textContent = text.working;
    const space = Array.from(await hkdf(secret, "perixd space", 16), (b) => b.toString(16).padStart(2, "0")).join("");
    const token = toBase64Url(await hkdf(secret, "perixd auth", 32));
    try {
      const response = await fetch("/v1/spaces/" + space, { method: "DELETE", headers: { Authorization: "Bearer " + token } });
      result.textContent = response.status === 204 ? text.done : response.status === 404 ? text.missing : text.failed;
    } catch {
      result.textContent = text.failed;
    }
  });
}
