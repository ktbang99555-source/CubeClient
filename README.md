# CubeClient

An open-source launcher for Minecraft: Java Edition. Electron front end, Java back end.

It downloads the official game files from Mojang's own manifests, verifies every file by
checksum, provisions a matching Java runtime, and starts the game with an authenticated
Microsoft account.

> **Status:** the launcher core works and is covered by tests, but signing in does not work yet.
> The Azure application is awaiting Microsoft's review for Minecraft authentication, so
> `api.minecraftservices.com` currently answers `403 Invalid app registration`. Everything up to
> that point — device code, token polling, Xbox Live, XSTS — succeeds. See
> [Authentication](#authentication).

## What it does

- **Official files only.** Versions, libraries, the client jar, and assets all come from
  Mojang's published manifests (`piston-meta.mojang.com`, `resources.download.minecraft.net`).
  Nothing is repackaged, mirrored, or redistributed.
- **Verified downloads.** Every file is checked against the publisher's digest before use —
  SHA-1 for Mojang files, SHA-256 for Adoptium runtimes. A mismatch forces a re-download rather
  than being accepted.
- **Bundled runtimes.** The correct JRE (8 for 1.8.9, 17 for modern versions) is fetched from
  [Adoptium](https://adoptium.net/) into the launcher's own directory, so the game does not
  depend on whatever `java` happens to be on `PATH`.
- **Isolated profiles.** Each profile gets its own instance directory for saves, config, and
  mods, while libraries, versions, and assets are shared.

## Authentication

CubeClient uses the standard Microsoft device-code flow and nothing else:

```
Microsoft identity platform  ->  Xbox Live  ->  XSTS  ->  Minecraft Services
```

**It does not bypass authentication.** There is no offline/cracked account support for online
play. When no one is signed in, the launcher builds an explicitly offline session that can start
singleplayer but is rejected by every server — it is a fallback for "not signed in", not a way
to play unauthenticated.

Token handling:

- The access token reaches the Electron process on a dedicated event and is **never forwarded to
  the renderer** — [`rendererEvents.js`](ui/src/rendererEvents.js) is a type allowlist plus
  credential-field stripping.
- At rest it is encrypted with Electron's `safeStorage` (DPAPI on Windows) —
  [`authStore.js`](ui/src/authStore.js). The plaintext token is never written to disk.
- It is passed to the back end over **stdin**, not as a command-line argument, because arguments
  are visible to any process that can list the process table.
- [`Session`](backend/src/main/java/com/cubeclient/launcher/auth/Session.java) redacts the token
  in `toString()` so it cannot leak into a log line, and HTTP error paths never echo response
  bodies from authenticated endpoints.

The Azure application ID in
[`MicrosoftAuthClient`](backend/src/main/java/com/cubeclient/launcher/auth/MicrosoftAuthClient.java)
is a **public client** ID. Device-code clients have no client secret by design, and the ID is
embedded in every distributed copy — it is not a credential.

## Architecture

```
┌──────────────────┐    JSON lines on stdout    ┌────────────────────┐
│  Electron (UI)   │ ◄───────────────────────── │  Java backend jar  │
│  profiles, login │ ──── session on stdin ───► │  download, launch  │
└──────────────────┘                            └────────────────────┘
```

The two processes talk **only** over the child process's stdout/stdin — no sockets, no local HTTP
server, no ports. The UI holds no game logic; the back end holds no UI.

Layout under `%APPDATA%/CubeClient/`:

```
profiles.json          launcher profiles
auth.json              encrypted session
runtimes/<8|17>/       provisioned JREs
libraries/ versions/ assets/     shared game files
instances/<profile>/   per-profile saves, config, mods, logs
```

## Building

Requires Node.js and a Java 17 JDK. Gradle is provided by the wrapper.

```bash
cd backend && ./gradlew jar        # builds the backend jar
cd ui && npm install && npm start  # runs the launcher
```

Running the back-end jar needs a Java 17 on `PATH`; set `CUBECLIENT_JAVA` to point at one
otherwise. (The launcher provisions the *game's* runtime itself, but cannot bootstrap its own.)

## Tests

```bash
cd backend && ./gradlew test   # 53 tests
cd ui && npx jest              # 23 tests
```

## Not done yet

- Sign-in is blocked pending Microsoft's app review (see above).
- No token refresh — the stored token expires after roughly a day.
- Assets download sequentially; a first launch of a modern version is slow.
- Windows only in practice: the JRE unpacker handles `.zip`, not `.tar.gz`.
- No installer or packaging yet.

## Legal

Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.
CubeClient contains no Mojang code or assets; it only downloads them from Mojang's own servers
at runtime, using the signed-in user's own account.
