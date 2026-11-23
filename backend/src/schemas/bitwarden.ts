import { z } from "zod";

/** Strict Bitwarden Encryption Type 2 format: 2.<b64_iv>|<b64_ct>|<b64_mac> */
export const cipherStringSchema = z
  .string()
  .min(10)
  .max(65_536)
  .regex(/^2\.[A-Za-z0-9+/=]+\|[A-Za-z0-9+/=]+\|[A-Za-z0-9+/=]+$/, {
    message: "Expected Bitwarden Encryption Type 2 CipherString",
  });

export const nullableCipherStringSchema = cipherStringSchema.nullable().optional();

export const preloginSchema = z.object({
  email: z.string().trim().email().max(254),
});

export const registerSchema = z.object({
  email: z.string().trim().email().max(254),
  name: z.string().trim().max(120).nullable().optional(),
  masterPasswordHash: z.string().min(20).max(512),
  masterPasswordHint: z.string().max(120).nullable().optional(),
  key: cipherStringSchema.optional(),
  userSymmetricKey: cipherStringSchema.optional(),
  kdf: z.union([z.literal(0), z.literal(1)]).default(1),
  kdfIterations: z.number().int().min(1).max(2_000_000),
  kdfMemory: z.number().int().min(15).max(1024).nullable().optional(),
  kdfParallelism: z.number().int().min(1).max(16).nullable().optional(),
  keys: z
    .object({
      publicKey: z.string().min(100).max(8192),
      encryptedPrivateKey: cipherStringSchema,
    })
    .nullable()
    .optional(),
  userAsymmetricKeys: z
    .object({
      publicKey: z.string().min(100).max(8192),
      encryptedPrivateKey: cipherStringSchema,
    })
    .nullable()
    .optional(),
});

const cipherUriSchema = z.object({
  uri: nullableCipherStringSchema,
  match: z.number().int().min(0).max(5).nullable().optional(),
});

const fido2CredentialSchema = z.object({
  credentialId: cipherStringSchema,
  keyType: cipherStringSchema,
  keyAlgorithm: cipherStringSchema,
  keyCurve: cipherStringSchema,
  keyValue: cipherStringSchema,
  rpId: cipherStringSchema,
  rpName: nullableCipherStringSchema,
  userHandle: nullableCipherStringSchema,
  userName: nullableCipherStringSchema,
  userDisplayName: nullableCipherStringSchema,
  counter: cipherStringSchema,
  discoverable: cipherStringSchema,
  creationDate: z.string().max(64),
});

const cipherLoginSchema = z.object({
  uris: z.array(cipherUriSchema).max(50).nullable().optional(),
  username: nullableCipherStringSchema,
  password: nullableCipherStringSchema,
  passwordRevisionDate: z.string().max(64).nullable().optional(),
  totp: nullableCipherStringSchema,
  fido2Credentials: z.array(fido2CredentialSchema).max(25).nullable().optional(),
});

const cipherSecureNoteSchema = z.object({
  type: z.number().int().min(0).max(10).default(0),
});

const cipherCardSchema = z.object({
  cardholderName: nullableCipherStringSchema,
  brand: nullableCipherStringSchema,
  number: nullableCipherStringSchema,
  expMonth: nullableCipherStringSchema,
  expYear: nullableCipherStringSchema,
  code: nullableCipherStringSchema,
});

const cipherIdentitySchema = z.object({
  title: nullableCipherStringSchema,
  firstName: nullableCipherStringSchema,
  middleName: nullableCipherStringSchema,
  lastName: nullableCipherStringSchema,
  address1: nullableCipherStringSchema,
  address2: nullableCipherStringSchema,
  address3: nullableCipherStringSchema,
  city: nullableCipherStringSchema,
  state: nullableCipherStringSchema,
  postalCode: nullableCipherStringSchema,
  country: nullableCipherStringSchema,
  company: nullableCipherStringSchema,
  email: nullableCipherStringSchema,
  phone: nullableCipherStringSchema,
  ssn: nullableCipherStringSchema,
  username: nullableCipherStringSchema,
  passportNumber: nullableCipherStringSchema,
  licenseNumber: nullableCipherStringSchema,
});

const cipherFieldSchema = z.object({
  name: nullableCipherStringSchema,
  value: nullableCipherStringSchema,
  type: z.number().int().min(0).max(3),
  linkedId: z.number().int().nullable().optional(),
});

const passwordHistoryItemSchema = z.object({
  password: cipherStringSchema,
  lastUsedDate: z.string().max(64),
});

export const cipherInputSchema = z.object({
  type: z.union([z.literal(1), z.literal(2), z.literal(3), z.literal(4)]),
  folderId: z.string().uuid().nullable().optional(),
  organizationId: z.string().uuid().nullable().optional(),
  name: cipherStringSchema,
  notes: nullableCipherStringSchema,
  favorite: z.boolean().optional().default(false),
  reprompt: z.number().int().min(0).max(1).optional().default(0),
  login: cipherLoginSchema.nullable().optional(),
  secureNote: cipherSecureNoteSchema.nullable().optional(),
  card: cipherCardSchema.nullable().optional(),
  identity: cipherIdentitySchema.nullable().optional(),
  fields: z.array(cipherFieldSchema).max(100).nullable().optional(),
  passwordHistory: z.array(passwordHistoryItemSchema).max(20).nullable().optional(),
});

export const folderInputSchema = z.object({
  name: cipherStringSchema,
});

export const importVaultSchema = z.object({
  folders: z.array(folderInputSchema).max(500).default([]),
  ciphers: z.array(cipherInputSchema).max(5000).default([]),
  folderRelationships: z
    .array(
      z.object({
        key: z.number().int().min(0),
        value: z.number().int().min(0),
      }),
    )
    .max(5000)
    .default([]),
});

export const changePasswordSchema = z.object({
  masterPasswordHash: z.string().min(20).max(512),
  newMasterPasswordHash: z.string().min(20).max(512),
  key: cipherStringSchema,
  masterPasswordHint: z.string().max(120).nullable().optional(),
});

export type CipherInput = z.infer<typeof cipherInputSchema>;
