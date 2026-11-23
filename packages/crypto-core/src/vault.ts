import { decryptString, encryptString } from "./cipher-string.js";
import type {
  CipherCard,
  CipherField,
  CipherIdentity,
  CipherLogin,
  CipherPayload,
  Fido2Credential,
  FolderPayload,
  SymmetricKey,
} from "./types.js";

async function encOpt(value: string | null | undefined, key: SymmetricKey): Promise<string | null> {
  if (value === null || value === undefined || value === "") return null;
  return encryptString(value, key);
}

async function decOpt(value: string | null | undefined, key: SymmetricKey): Promise<string | null> {
  if (value === null || value === undefined || value === "") return null;
  return decryptString(value, key);
}

async function encryptFido2(cred: Fido2Credential, key: SymmetricKey): Promise<Fido2Credential> {
  const [
    credentialId,
    keyType,
    keyAlgorithm,
    keyCurve,
    keyValue,
    rpId,
    rpName,
    userHandle,
    userName,
    userDisplayName,
    counter,
    discoverable,
  ] = await Promise.all([
    encryptString(cred.credentialId, key),
    encryptString(cred.keyType, key),
    encryptString(cred.keyAlgorithm, key),
    encryptString(cred.keyCurve, key),
    encryptString(cred.keyValue, key),
    encryptString(cred.rpId, key),
    encOpt(cred.rpName, key),
    encOpt(cred.userHandle, key),
    encOpt(cred.userName, key),
    encOpt(cred.userDisplayName, key),
    encryptString(cred.counter, key),
    encryptString(cred.discoverable, key),
  ]);

  return {
    credentialId,
    keyType,
    keyAlgorithm,
    keyCurve,
    keyValue,
    rpId,
    rpName,
    userHandle,
    userName,
    userDisplayName,
    counter,
    discoverable,
    creationDate: cred.creationDate,
  };
}

async function decryptFido2(cred: Fido2Credential, key: SymmetricKey): Promise<Fido2Credential> {
  const [
    credentialId,
    keyType,
    keyAlgorithm,
    keyCurve,
    keyValue,
    rpId,
    rpName,
    userHandle,
    userName,
    userDisplayName,
    counter,
    discoverable,
  ] = await Promise.all([
    decryptString(cred.credentialId, key),
    decryptString(cred.keyType, key),
    decryptString(cred.keyAlgorithm, key),
    decryptString(cred.keyCurve, key),
    decryptString(cred.keyValue, key),
    decryptString(cred.rpId, key),
    decOpt(cred.rpName, key),
    decOpt(cred.userHandle, key),
    decOpt(cred.userName, key),
    decOpt(cred.userDisplayName, key),
    decryptString(cred.counter, key),
    decryptString(cred.discoverable, key),
  ]);

  return {
    credentialId,
    keyType,
    keyAlgorithm,
    keyCurve,
    keyValue,
    rpId,
    rpName,
    userHandle,
    userName,
    userDisplayName,
    counter,
    discoverable,
    creationDate: cred.creationDate,
  };
}

export async function encryptCipher(
  plain: CipherPayload,
  key: SymmetricKey,
): Promise<CipherPayload> {
  const name = await encryptString(plain.name, key);
  const notes = await encOpt(plain.notes, key);

  let login: CipherLogin | null = null;
  if (plain.login) {
    const uris = plain.login.uris
      ? await Promise.all(
          plain.login.uris.map(async (u) => ({
            uri: await encOpt(u.uri, key),
            match: u.match ?? null,
          })),
        )
      : null;

    const fido2Credentials = plain.login.fido2Credentials
      ? await Promise.all(plain.login.fido2Credentials.map((c) => encryptFido2(c, key)))
      : null;

    login = {
      uris,
      username: await encOpt(plain.login.username, key),
      password: await encOpt(plain.login.password, key),
      passwordRevisionDate: plain.login.passwordRevisionDate ?? null,
      totp: await encOpt(plain.login.totp, key),
      fido2Credentials,
    };
  }

  let card: CipherCard | null = null;
  if (plain.card) {
    card = {
      cardholderName: await encOpt(plain.card.cardholderName, key),
      brand: await encOpt(plain.card.brand, key),
      number: await encOpt(plain.card.number, key),
      expMonth: await encOpt(plain.card.expMonth, key),
      expYear: await encOpt(plain.card.expYear, key),
      code: await encOpt(plain.card.code, key),
    };
  }

  let identity: CipherIdentity | null = null;
  if (plain.identity) {
    identity = {
      title: await encOpt(plain.identity.title, key),
      firstName: await encOpt(plain.identity.firstName, key),
      middleName: await encOpt(plain.identity.middleName, key),
      lastName: await encOpt(plain.identity.lastName, key),
      address1: await encOpt(plain.identity.address1, key),
      address2: await encOpt(plain.identity.address2, key),
      address3: await encOpt(plain.identity.address3, key),
      city: await encOpt(plain.identity.city, key),
      state: await encOpt(plain.identity.state, key),
      postalCode: await encOpt(plain.identity.postalCode, key),
      country: await encOpt(plain.identity.country, key),
      company: await encOpt(plain.identity.company, key),
      email: await encOpt(plain.identity.email, key),
      phone: await encOpt(plain.identity.phone, key),
      ssn: await encOpt(plain.identity.ssn, key),
      username: await encOpt(plain.identity.username, key),
      passportNumber: await encOpt(plain.identity.passportNumber, key),
      licenseNumber: await encOpt(plain.identity.licenseNumber, key),
    };
  }

  const fields: CipherField[] | null = plain.fields
    ? await Promise.all(
        plain.fields.map(async (f) => ({
          name: await encOpt(f.name, key),
          value: await encOpt(f.value, key),
          type: f.type,
          linkedId: f.linkedId ?? null,
        })),
      )
    : null;

  return {
    ...plain,
    name,
    notes,
    login,
    secureNote: plain.secureNote ?? null,
    card,
    identity,
    fields,
  };
}

export async function decryptCipher(
  encrypted: CipherPayload,
  key: SymmetricKey,
): Promise<CipherPayload> {
  const name = await decryptString(encrypted.name, key);
  const notes = await decOpt(encrypted.notes, key);

  let login: CipherLogin | null = null;
  if (encrypted.login) {
    const uris = encrypted.login.uris
      ? await Promise.all(
          encrypted.login.uris.map(async (u) => ({
            uri: await decOpt(u.uri, key),
            match: u.match ?? null,
          })),
        )
      : null;

    const fido2Credentials = encrypted.login.fido2Credentials
      ? await Promise.all(encrypted.login.fido2Credentials.map((c) => decryptFido2(c, key)))
      : null;

    login = {
      uris,
      username: await decOpt(encrypted.login.username, key),
      password: await decOpt(encrypted.login.password, key),
      passwordRevisionDate: encrypted.login.passwordRevisionDate ?? null,
      totp: await decOpt(encrypted.login.totp, key),
      fido2Credentials,
    };
  }

  let card: CipherCard | null = null;
  if (encrypted.card) {
    card = {
      cardholderName: await decOpt(encrypted.card.cardholderName, key),
      brand: await decOpt(encrypted.card.brand, key),
      number: await decOpt(encrypted.card.number, key),
      expMonth: await decOpt(encrypted.card.expMonth, key),
      expYear: await decOpt(encrypted.card.expYear, key),
      code: await decOpt(encrypted.card.code, key),
    };
  }

  let identity: CipherIdentity | null = null;
  if (encrypted.identity) {
    identity = {
      title: await decOpt(encrypted.identity.title, key),
      firstName: await decOpt(encrypted.identity.firstName, key),
      middleName: await decOpt(encrypted.identity.middleName, key),
      lastName: await decOpt(encrypted.identity.lastName, key),
      address1: await decOpt(encrypted.identity.address1, key),
      address2: await decOpt(encrypted.identity.address2, key),
      address3: await decOpt(encrypted.identity.address3, key),
      city: await decOpt(encrypted.identity.city, key),
      state: await decOpt(encrypted.identity.state, key),
      postalCode: await decOpt(encrypted.identity.postalCode, key),
      country: await decOpt(encrypted.identity.country, key),
      company: await decOpt(encrypted.identity.company, key),
      email: await decOpt(encrypted.identity.email, key),
      phone: await decOpt(encrypted.identity.phone, key),
      ssn: await decOpt(encrypted.identity.ssn, key),
      username: await decOpt(encrypted.identity.username, key),
      passportNumber: await decOpt(encrypted.identity.passportNumber, key),
      licenseNumber: await decOpt(encrypted.identity.licenseNumber, key),
    };
  }

  const fields: CipherField[] | null = encrypted.fields
    ? await Promise.all(
        encrypted.fields.map(async (f) => ({
          name: await decOpt(f.name, key),
          value: await decOpt(f.value, key),
          type: f.type,
          linkedId: f.linkedId ?? null,
        })),
      )
    : null;

  return {
    ...encrypted,
    name,
    notes,
    login,
    secureNote: encrypted.secureNote ?? null,
    card,
    identity,
    fields,
  };
}

export async function encryptFolder(
  folder: FolderPayload,
  key: SymmetricKey,
): Promise<FolderPayload> {
  return {
    ...folder,
    name: await encryptString(folder.name, key),
  };
}

export async function decryptFolder(
  folder: FolderPayload,
  key: SymmetricKey,
): Promise<FolderPayload> {
  return {
    ...folder,
    name: await decryptString(folder.name, key),
  };
}

export interface BitwardenExportJson {
  encrypted?: boolean;
  folders?: { id: string; name: string }[];
  items?: CipherPayload[];
}

export interface EncryptedImportPayload {
  folders: { name: string }[];
  ciphers: CipherPayload[];
  folderRelationships: { key: number; value: number }[];
}

/**
 * Converts an unencrypted Bitwarden JSON export into an encrypted
 * POST /api/ciphers/import request payload ready to send to the backend.
 */
export async function prepareBitwardenImport(
  exportData: BitwardenExportJson,
  key: SymmetricKey,
): Promise<EncryptedImportPayload> {
  if (exportData.encrypted) {
    throw new Error("Encrypted Bitwarden JSON imports must be decrypted before re-encrypting for import");
  }

  const sourceFolders = exportData.folders ?? [];
  const sourceItems = exportData.items ?? [];

  const folderIndexById = new Map<string, number>();
  const encryptedFolders: { name: string }[] = [];

  for (let i = 0; i < sourceFolders.length; i++) {
    const folder = sourceFolders[i]!;
    folderIndexById.set(folder.id, i);
    encryptedFolders.push({ name: await encryptString(folder.name, key) });
  }

  const encryptedCiphers: CipherPayload[] = [];
  const folderRelationships: { key: number; value: number }[] = [];

  for (let i = 0; i < sourceItems.length; i++) {
    const item = sourceItems[i]!;
    const encrypted = await encryptCipher(
      {
        ...item,
        id: undefined,
        folderId: null,
        organizationId: null,
      },
      key,
    );
    encryptedCiphers.push(encrypted);

    if (item.folderId && folderIndexById.has(item.folderId)) {
      folderRelationships.push({
        key: i,
        value: folderIndexById.get(item.folderId)!,
      });
    }
  }

  return {
    folders: encryptedFolders,
    ciphers: encryptedCiphers,
    folderRelationships,
  };
}
