/**
 * The one gate between the renderer and `shell.openExternal`.
 *
 * The renderer asks the main process to open a URL when the user presses
 * "브라우저에서 열기" in the sign-in modal, and the URL it passes came from a backend
 * event. Handing that straight to the OS would let anything that could put a string on
 * that event open an arbitrary address — or, with a `file:` URL, a local program.
 *
 * So this is an allowlist, not a blocklist: only Microsoft's device-login page over
 * https gets through, and everything else is refused without explanation. It lives in
 * its own module because a security control that cannot be unit-tested is a security
 * control nobody checks.
 */
const ALLOWED_HOSTS = new Set(['www.microsoft.com', 'microsoft.com']);

function isAllowedExternalUrl(url) {
  if (typeof url !== 'string') return false;

  let parsed;
  try {
    parsed = new URL(url);
  } catch (err) {
    // Not a URL at all. A bare path like "C:\Windows\System32\cmd.exe" lands here.
    return false;
  }

  if (parsed.protocol !== 'https:') return false;
  return ALLOWED_HOSTS.has(parsed.hostname);
}

module.exports = { isAllowedExternalUrl };
