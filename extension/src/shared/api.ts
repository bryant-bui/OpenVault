import type {
  CipherPayload,
  EncryptedImportPayload,
  FolderPayload,
  KdfConfig,
  KdfType,
} from "@openvault/crypto-core";
import type { R2BackupItem } from "./types.js";

export class ApiError extends Error {
  readonly status: number;
  constructor(status: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

export function normalizeServerUrl(url: string): string {
  const trimmed = url.trim().replace(/\/+$/, "");
  const withProto = /^https?:\/\//i.test(trimmed) ? trimmed : `https://${trimmed}`;
  // Validate URL format
  const parsed = new URL(withProto);
  return `${parsed.origin}${parsed.pathname.replace(/\/+$/, "")}`;
}

async function parseErrorMessage(res: Response): Promise<string> {
  try {
    const body = (await res.json()) as {
      message?: string;
      error_description?: string;
      error?: string;
      ErrorModel?: { Message?: string };
    };
    return (
      body.ErrorModel?.Message ??
      body.message ??
      body.error_description ??
      body.error ??
      `HTTP ${res.status}`
    );
  } catch {
    return `HTTP ${res.status}`;
  }
}

async function requestJson<T>(
  serverUrl: string,
  path: string,
  init: RequestInit = {},
  accessToken?: string,
): Promise<T> {
  const headers = new Headers(init.headers);
  if (accessToken) {
    headers.set("Authorization", `Bearer ${accessToken}`);
  }
  if (init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const res = await fetch(`${normalizeServerUrl(serverUrl)}${path}`, {
    ...init,
    headers,
  });

  if (!res.ok) {
    throw new ApiError(res.status, await parseErrorMessage(res));
  }
  if (res.status === 204) {
    return undefined as T;
  }
  return (await res.json()) as T;
}

export interface TokenResponse {
  access_token: string;
  expires_in: number;
  token_type: string;
  refresh_token: string;
  Key: string;
  PrivateKey: string | null;
  Kdf: KdfType;
  KdfIterations: number;
  KdfMemory?: number | null;
  KdfParallelism?: number | null;
}

export interface SyncResponse {
  profile: {
    id: string;
    email: string;
    name: string | null;
    key: string;
    privateKey: string | null;
    premium: boolean;
  };
  folders: FolderPayload[];
  ciphers: CipherPayload[];
}

export async function apiPrelogin(serverUrl: string, email: string): Promise<KdfConfig> {
  const data = await requestJson<{
    kdf?: KdfType;
    Kdf?: KdfType;
    kdfIterations?: number;
    KdfIterations?: number;
    kdfMemory?: number | null;
    KdfMemory?: number | null;
    kdfParallelism?: number | null;
    KdfParallelism?: number | null;
  }>(serverUrl, "/identity/accounts/prelogin", {
    method: "POST",
    body: JSON.stringify({ email: email.trim().toLowerCase() }),
  });

  return {
    kdf: data.kdf ?? data.Kdf ?? 1,
    kdfIterations: data.kdfIterations ?? data.KdfIterations ?? 3,
    kdfMemory: data.kdfMemory ?? data.KdfMemory ?? 64,
    kdfParallelism: data.kdfParallelism ?? data.KdfParallelism ?? 4,
  };
}

export async function apiRegister(
  serverUrl: string,
  payload: {
    email: string;
    name: string;
    masterPasswordHash: string;
    masterPasswordHint?: string;
    userSymmetricKey: string;
    kdf: KdfType;
    kdfIterations: number;
    kdfMemory?: number | null;
    kdfParallelism?: number | null;
    userAsymmetricKeys: { publicKey: string; encryptedPrivateKey: string };
  },
): Promise<{ id: string; email: string }> {
  return requestJson(serverUrl, "/identity/accounts/register/finish", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export async function apiLoginPassword(
  serverUrl: string,
  email: string,
  masterPasswordHash: string,
  deviceIdentifier: string,
): Promise<TokenResponse> {
  const form = new URLSearchParams({
    grant_type: "password",
    username: email.trim().toLowerCase(),
    password: masterPasswordHash,
    scope: "api offline_access",
    client_id: "browser",
    deviceType: "2",
    deviceIdentifier,
    deviceName: "OpenVault Chrome Extension",
  });

  return requestJson<TokenResponse>(serverUrl, "/identity/connect/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: form.toString(),
  });
}

export async function apiRefreshToken(
  serverUrl: string,
  refreshToken: string,
): Promise<TokenResponse> {
  const form = new URLSearchParams({
    grant_type: "refresh_token",
    refresh_token: refreshToken,
    client_id: "browser",
  });

  return requestJson<TokenResponse>(serverUrl, "/identity/connect/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: form.toString(),
  });
}

export async function apiGetRevisionDate(serverUrl: string, accessToken: string): Promise<number> {
  return requestJson<number>(serverUrl, "/api/accounts/revision-date", { method: "GET" }, accessToken);
}

export async function apiSync(serverUrl: string, accessToken: string): Promise<SyncResponse> {
  return requestJson<SyncResponse>(serverUrl, "/api/sync", { method: "GET" }, accessToken);
}

export async function apiCreateCipher(
  serverUrl: string,
  accessToken: string,
  cipher: CipherPayload,
): Promise<CipherPayload> {
  return requestJson<CipherPayload>(
    serverUrl,
    "/api/ciphers",
    { method: "POST", body: JSON.stringify(cipher) },
    accessToken,
  );
}

export async function apiUpdateCipher(
  serverUrl: string,
  accessToken: string,
  id: string,
  cipher: CipherPayload,
): Promise<CipherPayload> {
  return requestJson<CipherPayload>(
    serverUrl,
    `/api/ciphers/${encodeURIComponent(id)}`,
    { method: "PUT", body: JSON.stringify(cipher) },
    accessToken,
  );
}

export async function apiSoftDeleteCipher(
  serverUrl: string,
  accessToken: string,
  id: string,
): Promise<void> {
  await requestJson<void>(
    serverUrl,
    `/api/ciphers/${encodeURIComponent(id)}/delete`,
    { method: "PUT" },
    accessToken,
  );
}

export async function apiRestoreCipher(
  serverUrl: string,
  accessToken: string,
  id: string,
): Promise<CipherPayload> {
  return requestJson<CipherPayload>(
    serverUrl,
    `/api/ciphers/${encodeURIComponent(id)}/restore`,
    { method: "PUT" },
    accessToken,
  );
}

export async function apiHardDeleteCipher(
  serverUrl: string,
  accessToken: string,
  id: string,
): Promise<void> {
  await requestJson<void>(
    serverUrl,
    `/api/ciphers/${encodeURIComponent(id)}`,
    { method: "DELETE" },
    accessToken,
  );
}

export async function apiCreateFolder(
  serverUrl: string,
  accessToken: string,
  nameEncrypted: string,
): Promise<FolderPayload> {
  return requestJson<FolderPayload>(
    serverUrl,
    "/api/folders",
    { method: "POST", body: JSON.stringify({ name: nameEncrypted }) },
    accessToken,
  );
}

export async function apiUpdateFolder(
  serverUrl: string,
  accessToken: string,
  id: string,
  nameEncrypted: string,
): Promise<FolderPayload> {
  return requestJson<FolderPayload>(
    serverUrl,
    `/api/folders/${encodeURIComponent(id)}`,
    { method: "PUT", body: JSON.stringify({ name: nameEncrypted }) },
    accessToken,
  );
}

export async function apiDeleteFolder(
  serverUrl: string,
  accessToken: string,
  id: string,
): Promise<void> {
  await requestJson<void>(
    serverUrl,
    `/api/folders/${encodeURIComponent(id)}`,
    { method: "DELETE" },
    accessToken,
  );
}

export async function apiImportVault(
  serverUrl: string,
  accessToken: string,
  payload: EncryptedImportPayload,
): Promise<{ importedFolders: number; importedCiphers: number }> {
  return requestJson(
    serverUrl,
    "/api/ciphers/import",
    { method: "POST", body: JSON.stringify(payload) },
    accessToken,
  );
}

export async function apiTriggerR2Backup(
  serverUrl: string,
  accessToken: string,
): Promise<{ key: string; sha256: string; cipherCount: number; bytes: number; createdAt: string }> {
  return requestJson(serverUrl, "/api/openvault/backup", { method: "POST" }, accessToken);
}

export async function apiListR2Backups(
  serverUrl: string,
  accessToken: string,
): Promise<{ backups: R2BackupItem[] }> {
  return requestJson(serverUrl, "/api/openvault/backups", { method: "GET" }, accessToken);
}

export async function apiLogout(
  serverUrl: string,
  accessToken: string,
  refreshToken: string,
  allDevices = false,
): Promise<void> {
  await requestJson<void>(
    serverUrl,
    "/api/accounts/logout",
    { method: "POST", body: JSON.stringify({ refreshToken, allDevices }) },
    accessToken,
  );
}
