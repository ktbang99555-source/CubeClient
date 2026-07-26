/**
 * Decides what the renderer is allowed to see.
 *
 * The backend's `auth_result` event carries a Minecraft access token. The renderer displays
 * untrusted profile data and has no need for a credential, so this is an allowlist rather than
 * a "strip the token field" step — a new credential-bearing event added later is excluded by
 * default instead of leaking until someone remembers to filter it.
 */
const RENDERER_VISIBLE_TYPES = new Set([
  'profiles',
  'progress',
  'error',
  'launched',
  'exited',
  'backend_exit',
  'device_code',
  'login_success',
  'pong',
]);

/**
 * Field names that must never reach the renderer on ANY event type. The type allowlist is the
 * primary control; this is defence in depth for the case where a credential is later attached
 * to an otherwise-innocuous event.
 */
const CREDENTIAL_FIELDS = new Set([
  'accessToken',
  'access_token',
  'refreshToken',
  'refresh_token',
  'token',
  'password',
  'secret',
]);

/**
 * @returns {object|null} the event to forward to the renderer, or null to withhold it.
 */
function sanitizeForRenderer(event) {
  if (!event || typeof event.type !== 'string') return null;

  if (event.type === 'auth_result') {
    // Consumed by the main process. Tell the renderer someone logged in, without the token.
    return { type: 'login_success', username: event.username, uuid: event.uuid };
  }

  if (!RENDERER_VISIBLE_TYPES.has(event.type)) return null;

  const safe = {};
  for (const [key, value] of Object.entries(event)) {
    if (!CREDENTIAL_FIELDS.has(key)) {
      safe[key] = value;
    }
  }
  return safe;
}

module.exports = { sanitizeForRenderer, RENDERER_VISIBLE_TYPES, CREDENTIAL_FIELDS };
