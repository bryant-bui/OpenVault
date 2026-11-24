export enum KdfType {
  PBKDF2_SHA256 = 0,
  Argon2id = 1,
}

export interface KdfConfig {
  kdf: KdfType;
  kdfIterations: number;
  /** Argon2id memory in MiB (only for KdfType.Argon2id) */
  kdfMemory?: number | null;
  /** Argon2id parallelism / lanes (only for KdfType.Argon2id) */
  kdfParallelism?: number | null;
}

export const DEFAULT_PBKDF2_CONFIG: KdfConfig = {
  kdf: KdfType.PBKDF2_SHA256,
  kdfIterations: 600_000,
};

export const DEFAULT_ARGON2ID_CONFIG: KdfConfig = {
  kdf: KdfType.Argon2id,
  kdfIterations: 3,
  kdfMemory: 64,
  kdfParallelism: 4,
};

export enum EncryptionType {
  AesCbc256_HmacSha256_B64 = 2,
}

export interface SymmetricKey {
  /** 32-byte AES-256-CBC key */
  encKey: Uint8Array<ArrayBuffer>;
  /** 32-byte HMAC-SHA-256 key */
  macKey: Uint8Array<ArrayBuffer>;
  /** 64-byte concatenated raw key (encKey || macKey) */
  raw: Uint8Array<ArrayBuffer>;
}

export enum CipherType {
  Login = 1,
  SecureNote = 2,
  Card = 3,
  Identity = 4,
}

export enum SecureNoteType {
  Generic = 0,
  Software = 1,
  Passport = 2,
  IdCard = 3,
}

export enum FieldType {
  Text = 0,
  Hidden = 1,
  Boolean = 2,
  Linked = 3,
}

export interface CipherUri {
  uri: string | null;
  match?: number | null;
}

export interface Fido2Credential {
  credentialId: string;
  keyType: string;
  keyAlgorithm: string;
  keyCurve: string;
  keyValue: string;
  rpId: string;
  rpName?: string | null;
  userHandle?: string | null;
  userName?: string | null;
  userDisplayName?: string | null;
  counter: string;
  discoverable: string;
  creationDate: string;
}

export interface CipherLogin {
  uris?: CipherUri[] | null;
  username?: string | null;
  password?: string | null;
  passwordRevisionDate?: string | null;
  totp?: string | null;
  fido2Credentials?: Fido2Credential[] | null;
}

export interface CipherSecureNote {
  type: number;
}

export interface CipherCard {
  cardholderName?: string | null;
  brand?: string | null;
  number?: string | null;
  expMonth?: string | null;
  expYear?: string | null;
  code?: string | null;
}

export interface CipherIdentity {
  title?: string | null;
  firstName?: string | null;
  middleName?: string | null;
  lastName?: string | null;
  address1?: string | null;
  address2?: string | null;
  address3?: string | null;
  city?: string | null;
  state?: string | null;
  postalCode?: string | null;
  country?: string | null;
  company?: string | null;
  email?: string | null;
  phone?: string | null;
  ssn?: string | null;
  username?: string | null;
  passportNumber?: string | null;
  licenseNumber?: string | null;
}

export interface CipherField {
  name?: string | null;
  value?: string | null;
  type: FieldType;
  linkedId?: number | null;
}

export interface CipherPayload {
  id?: string;
  folderId?: string | null;
  organizationId?: string | null;
  type: CipherType;
  name: string;
  notes?: string | null;
  favorite?: boolean;
  reprompt?: number;
  login?: CipherLogin | null;
  secureNote?: CipherSecureNote | null;
  card?: CipherCard | null;
  identity?: CipherIdentity | null;
  fields?: CipherField[] | null;
  revisionDate?: string;
  creationDate?: string;
  deletedDate?: string | null;
}

export interface FolderPayload {
  id?: string;
  name: string;
  revisionDate?: string;
}
