import {
  CipherType,
  FieldType,
  type CipherField,
  type CipherPayload,
  type Fido2Credential,
  type SymmetricKey,
} from "./types.js";
import {
  prepareBitwardenImport,
  type BitwardenExportJson,
  type EncryptedImportPayload,
} from "./vault.js";

export type ImportSourceFormat =
  | "bitwarden-json"
  | "proton-pass-json"
  | "proton-pass-csv";

export interface ParsedImportBundle {
  format: ImportSourceFormat;
  exportData: BitwardenExportJson;
  skippedCount: number;
}

// --- Zero-Dependency ZIP Extractor for Proton Pass unencrypted ZIP ("Mã bưu chính") ---

function readUint16LE(view: DataView, offset: number): number {
  return view.getUint16(offset, true);
}

function readUint32LE(view: DataView, offset: number): number {
  return view.getUint32(offset, true);
}

async function inflateRawBytes(compressed: Uint8Array): Promise<Uint8Array> {
  if (typeof DecompressionStream === "undefined") {
    throw new Error("Trình duyệt không hỗ trợ DecompressionStream; vui lòng giải nén file ZIP và chọn file data.json bên trong.");
  }
  const copy = new Uint8Array(new ArrayBuffer(compressed.byteLength));
  copy.set(compressed);
  const stream = new Blob([copy]).stream().pipeThrough(new DecompressionStream("deflate-raw"));
  const ab = await new Response(stream).arrayBuffer();
  return new Uint8Array(ab);
}

/**
 * Extracts `data.json` (or the first `.json` / `.csv` entry) from an unencrypted ZIP archive
 * in browser memory using the ZIP Central Directory + Web DecompressionStream.
 */
export async function extractVaultTextFromZip(
  zipBytes: Uint8Array,
): Promise<{ filename: string; text: string }> {
  const view = new DataView(zipBytes.buffer, zipBytes.byteOffset, zipBytes.byteLength);

  // Locate End of Central Directory (EOCD) signature 0x06054b50 from end of file
  let eocdOffset = -1;
  const minOffset = Math.max(0, zipBytes.byteLength - 65_557);
  for (let i = zipBytes.byteLength - 22; i >= minOffset; i--) {
    if (readUint32LE(view, i) === 0x06054b50) {
      eocdOffset = i;
      break;
    }
  }
  if (eocdOffset === -1) {
    throw new Error("Tệp ZIP không hợp lệ (không tìm thấy cấu trúc ZIP).");
  }

  const totalEntries = readUint16LE(view, eocdOffset + 10);
  const centralDirOffset = readUint32LE(view, eocdOffset + 16);
  const decoder = new TextDecoder("utf-8");

  interface ZipEntryMeta {
    name: string;
    method: number;
    compressedSize: number;
    localHeaderOffset: number;
    isEncrypted: boolean;
  }

  const entries: ZipEntryMeta[] = [];
  let cursor = centralDirOffset;

  for (let i = 0; i < totalEntries; i++) {
    if (cursor + 46 > zipBytes.byteLength || readUint32LE(view, cursor) !== 0x02014b50) {
      break;
    }
    const flags = readUint16LE(view, cursor + 8);
    const method = readUint16LE(view, cursor + 10);
    const compressedSize = readUint32LE(view, cursor + 20);
    const nameLen = readUint16LE(view, cursor + 28);
    const extraLen = readUint16LE(view, cursor + 30);
    const commentLen = readUint16LE(view, cursor + 32);
    const localHeaderOffset = readUint32LE(view, cursor + 42);
    const name = decoder.decode(zipBytes.subarray(cursor + 46, cursor + 46 + nameLen));

    entries.push({
      name,
      method,
      compressedSize,
      localHeaderOffset,
      isEncrypted: (flags & 0x01) !== 0,
    });
    cursor += 46 + nameLen + extraLen + commentLen;
  }

  const target =
    entries.find((e) => /(^|\/)data\.json$/i.test(e.name)) ??
    entries.find((e) => /\.json$/i.test(e.name) && !e.name.startsWith("__MACOSX")) ??
    entries.find((e) => /\.csv$/i.test(e.name) && !e.name.startsWith("__MACOSX"));

  if (!target) {
    throw new Error("Không tìm thấy file data.json hoặc .csv bên trong tệp ZIP.");
  }
  if (target.isEncrypted) {
    throw new Error(
      "Tệp ZIP đang được mã hóa mật khẩu/PGP. Hãy xuất từ Proton Pass ở chế độ 'Mã bưu chính' (ZIP không mã hóa) hoặc CSV.",
    );
  }

  const lh = target.localHeaderOffset;
  if (lh + 30 > zipBytes.byteLength || readUint32LE(view, lh) !== 0x04034b50) {
    throw new Error("Local file header trong tệp ZIP bị lỗi.");
  }
  const lhNameLen = readUint16LE(view, lh + 26);
  const lhExtraLen = readUint16LE(view, lh + 28);
  const dataStart = lh + 30 + lhNameLen + lhExtraLen;
  const compressedSlice = zipBytes.subarray(dataStart, dataStart + target.compressedSize);

  let rawBytes: Uint8Array;
  if (target.method === 0) {
    rawBytes = compressedSlice;
  } else if (target.method === 8) {
    rawBytes = await inflateRawBytes(compressedSlice);
  } else {
    throw new Error(`Thuật toán nén ZIP không được hỗ trợ (method ${target.method}).`);
  }

  return {
    filename: target.name,
    text: decoder.decode(rawBytes),
  };
}

// --- Proton Pass JSON (`data.json`) Parser ---

interface ProtonExtraField {
  fieldName?: string;
  type?: string;
  data?: {
    content?: string;
    totpUri?: string;
  };
}

interface ProtonPasskeyRaw {
  key?: string;
  credentialId?: string;
  domain?: string;
  rpId?: string;
  rpName?: string;
  userHandle?: string;
  userName?: string;
  userDisplayName?: string;
  createTime?: number;
  content?: string;
}

interface ProtonItemRaw {
  itemId?: string;
  state?: number; // 1 = active, 2 = trashed
  pinned?: boolean;
  aliasEmail?: string | null;
  data?: {
    metadata?: {
      name?: string;
      note?: string;
      type?: string;
    };
    type?: string;
    extraFields?: ProtonExtraField[];
    content?: {
      itemEmail?: string;
      itemUsername?: string;
      username?: string;
      password?: string;
      urls?: string[];
      autofillUrls?: { url?: string }[];
      totpUri?: string;
      passkeys?: ProtonPasskeyRaw[];
      // creditCard
      cardholderName?: string;
      number?: string;
      verificationNumber?: string;
      expirationDate?: string;
      pin?: string;
      // wifi / sshKey / other
      ssid?: string;
      security?: string;
      privateKey?: string;
      publicKey?: string;
      [key: string]: unknown;
    };
  };
}

interface ProtonExportJson {
  version?: string;
  vaults: Record<
    string,
    {
      name?: string;
      items?: ProtonItemRaw[];
    }
  >;
}

function parseExpirationDate(exp?: string): { expMonth: string | null; expYear: string | null } {
  if (!exp) return { expMonth: null, expYear: null };
  const clean = exp.trim();
  // YYYY-MM or YYYY/MM
  const isoMatch = /^(\d{4})[-/](\d{1,2})$/.exec(clean);
  if (isoMatch) {
    return { expYear: isoMatch[1]!, expMonth: isoMatch[2]!.padStart(2, "0") };
  }
  // MM/YY or MM/YYYY
  const slashMatch = /^(\d{1,2})[-/](\d{2,4})$/.exec(clean);
  if (slashMatch) {
    const rawYear = slashMatch[2]!;
    return {
      expMonth: slashMatch[1]!.padStart(2, "0"),
      expYear: rawYear.length === 2 ? `20${rawYear}` : rawYear,
    };
  }
  return { expMonth: null, expYear: null };
}

function mapProtonPasskey(pk: ProtonPasskeyRaw): Fido2Credential | null {
  const keyValue = pk.content ?? pk.key;
  const rpId = pk.rpId ?? pk.domain;
  if (!keyValue || !rpId || !pk.credentialId) return null;

  return {
    credentialId: pk.credentialId,
    keyType: "public-key",
    keyAlgorithm: "ECDSA",
    keyCurve: "P-256",
    keyValue,
    rpId,
    rpName: pk.rpName ?? rpId,
    userHandle: pk.userHandle ?? null,
    userName: pk.userName ?? null,
    userDisplayName: pk.userDisplayName ?? pk.userName ?? null,
    counter: "0",
    discoverable: "true",
    creationDate: pk.createTime
      ? new Date(pk.createTime * 1000).toISOString()
      : new Date().toISOString(),
  };
}

function parseProtonPassJson(doc: ProtonExportJson): ParsedImportBundle {
  const folders: { id: string; name: string }[] = [];
  const items: CipherPayload[] = [];
  let skippedCount = 0;

  const vaultEntries: [string, { name?: string; items?: ProtonItemRaw[] }][] = Array.isArray(doc.vaults)
    ? (doc.vaults as { name?: string; items?: ProtonItemRaw[] }[]).map((v, i) => [`vault-${i}`, v])
    : Object.entries(doc.vaults);

  for (const [vaultId, vault] of vaultEntries) {
    const folderName = vault?.name?.trim() || "Proton Pass";
    folders.push({ id: vaultId, name: folderName });

    for (const rawItem of vault?.items ?? []) {
      // Skip trashed items (state === 2)
      if (rawItem.state === 2) {
        skippedCount++;
        continue;
      }

      const data = rawItem.data;
      if (!data) {
        skippedCount++;
        continue;
      }

      const title = data.metadata?.name?.trim() || "Untitled";
      const rawNote = data.metadata?.note?.trim() || null;
      const noteText = rawNote && rawNote.length > 40_000 ? rawNote.slice(0, 40_000) : rawNote;
      const content = data.content ?? {};
      const itemType = (data.type ?? data.metadata?.type ?? "login").toLowerCase();

      const customFields: CipherField[] = [];
      let extraTotp: string | null = null;

      for (const ef of data.extraFields ?? []) {
        const efName = ef.fieldName?.trim() || "Field";
        if (ef.type === "totp" && ef.data?.totpUri) {
          if (!extraTotp) {
            extraTotp = ef.data.totpUri.trim();
          } else {
            customFields.push({
              name: efName,
              value: ef.data.totpUri.trim(),
              type: FieldType.Hidden,
            });
          }
        } else if (ef.data?.content) {
          customFields.push({
            name: efName,
            value: ef.data.content,
            type: ef.type === "hidden" ? FieldType.Hidden : FieldType.Text,
          });
        }
      }

      if (itemType === "login") {
        const itemEmail = content.itemEmail?.trim() || "";
        const itemUsername = content.itemUsername?.trim() || content.username?.trim() || "";
        const primaryUser = itemUsername || itemEmail || null;

        if (itemEmail && itemUsername && itemEmail.toLowerCase() !== itemUsername.toLowerCase()) {
          customFields.unshift({
            name: "Email",
            value: itemEmail,
            type: FieldType.Text,
          });
        }

        const urlSet = new Set<string>();
        for (const u of content.urls ?? []) {
          if (u?.trim()) urlSet.add(u.trim());
        }
        for (const au of content.autofillUrls ?? []) {
          if (au?.url?.trim()) urlSet.add(au.url.trim());
        }

        const fido2Credentials = (content.passkeys ?? [])
          .map(mapProtonPasskey)
          .filter((x): x is Fido2Credential => x !== null);

        items.push({
          folderId: vaultId,
          type: CipherType.Login,
          name: title,
          notes: noteText,
          favorite: Boolean(rawItem.pinned),
          login: {
            username: primaryUser,
            password: content.password ?? null,
            totp: content.totpUri?.trim() || extraTotp || null,
            uris: Array.from(urlSet).slice(0, 50).map((uri) => ({ uri, match: null })),
            fido2Credentials: fido2Credentials.length > 0 ? fido2Credentials.slice(0, 25) : null,
          },
          fields: customFields.length > 0 ? customFields.slice(0, 100) : null,
        });
      } else if (itemType === "creditcard") {
        const { expMonth, expYear } = parseExpirationDate(content.expirationDate);
        if (content.pin) {
          customFields.push({ name: "PIN", value: content.pin, type: FieldType.Hidden });
        }
        items.push({
          folderId: vaultId,
          type: CipherType.Card,
          name: title,
          notes: noteText,
          favorite: Boolean(rawItem.pinned),
          card: {
            cardholderName: content.cardholderName?.trim() || null,
            brand: null,
            number: content.number?.trim() || null,
            expMonth,
            expYear,
            code: content.verificationNumber?.trim() || null,
          },
          fields: customFields.length > 0 ? customFields : null,
        });
      } else if (itemType === "alias") {
        items.push({
          folderId: vaultId,
          type: CipherType.Login,
          name: title,
          notes: noteText,
          favorite: Boolean(rawItem.pinned),
          login: {
            username: rawItem.aliasEmail?.trim() || null,
            password: null,
            totp: extraTotp,
            uris: [],
          },
          fields: customFields.length > 0 ? customFields : null,
        });
      } else if (itemType === "note") {
        items.push({
          folderId: vaultId,
          type: CipherType.SecureNote,
          name: title,
          notes: noteText,
          favorite: Boolean(rawItem.pinned),
          secureNote: { type: 0 },
          fields: customFields.length > 0 ? customFields : null,
        });
      } else {
        // Wi-Fi, SSH Key, Identity, etc. -> preserve all content cleanly as a Secure Note
        const lines: string[] = [];
        if (noteText) lines.push(noteText);
        if (content.ssid) lines.push(`SSID: ${content.ssid}`);
        if (content.password) lines.push(`Password: ${content.password}`);
        if (content.security) lines.push(`Security: ${content.security}`);
        if (content.publicKey) lines.push(`Public Key:\n${content.publicKey}`);
        if (content.privateKey) lines.push(`Private Key:\n${content.privateKey}`);

        items.push({
          folderId: vaultId,
          type: CipherType.SecureNote,
          name: title,
          notes: lines.join("\n\n") || null,
          favorite: Boolean(rawItem.pinned),
          secureNote: { type: 0 },
          fields: customFields.length > 0 ? customFields : null,
        });
      }
    }
  }

  return {
    format: "proton-pass-json",
    exportData: {
      encrypted: false,
      folders,
      items,
    },
    skippedCount,
  };
}

// --- RFC 4180 CSV Parser for Proton Pass CSV Exports ---

export function parseCsvRows(csvText: string): string[][] {
  const input = csvText.replace(/^\uFEFF/, ""); // Strip UTF-8 BOM
  const rows: string[][] = [];
  let row: string[] = [];
  let field = "";
  let inQuotes = false;

  for (let i = 0; i < input.length; i++) {
    const ch = input[i]!;
    if (inQuotes) {
      if (ch === '"') {
        if (input[i + 1] === '"') {
          field += '"';
          i++;
        } else {
          inQuotes = false;
        }
      } else {
        field += ch;
      }
    } else if (ch === '"') {
      inQuotes = true;
    } else if (ch === ",") {
      row.push(field);
      field = "";
    } else if (ch === "\r") {
      if (input[i + 1] === "\n") i++;
      row.push(field);
      rows.push(row);
      row = [];
      field = "";
    } else if (ch === "\n") {
      row.push(field);
      rows.push(row);
      row = [];
      field = "";
    } else {
      field += ch;
    }
  }

  if (field.length > 0 || row.length > 0) {
    row.push(field);
    rows.push(row);
  }

  return rows.filter((r) => r.some((cell) => cell.trim().length > 0));
}

function parseProtonPassCsv(csvText: string): ParsedImportBundle {
  const rows = parseCsvRows(csvText);
  if (rows.length < 2) {
    throw new Error("Tệp CSV không có dữ liệu.");
  }

  const headers = rows[0]!.map((h) => h.trim().toLowerCase());
  const idx = (candidates: string[]): number => {
    for (const c of candidates) {
      const pos = headers.indexOf(c);
      if (pos !== -1) return pos;
    }
    return -1;
  };

  const iType = idx(["type"]);
  const iName = idx(["name", "title"]);
  const iUrl = idx(["url", "urls", "website", "login_uri"]);
  const iEmail = idx(["email"]);
  const iUser = idx(["username", "login_username"]);
  const iPass = idx(["password", "login_password"]);
  const iNote = idx(["note", "notes"]);
  const iTotp = idx(["totp", "login_totp", "otp"]);
  const iVault = idx(["vault", "folder"]);

  if (iName === -1 && iUrl === -1 && iPass === -1) {
    throw new Error("Không nhận diện được cột dữ liệu CSV (cần các cột name, url, username, password).");
  }

  const folderIdByName = new Map<string, string>();
  const folders: { id: string; name: string }[] = [];
  const items: CipherPayload[] = [];
  let skippedCount = 0;

  for (let r = 1; r < rows.length; r++) {
    const cols = rows[r]!;
    const cell = (index: number): string => (index >= 0 ? (cols[index] ?? "").trim() : "");

    const typeStr = cell(iType).toLowerCase();
    const name = cell(iName) || cell(iUrl) || "Untitled";
    const urlRaw = cell(iUrl);
    const email = cell(iEmail);
    const username = cell(iUser);
    const password = cell(iPass);
    const note = cell(iNote) || null;
    const totp = cell(iTotp) || null;
    const vaultName = cell(iVault);

    let folderId: string | null = null;
    if (vaultName) {
      if (!folderIdByName.has(vaultName)) {
        const fid = `csv-vault-${folders.length}`;
        folderIdByName.set(vaultName, fid);
        folders.push({ id: fid, name: vaultName });
      }
      folderId = folderIdByName.get(vaultName)!;
    }

    if (typeStr === "note") {
      items.push({
        folderId,
        type: CipherType.SecureNote,
        name,
        notes: note,
        secureNote: { type: 0 },
      });
      continue;
    }

    if (!name && !password && !username && !email && !note) {
      skippedCount++;
      continue;
    }

    const primaryUser = username || email || null;
    const fields: CipherField[] = [];
    if (email && username && email.toLowerCase() !== username.toLowerCase()) {
      fields.push({ name: "Email", value: email, type: FieldType.Text });
    }

    const uris = urlRaw
      .split(/[\s,;]+/)
      .map((u) => u.trim())
      .filter(Boolean)
      .map((uri) => ({ uri, match: null }));

    items.push({
      folderId,
      type: CipherType.Login,
      name,
      notes: note,
      login: {
        username: primaryUser,
        password: password || null,
        totp,
        uris,
      },
      fields: fields.length > 0 ? fields : null,
    });
  }

  return {
    format: "proton-pass-csv",
    exportData: {
      encrypted: false,
      folders,
      items,
    },
    skippedCount,
  };
}

/**
 * Automatically detects and parses Bitwarden JSON, Proton Pass JSON (`data.json`),
 * or Proton Pass / standard CSV into a normalized Bitwarden export structure.
 */
export function parseUniversalVaultImport(rawText: string): ParsedImportBundle {
  const trimmed = rawText.replace(/^\uFEFF/, "").trim();
  if (!trimmed) {
    throw new Error("Tệp nhập liệu đang trống.");
  }

  if (trimmed.startsWith("{")) {
    let parsed: Record<string, unknown>;
    try {
      parsed = JSON.parse(trimmed) as Record<string, unknown>;
    } catch {
      throw new Error("Tệp JSON bị lỗi cú pháp.");
    }

    // Detect Proton Pass `data.json`: has `vaults` object map or array
    if (parsed["vaults"] && typeof parsed["vaults"] === "object") {
      return parseProtonPassJson(parsed as unknown as ProtonExportJson);
    }

    // Detect Bitwarden JSON export: has `items` array
    if (Array.isArray(parsed["items"])) {
      const bw = parsed as unknown as BitwardenExportJson;
      if (bw.encrypted) {
        throw new Error("Tệp Bitwarden JSON đang mã hóa. Hãy chọn bản xuất JSON không mã hóa (.json).");
      }
      return {
        format: "bitwarden-json",
        exportData: bw,
        skippedCount: 0,
      };
    }

    throw new Error(
      "Định dạng JSON không được hỗ trợ. Hỗ trợ file JSON xuất từ Bitwarden hoặc file data.json từ Proton Pass.",
    );
  }

  // Otherwise parse as CSV (Proton Pass CSV / Chrome / standard CSV)
  return parseProtonPassCsv(trimmed);
}

export interface PreparedUniversalImport {
  format: ImportSourceFormat;
  skippedCount: number;
  payload: EncryptedImportPayload;
}

/**
 * Auto-detects format (Bitwarden JSON, Proton Pass JSON, or CSV) and encrypts every item
 * Zero-Knowledge using the user's SymmetricKey ready for `POST /api/ciphers/import`.
 */
export async function prepareUniversalVaultImport(
  rawText: string,
  key: SymmetricKey,
): Promise<PreparedUniversalImport> {
  const bundle = parseUniversalVaultImport(rawText);
  const payload = await prepareBitwardenImport(bundle.exportData, key);
  return {
    format: bundle.format,
    skippedCount: bundle.skippedCount,
    payload,
  };
}
