import {
  WEBAUTHN_BRIDGE_CHANNEL,
  type SerializedCreateResponseData,
  type SerializedGetResponseData,
  type SerializedWebAuthnRequest,
  type SerializedWebAuthnResponse,
  type WebAuthnBridgeRequestEnvelope,
  type WebAuthnBridgeResponseEnvelope,
} from "../shared/passkey-bridge.js";

/**
 * OpenVault WebAuthn MAIN-World Interceptor (document_start).
 * Intercepts navigator.credentials.create/get ONLY when `options.publicKey` is supplied,
 * communicating with our ISOLATED content script via same-window structured-clone postMessage
 * (never leaking secrets — only the public WebAuthn request & response already owned by the page).
 * Always falls back cleanly to the browser's native authenticator when disabled,
 * when no matching passkey exists, or when the user chooses "Dùng trình duyệt / thiết bị".
 */
(() => {
  const FLAG = "__openvault_webauthn_installed__";
  if ((window as unknown as Record<string, unknown>)[FLAG]) return;
  if (!navigator.credentials || typeof navigator.credentials.create !== "function") return;
  (window as unknown as Record<string, unknown>)[FLAG] = true;

  const nativeCreate = navigator.credentials.create.bind(navigator.credentials);
  const nativeGet = navigator.credentials.get.bind(navigator.credentials);

  if (
    typeof PublicKeyCredential !== "undefined" &&
    typeof PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable === "function"
  ) {
    PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable = () => Promise.resolve(true);
  }

  function toUint8Array(source: BufferSource): Uint8Array {
    if (source instanceof ArrayBuffer) return new Uint8Array(source);
    return new Uint8Array(source.buffer, source.byteOffset, source.byteLength);
  }

  function bytesToBase64Url(bytes: Uint8Array): string {
    let bin = "";
    for (let i = 0; i < bytes.length; i++) {
      bin += String.fromCharCode(bytes[i]!);
    }
    return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
  }

  function base64UrlToArrayBuffer(b64Url: string): ArrayBuffer {
    const padded = b64Url.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - (b64Url.length % 4)) % 4);
    const bin = atob(padded);
    const bytes = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) {
      bytes[i] = bin.charCodeAt(i);
    }
    return bytes.buffer;
  }

  function dispatchBridgeRequest(
    payload: SerializedWebAuthnRequest,
    signal?: AbortSignal,
  ): Promise<SerializedWebAuthnResponse> {
    return new Promise((resolve) => {
      let settled = false;
      const done = (res: SerializedWebAuthnResponse) => {
        if (settled) return;
        settled = true;
        window.removeEventListener("message", onMessage);
        signal?.removeEventListener("abort", onAbort);
        window.clearTimeout(timer);
        resolve(res);
      };

      const onMessage = (event: MessageEvent) => {
        if (event.source !== window) return;
        const msg = event.data as Partial<WebAuthnBridgeResponseEnvelope> | null | undefined;
        if (
          !msg ||
          msg.channel !== WEBAUTHN_BRIDGE_CHANNEL ||
          msg.direction !== "isolated-to-main" ||
          !msg.payload ||
          msg.payload.requestId !== payload.requestId
        ) {
          return;
        }
        done(msg.payload);
      };

      const onAbort = () => {
        done({ requestId: payload.requestId, action: "cancel", reason: "Aborted" });
      };

      // Generous timeout for user PIN/password entry, clean fallback if bridge never replies
      const timer = window.setTimeout(() => {
        done({ requestId: payload.requestId, action: "fallback" });
      }, 120_000);

      window.addEventListener("message", onMessage);
      if (signal) {
        if (signal.aborted) {
          onAbort();
          return;
        }
        signal.addEventListener("abort", onAbort, { once: true });
      }

      const envelope: WebAuthnBridgeRequestEnvelope = {
        channel: WEBAUTHN_BRIDGE_CHANNEL,
        direction: "main-to-isolated",
        payload,
      };
      const targetOrigin =
        window.location.origin && window.location.origin !== "null"
          ? window.location.origin
          : "*";
      window.postMessage(envelope, targetOrigin);
    });
  }

  function buildCreateCredential(data: SerializedCreateResponseData): PublicKeyCredential {
    const rawId = base64UrlToArrayBuffer(data.credentialIdB64Url);
    const clientDataJSON = base64UrlToArrayBuffer(data.clientDataJsonB64Url);
    const attestationObject = base64UrlToArrayBuffer(data.attestationObjectB64Url);
    const authenticatorData = base64UrlToArrayBuffer(data.authenticatorDataB64Url);
    const publicKeySpki = base64UrlToArrayBuffer(data.publicKeySpkiB64Url);
    const transports = ["internal", "hybrid"];

    const responseObj: Record<string, unknown> = {
      clientDataJSON,
      attestationObject,
      getTransports: () => [...transports],
      getAuthenticatorData: () => authenticatorData,
      getPublicKey: () => publicKeySpki,
      getPublicKeyAlgorithm: () => -7,
    };
    if (typeof AuthenticatorAttestationResponse !== "undefined") {
      Object.setPrototypeOf(responseObj, AuthenticatorAttestationResponse.prototype);
    }

    const credObj: Record<string, unknown> = {
      id: data.credentialIdB64Url,
      rawId,
      type: "public-key",
      authenticatorAttachment: "platform",
      response: responseObj,
      getClientExtensionResults: () => ({ credProps: { rk: true } }),
      toJSON: () => ({
        id: data.credentialIdB64Url,
        rawId: data.credentialIdB64Url,
        type: "public-key",
        authenticatorAttachment: "platform",
        response: {
          clientDataJSON: data.clientDataJsonB64Url,
          attestationObject: data.attestationObjectB64Url,
          transports: [...transports],
          authenticatorData: data.authenticatorDataB64Url,
          publicKey: data.publicKeySpkiB64Url,
          publicKeyAlgorithm: -7,
        },
        clientExtensionResults: { credProps: { rk: true } },
      }),
    };
    if (typeof PublicKeyCredential !== "undefined") {
      Object.setPrototypeOf(credObj, PublicKeyCredential.prototype);
    }

    return credObj as unknown as PublicKeyCredential;
  }

  function buildGetCredential(data: SerializedGetResponseData): PublicKeyCredential {
    const rawId = base64UrlToArrayBuffer(data.credentialIdB64Url);
    const clientDataJSON = base64UrlToArrayBuffer(data.clientDataJsonB64Url);
    const authenticatorData = base64UrlToArrayBuffer(data.authenticatorDataB64Url);
    const signature = base64UrlToArrayBuffer(data.signatureDerB64Url);
    const userHandle = data.userHandleB64Url
      ? base64UrlToArrayBuffer(data.userHandleB64Url)
      : null;

    const responseObj: Record<string, unknown> = {
      clientDataJSON,
      authenticatorData,
      signature,
      userHandle,
    };
    if (typeof AuthenticatorAssertionResponse !== "undefined") {
      Object.setPrototypeOf(responseObj, AuthenticatorAssertionResponse.prototype);
    }

    const credObj: Record<string, unknown> = {
      id: data.credentialIdB64Url,
      rawId,
      type: "public-key",
      authenticatorAttachment: "platform",
      response: responseObj,
      getClientExtensionResults: () => ({}),
      toJSON: () => ({
        id: data.credentialIdB64Url,
        rawId: data.credentialIdB64Url,
        type: "public-key",
        authenticatorAttachment: "platform",
        response: {
          clientDataJSON: data.clientDataJsonB64Url,
          authenticatorData: data.authenticatorDataB64Url,
          signature: data.signatureDerB64Url,
          userHandle: data.userHandleB64Url ?? undefined,
        },
        clientExtensionResults: {},
      }),
    };
    if (typeof PublicKeyCredential !== "undefined") {
      Object.setPrototypeOf(credObj, PublicKeyCredential.prototype);
    }

    return credObj as unknown as PublicKeyCredential;
  }

  navigator.credentials.create = async function openVaultCreate(
    options?: CredentialCreationOptions,
  ): Promise<Credential | null> {
    const pk = options?.publicKey;
    if (!pk) {
      return nativeCreate(options);
    }

    // Only intercept when relying party accepts ES256 (-7) or omits pubKeyCredParams
    const params = pk.pubKeyCredParams ?? [];
    const supportsEs256 = params.length === 0 || params.some((p) => p.alg === -7);
    if (!supportsEs256) {
      return nativeCreate(options);
    }

    const rpId = (pk.rp?.id || window.location.hostname).toLowerCase();
    const requestId = `ov-pk-c-${crypto.randomUUID()}`;
    const res = await dispatchBridgeRequest(
      {
        kind: "create",
        requestId,
        origin: window.location.origin,
        rpId,
        rpName: pk.rp?.name || rpId,
        challengeB64Url: bytesToBase64Url(toUint8Array(pk.challenge)),
        userIdB64Url: bytesToBase64Url(toUint8Array(pk.user.id)),
        userName: pk.user.name || "",
        userDisplayName: pk.user.displayName || pk.user.name || "",
        excludeCredentialIds: (pk.excludeCredentials ?? []).map((c) =>
          bytesToBase64Url(toUint8Array(c.id)),
        ),
        discoverable: pk.authenticatorSelection?.residentKey !== "discouraged",
      },
      options.signal,
    );

    if (res.action === "ok" && res.kind === "create") {
      return buildCreateCredential(res.data);
    }
    if (res.action === "cancel") {
      throw new DOMException(res.reason || "The operation was aborted.", "NotAllowedError");
    }
    return nativeCreate(options);
  };

  navigator.credentials.get = async function openVaultGet(
    options?: CredentialRequestOptions,
  ): Promise<Credential | null> {
    const pk = options?.publicKey;
    if (!pk || options.mediation === "conditional") {
      return nativeGet(options);
    }

    const rpId = (pk.rpId || window.location.hostname).toLowerCase();
    const requestId = `ov-pk-g-${crypto.randomUUID()}`;
    const res = await dispatchBridgeRequest(
      {
        kind: "get",
        requestId,
        origin: window.location.origin,
        rpId,
        challengeB64Url: bytesToBase64Url(toUint8Array(pk.challenge)),
        allowCredentialIds: (pk.allowCredentials ?? []).map((c) =>
          bytesToBase64Url(toUint8Array(c.id)),
        ),
      },
      options.signal,
    );

    if (res.action === "ok" && res.kind === "get") {
      return buildGetCredential(res.data);
    }
    if (res.action === "cancel") {
      throw new DOMException(res.reason || "The operation was aborted.", "NotAllowedError");
    }
    return nativeGet(options);
  };
})();
