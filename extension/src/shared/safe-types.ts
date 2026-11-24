import type { KdfType, SafeDocumentCategory, SafeItemMetadata } from "@openvault/crypto-core";

export interface SafeConfigDto {
  initialized: boolean;
  version?: number;
  kdfType?: KdfType;
  kdfIterations?: number;
  kdfMemory?: number | null;
  kdfParallelism?: number | null;
  passcodeSalt?: string | null;
  passcodeEncryptedSafeKey?: string | null;
  recoverySalt?: string | null;
  recoveryKdfIterations?: number;
  recoveryEncryptedSafeKey?: string | null;
  autoLockMinutes?: number;
  failedAttempts?: number;
  lockedUntilMs?: number | null;
  recoveryRequired?: boolean;
  usedBytes?: number;
  itemCount?: number;
  maxFileBytes: number;
  maxThumbBytes?: number;
  revisionDate?: string;
}

export interface SafeItemDto {
  id: string;
  status: string;
  category: SafeDocumentCategory;
  encryptedMetadata: string;
  hasThumbnail: boolean;
  blobSizeBytes: number;
  thumbSizeBytes: number;
  blobSha256: string | null;
  favorite: boolean;
  deletedAt: string | null;
  revisionDate: string;
  createdAt: string;
  updatedAt: string;
}

export interface SafeLockoutState {
  failedAttempts: number;
  lockedUntilMs: number | null;
  recoveryRequired: boolean;
  cachedRevisionDate?: string | null;
  cachedConfig?: SafeConfigDto | null;
}

export interface SafeSessionSecrets {
  safeKeyB64: string;
  unlockedAtMs: number;
  autoLockMinutes: number;
  expiresAtMs: number;
}

export interface SafeStatusResponse {
  initialized: boolean;
  unlocked: boolean;
  autoLockMinutes: number;
  unlockedExpiresAtMs: number | null;
  failedAttempts: number;
  lockedUntilMs: number | null;
  recoveryRequired: boolean;
  usedBytes: number;
  itemCount: number;
  maxFileBytes: number;
  hasRecoveryConfig: boolean;
}

export interface DecryptedSafeItem {
  id: string;
  category: SafeDocumentCategory;
  metadata: SafeItemMetadata;
  hasThumbnail: boolean;
  blobSizeBytes: number;
  thumbSizeBytes: number;
  blobSha256: string | null;
  favorite: boolean;
  deletedAt: string | null;
  revisionDate: string;
  createdAt: string;
  updatedAt: string;
}

export interface SafePrepareUploadResult {
  serverUrl: string;
  accessToken: string;
  itemId: string;
  fileKeyB64: string;
  thumbKeyB64: string | null;
}

export interface SafeBlobAuthResult {
  serverUrl: string;
  accessToken: string;
}
