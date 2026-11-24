import { z } from "zod";
import { cipherStringSchema } from "./bitwarden.js";

export const safeCategorySchema = z.enum([
  "id_card",
  "passport",
  "driver_license",
  "medical",
  "financial",
  "photo",
  "other",
]);

const saltB64Schema = z
  .string()
  .min(16)
  .max(64)
  .regex(/^[A-Za-z0-9+/=]+$/, "Invalid Base64 salt");

export const safeSetupSchema = z.object({
  kdfType: z.union([z.literal(0), z.literal(1)]).default(1),
  kdfIterations: z.number().int().min(1).max(2_000_000),
  kdfMemory: z.number().int().min(15).max(1024).nullable().optional(),
  kdfParallelism: z.number().int().min(1).max(16).nullable().optional(),
  passcodeSalt: saltB64Schema,
  passcodeEncryptedSafeKey: cipherStringSchema,
  recoverySalt: saltB64Schema,
  recoveryKdfIterations: z.number().int().min(100_000).max(1_000_000).default(100_000),
  recoveryEncryptedSafeKey: cipherStringSchema,
  autoLockMinutes: z.number().int().min(1).max(15).optional().default(3),
});

export const safePasscodeUpdateSchema = z.object({
  mode: z.enum(["change", "recovery"]).default("change"),
  kdfType: z.union([z.literal(0), z.literal(1)]).default(1),
  kdfIterations: z.number().int().min(1).max(2_000_000),
  kdfMemory: z.number().int().min(15).max(1024).nullable().optional(),
  kdfParallelism: z.number().int().min(1).max(16).nullable().optional(),
  passcodeSalt: saltB64Schema,
  passcodeEncryptedSafeKey: cipherStringSchema,
  recoverySalt: saltB64Schema.optional(),
  recoveryKdfIterations: z.number().int().min(100_000).max(1_000_000).optional(),
  recoveryEncryptedSafeKey: cipherStringSchema.optional(),
});

export const safeUnlockAttemptSchema = z.object({
  success: z.boolean(),
});

export const safeSettingsPatchSchema = z.object({
  autoLockMinutes: z.number().int().min(1).max(15),
});

export const safeItemCreateSchema = z.object({
  category: safeCategorySchema.default("other"),
  encryptedMetadata: cipherStringSchema,
  favorite: z.boolean().optional().default(false),
});

export const safeItemUpdateSchema = z.object({
  category: safeCategorySchema.optional(),
  encryptedMetadata: cipherStringSchema.optional(),
  favorite: z.boolean().optional(),
});

export const safeResetSchema = z.object({
  masterPasswordHash: z.string().min(16).max(512),
});
