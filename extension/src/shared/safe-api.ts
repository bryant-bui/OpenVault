import type { SafeDocumentCategory } from "@openvault/crypto-core";
import { ApiError, normalizeServerUrl, parseErrorMessage, requestJson } from "./api.js";
import type { SafeConfigDto, SafeItemDto } from "./safe-types.js";

export async function apiGetSafeConfig(
  serverUrl: string,
  accessToken: string,
): Promise<SafeConfigDto> {
  return requestJson<SafeConfigDto>(
    serverUrl,
    "/api/openvault/safe/config",
    { method: "GET" },
    accessToken,
  );
}

export async function apiSetupSafe(
  serverUrl: string,
  accessToken: string,
  payload: {
    kdfType: number;
    kdfIterations: number;
    kdfMemory?: number | null;
    kdfParallelism?: number | null;
    passcodeSalt: string;
    passcodeEncryptedSafeKey: string;
    recoverySalt: string;
    recoveryKdfIterations: number;
    recoveryEncryptedSafeKey: string;
    autoLockMinutes?: number;
  },
): Promise<SafeConfigDto> {
  return requestJson<SafeConfigDto>(
    serverUrl,
    "/api/openvault/safe/setup",
    { method: "POST", body: JSON.stringify(payload) },
    accessToken,
  );
}

export async function apiReportSafeUnlockAttempt(
  serverUrl: string,
  accessToken: string,
  success: boolean,
): Promise<{
  ok: boolean;
  failedAttempts: number;
  lockedUntilMs: number | null;
  recoveryRequired: boolean;
}> {
  return requestJson(
    serverUrl,
    "/api/openvault/safe/unlock-attempt",
    { method: "POST", body: JSON.stringify({ success }) },
    accessToken,
  );
}

export async function apiUpdateSafePasscode(
  serverUrl: string,
  accessToken: string,
  payload: {
    mode: "change" | "recovery";
    kdfType: number;
    kdfIterations: number;
    kdfMemory?: number | null;
    kdfParallelism?: number | null;
    passcodeSalt: string;
    passcodeEncryptedSafeKey: string;
    recoverySalt?: string;
    recoveryKdfIterations?: number;
    recoveryEncryptedSafeKey?: string;
  },
): Promise<SafeConfigDto> {
  return requestJson<SafeConfigDto>(
    serverUrl,
    "/api/openvault/safe/passcode",
    { method: "PUT", body: JSON.stringify(payload) },
    accessToken,
  );
}

export async function apiPatchSafeSettings(
  serverUrl: string,
  accessToken: string,
  autoLockMinutes: number,
): Promise<{ ok: boolean; autoLockMinutes: number; revisionDate: string }> {
  return requestJson(
    serverUrl,
    "/api/openvault/safe/settings",
    { method: "PATCH", body: JSON.stringify({ autoLockMinutes }) },
    accessToken,
  );
}

export async function apiListSafeItems(
  serverUrl: string,
  accessToken: string,
): Promise<SafeItemDto[]> {
  const res = await requestJson<{ items: SafeItemDto[] }>(
    serverUrl,
    "/api/openvault/safe/items",
    { method: "GET" },
    accessToken,
  );
  return res.items ?? [];
}

export async function apiCreateSafeItem(
  serverUrl: string,
  accessToken: string,
  payload: {
    category: SafeDocumentCategory;
    encryptedMetadata: string;
    favorite?: boolean;
  },
): Promise<{ id: string; revisionDate: string }> {
  return requestJson(
    serverUrl,
    "/api/openvault/safe/items",
    { method: "POST", body: JSON.stringify(payload) },
    accessToken,
  );
}

export async function apiUploadSafeBinary(
  serverUrl: string,
  accessToken: string,
  itemId: string,
  kind: "blob" | "thumbnail",
  ovsfBytes: Uint8Array,
): Promise<SafeItemDto | { ok: boolean }> {
  const path =
    kind === "thumbnail"
      ? `/api/openvault/safe/items/${encodeURIComponent(itemId)}/thumbnail`
      : `/api/openvault/safe/items/${encodeURIComponent(itemId)}/blob`;

  const buf = new Uint8Array(ovsfBytes);
  const res = await fetch(`${normalizeServerUrl(serverUrl)}${path}`, {
    method: "PUT",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/octet-stream",
    },
    body: buf.buffer,
  });
  if (!res.ok) {
    throw new ApiError(res.status, await parseErrorMessage(res));
  }
  return (await res.json()) as SafeItemDto | { ok: boolean };
}

export async function apiDownloadSafeBinary(
  serverUrl: string,
  accessToken: string,
  itemId: string,
  kind: "blob" | "thumbnail",
  signal?: AbortSignal,
): Promise<Uint8Array<ArrayBuffer>> {
  const path =
    kind === "thumbnail"
      ? `/api/openvault/safe/items/${encodeURIComponent(itemId)}/thumbnail`
      : `/api/openvault/safe/items/${encodeURIComponent(itemId)}/blob`;

  const res = await fetch(`${normalizeServerUrl(serverUrl)}${path}`, {
    method: "GET",
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
    signal,
  });
  if (!res.ok) {
    throw new ApiError(res.status, await parseErrorMessage(res));
  }
  return new Uint8Array(await res.arrayBuffer());
}

export async function apiUpdateSafeItem(
  serverUrl: string,
  accessToken: string,
  id: string,
  payload: {
    category?: SafeDocumentCategory;
    encryptedMetadata?: string;
    favorite?: boolean;
  },
): Promise<SafeItemDto> {
  return requestJson<SafeItemDto>(
    serverUrl,
    `/api/openvault/safe/items/${encodeURIComponent(id)}`,
    { method: "PUT", body: JSON.stringify(payload) },
    accessToken,
  );
}

export async function apiSoftDeleteSafeItem(
  serverUrl: string,
  accessToken: string,
  id: string,
): Promise<void> {
  await requestJson<void>(
    serverUrl,
    `/api/openvault/safe/items/${encodeURIComponent(id)}/delete`,
    { method: "PUT" },
    accessToken,
  );
}

export async function apiRestoreSafeItem(
  serverUrl: string,
  accessToken: string,
  id: string,
): Promise<SafeItemDto> {
  return requestJson<SafeItemDto>(
    serverUrl,
    `/api/openvault/safe/items/${encodeURIComponent(id)}/restore`,
    { method: "PUT" },
    accessToken,
  );
}

export async function apiHardDeleteSafeItem(
  serverUrl: string,
  accessToken: string,
  id: string,
): Promise<void> {
  await requestJson<void>(
    serverUrl,
    `/api/openvault/safe/items/${encodeURIComponent(id)}`,
    { method: "DELETE" },
    accessToken,
  );
}

export async function apiResetSafe(
  serverUrl: string,
  accessToken: string,
  masterPasswordHash: string,
): Promise<void> {
  await requestJson<void>(
    serverUrl,
    "/api/openvault/safe/reset",
    { method: "DELETE", body: JSON.stringify({ masterPasswordHash }) },
    accessToken,
  );
}
