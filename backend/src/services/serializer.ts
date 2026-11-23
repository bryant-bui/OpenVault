import type { CipherInput } from "../schemas/bitwarden.js";

export interface UserRow {
  id: string;
  email: string;
  name: string | null;
  master_password_hash: string;
  password_salt: string;
  server_kdf_iterations: number;
  master_password_hint: string | null;
  symmetric_key: string;
  public_key: string | null;
  private_key: string | null;
  security_stamp: string;
  kdf_type: number;
  kdf_iterations: number;
  kdf_memory: number | null;
  kdf_parallelism: number | null;
  revision_date: string;
  created_at: string;
  updated_at: string;
}

export interface FolderRow {
  id: string;
  user_id: string;
  name: string;
  revision_date: string;
  created_at: string;
}

export interface CipherRow {
  id: string;
  user_id: string;
  folder_id: string | null;
  type: number;
  data_json: string;
  favorite: number;
  reprompt: number;
  deleted_at: string | null;
  revision_date: string;
  created_at: string;
}

export function serializeProfile(user: UserRow) {
  return {
    object: "profile",
    id: user.id,
    name: user.name,
    email: user.email,
    emailVerified: true,
    premium: true,
    premiumFromOrganization: false,
    masterPasswordHint: user.master_password_hint,
    culture: "en-US",
    twoFactorEnabled: false,
    key: user.symmetric_key,
    privateKey: user.private_key,
    securityStamp: user.security_stamp,
    forcePasswordReset: false,
    usesKeyConnector: false,
    avatarColor: "#0572EC",
    creationDate: user.created_at,
    organizations: [],
    providers: [],
    providerOrganizations: [],
  };
}

export function serializeFolder(row: FolderRow) {
  return {
    object: "folder",
    id: row.id,
    name: row.name,
    revisionDate: row.revision_date,
  };
}

export function serializeCipher(row: CipherRow) {
  const data = JSON.parse(row.data_json) as Omit<
    CipherInput,
    "type" | "folderId" | "organizationId" | "favorite" | "reprompt"
  >;

  return {
    object: "cipherDetails",
    id: row.id,
    organizationId: null,
    folderId: row.folder_id,
    type: row.type,
    name: data.name,
    notes: data.notes ?? null,
    favorite: row.favorite === 1,
    reprompt: row.reprompt,
    login: data.login ?? null,
    secureNote: data.secureNote ?? null,
    card: data.card ?? null,
    identity: data.identity ?? null,
    fields: data.fields ?? null,
    passwordHistory: data.passwordHistory ?? null,
    attachments: null,
    collectionIds: [],
    organizationUseTotp: true,
    edit: true,
    viewPassword: true,
    permissions: {
      delete: true,
      restore: true,
    },
    creationDate: row.created_at,
    revisionDate: row.revision_date,
    deletedDate: row.deleted_at,
  };
}

export function serializeServerConfig(origin: string) {
  return {
    object: "config",
    version: "2026.11.0-openvault",
    gitHash: "openvault",
    server: {
      name: "OpenVault",
      url: origin,
    },
    environment: {
      vault: origin,
      api: `${origin}/api`,
      identity: `${origin}/identity`,
      notifications: null,
      sso: null,
    },
    featureStates: {
      "fido2-vault-credentials": true,
    },
  };
}
