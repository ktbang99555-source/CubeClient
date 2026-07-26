const fs = require('fs');
const path = require('path');

/**
 * Stores the Minecraft session, with the access token encrypted at rest.
 *
 * Encryption goes through Electron's safeStorage, which on Windows uses DPAPI — the ciphertext
 * is bound to the OS user account, so another user (or a copied file) cannot read it. The
 * username and uuid are stored in the clear; they are not credentials.
 *
 * safeStorage is injected rather than required directly so this module is testable outside
 * Electron, where safeStorage does not exist.
 */
class AuthStore {
  constructor(authJsonPath, safeStorage) {
    this.authJsonPath = authJsonPath;
    this.safeStorage = safeStorage;
  }

  /** @returns {boolean} false when the OS has no encryption backend available. */
  isEncryptionAvailable() {
    return this.safeStorage.isEncryptionAvailable();
  }

  save({ username, uuid, accessToken }) {
    if (!this.isEncryptionAvailable()) {
      // Refuse rather than silently downgrade to plaintext: the user chose encrypted storage.
      throw new Error('OS encryption is unavailable, refusing to store the token unencrypted');
    }
    const encrypted = this.safeStorage.encryptString(accessToken);
    fs.mkdirSync(path.dirname(this.authJsonPath), { recursive: true });
    fs.writeFileSync(
      this.authJsonPath,
      JSON.stringify({ username, uuid, accessTokenEncrypted: encrypted.toString('base64') }),
      { mode: 0o600 }
    );
  }

  /** @returns {{username,uuid,accessToken}|null} null when nothing is stored or it is unreadable. */
  load() {
    if (!fs.existsSync(this.authJsonPath)) return null;

    let stored;
    try {
      stored = JSON.parse(fs.readFileSync(this.authJsonPath, 'utf8'));
    } catch (err) {
      return null;
    }
    if (!stored.accessTokenEncrypted) return null;

    try {
      const accessToken = this.safeStorage.decryptString(
        Buffer.from(stored.accessTokenEncrypted, 'base64')
      );
      return { username: stored.username, uuid: stored.uuid, accessToken };
    } catch (err) {
      // Ciphertext from another OS user or a different machine cannot be decrypted. Treat it
      // as "not logged in" rather than crashing the launcher.
      return null;
    }
  }

  clear() {
    if (fs.existsSync(this.authJsonPath)) {
      fs.rmSync(this.authJsonPath);
    }
  }
}

module.exports = { AuthStore };
