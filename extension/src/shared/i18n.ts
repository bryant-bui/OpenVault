export type AppLanguage = "en" | "vi";

export interface ExtStrings {
  isVi: boolean;
  // Common
  loadingApp: string;
  searchPlaceholder: string;
  clearSearch: string;
  newItem: string;
  addAccount: string;
  save: string;
  saving: string;
  cancel: string;
  delete: string;
  remove: string;
  edit: string;
  close: string;
  back: string;
  copy: string;
  done: string;
  // Filters
  allItems: string;
  favorites: string;
  logins: string;
  totpCodes: string;
  passkeys: string;
  creditCards: string;
  identities: string;
  secureNotes: string;
  categorySoftware: string;
  categoryNotes: string;
  categoryPassport: string;
  categoryIdCard: string;
  trash: string;
  folders: string;
  weakOrReused: string;
  suggestedFor: string;
  results: string;
  noMatchingItems: string;
  emptyVaultTitle: string;
  tryOtherKeyword: string;
  emptyVaultHint: string;
  welcomeTitle: string;
  welcomeSubtitle: string;
  importBtn: string;
  // Top bar & account menu
  syncNow: string;
  passwordGenerator: string;
  watchtower: string;
  watchtowerAlerts: (n: number) => string;
  settingsAndImport: string;
  lockVault: string;
  pendingSaveTitle: string;
  pendingUpdateTitle: string;
  dismiss: string;
  // Detail pane
  personalVault: string;
  fillNow: string;
  openAndFill: string;
  itemOptions: string;
  editItem: string;
  addFavorite: string;
  removeFavorite: string;
  fillCurrentTab: string;
  restoreFromTrash: string;
  deletePermanently: string;
  moveToTrash: string;
  inTrash: string;
  largeType: string;
  showPassword: string;
  hidePassword: string;
  openWebsite: string;
  lastEdited: string;
  added: string;
  customFields: string;
  deletePermTitle: string;
  moveTrashTitle: string;
  deletePermDesc: (name: string) => string;
  moveTrashDesc: (name: string) => string;
  strengthLabels: Record<number, string>;
  // Editor
  editItemTitle: string;
  newItemTitle: string;
  itemNameLabel: string;
  itemNamePlaceholder: string;
  folderLabel: string;
  noFolder: string;
  favoriteLabel: string;
  usernameLabel: string;
  passwordLabel: string;
  generateStrong: string;
  passwordPlaceholder: string;
  totpLabel: string;
  totpFieldShortLabel: string;
  websitesLabel: string;
  addWebsite: string;
  notesLabel: string;
  notesPlaceholder: string;
  addMore: string;
  changeIcon: string;
  resetIcon: string;
  removePasskeyTitle: string;
  removePasskeyDesc: string;
  fieldLabelPlaceholder: string;
  fieldValuePlaceholder: string;
  // Add More menu & Recovery Codes
  addRecoveryCodes: string;
  recoveryCodesTitle: string;
  pasteAll: string;
  clearAll: string;
  copyAll: string;
  showCodes: string;
  hideCodes: string;
  addSecQuestion: string;
  addText: string;
  addUrl: string;
  addEmail: string;
  addAddress: string;
  addDate: string;
  addOtp: string;
  addPasswordField: string;
  addPhone: string;
  // Settings Tabs & Sections
  settingsTitle: string;
  settingsTabGeneral: string;
  settingsTabAutofill: string;
  settingsTabSecurity: string;
  settingsTabData: string;
  // Watchtower
  watchtowerTitle: string;
  vaultHealthScore: string;
  watchtowerLocalNote: (total: number) => string;
  weakAndReusedCard: string;
  weakReusedSummary: (weak: number, reused: number) => string;
  twoFaAndPasskeyCard: string;
  twoFaPasskeySummary: (totp: number, passkeys: number) => string;
  // Unlock
  unlockHeading: string;
  enterPinPrompt: string;
  enterMasterPrompt: string;
  unlocking: string;
  useMasterPassword: string;
  usePin: (remaining: number) => string;
  signOut: string;
  signOutTitle: string;
  signOutDesc: string;
  pinRestartNotice: string;
  autoSyncActive: string;
  changeAvatar: string;
  removeAvatar: string;
  appearanceAndLang: string;
  themeSystem: string;
  themeLight: string;
  themeDark: string;
  languageLabel: string;
  autofillSection: string;
  autosuggestSection: string;
  passkeysSection: string;
  browserSection: string;
  securitySection: string;
  backupSection: string;
  aboutSection: string;
  lockNow: string;
}

const EN: ExtStrings = {
  isVi: false,
  loadingApp: "Starting OpenVault...",
  searchPlaceholder: "Search in OpenVault",
  clearSearch: "Clear search",
  newItem: "New Item",
  addAccount: "+ Add account",
  save: "Save",
  saving: "Saving...",
  cancel: "Cancel",
  delete: "Delete",
  remove: "Remove",
  edit: "Edit",
  close: "Close",
  back: "Back",
  copy: "Copy",
  done: "Done",
  allItems: "All Items",
  favorites: "Favorites",
  logins: "Logins",
  totpCodes: "2FA Codes (TOTP)",
  passkeys: "Passkeys",
  creditCards: "Credit Cards",
  identities: "Identities",
  secureNotes: "Notes",
  categorySoftware: "Software",
  categoryNotes: "Notes",
  categoryPassport: "Passport",
  categoryIdCard: "ID Card",
  trash: "Trash",
  folders: "Folders",
  weakOrReused: "Weak / Reused",
  suggestedFor: "Suggested",
  results: "Results",
  noMatchingItems: "No Matching Items",
  emptyVaultTitle: "No Items Yet",
  tryOtherKeyword: "Try a different keyword.",
  emptyVaultHint: "Click + New Item or open Settings to import your vault.",
  welcomeTitle: "Welcome to OpenVault",
  welcomeSubtitle: "Select an item on the left to inspect it, or add a new Zero-Knowledge encrypted item.",
  importBtn: "Import Proton / Bitwarden",
  syncNow: "Sync",
  passwordGenerator: "Password Generator",
  watchtower: "Watchtower",
  watchtowerAlerts: (n) => `Watchtower (${n} Alerts)`,
  settingsAndImport: "Settings",
  lockVault: "Lock",
  pendingSaveTitle: "Save New Login?",
  pendingUpdateTitle: "Update Saved Password?",
  dismiss: "Dismiss",
  personalVault: "Personal",
  fillNow: "Autofill",
  openAndFill: "Open & Fill",
  itemOptions: "Item Options",
  editItem: "Edit Item",
  addFavorite: "Add to Favorites",
  removeFavorite: "Remove from Favorites",
  fillCurrentTab: "Autofill Current Tab",
  restoreFromTrash: "Restore from Trash",
  deletePermanently: "Delete Permanently",
  moveToTrash: "Move to Trash",
  inTrash: "In Trash",
  largeType: "Large Type",
  showPassword: "Show Password",
  hidePassword: "Hide Password",
  openWebsite: "Open Website",
  lastEdited: "Last Edited",
  added: "Added",
  customFields: "Custom Fields",
  deletePermTitle: "Delete Permanently?",
  moveTrashTitle: "Move to Trash?",
  deletePermDesc: (name) => `"${name}" will be permanently deleted from Cloudflare D1 and cannot be recovered.`,
  moveTrashDesc: (name) => `"${name}" will be moved to Trash and can be restored later.`,
  strengthLabels: {
    0: "Weak",
    1: "Fair",
    2: "Good",
    3: "Strong",
    4: "Fantastic",
  },
  editItemTitle: "Edit Item",
  newItemTitle: "New Item",
  itemNameLabel: "Title *",
  itemNamePlaceholder: "GitHub, Gmail, Bank...",
  folderLabel: "Folder",
  noFolder: "No Folder",
  favoriteLabel: "Favorite",
  usernameLabel: "Username / Email",
  passwordLabel: "Password",
  generateStrong: "Generate Strong",
  passwordPlaceholder: "Enter or Generate Password",
  totpLabel: "One-Time Password (TOTP Secret or otpauth://)",
  totpFieldShortLabel: "One-Time Password",
  websitesLabel: "Websites (URL)",
  addWebsite: "Add URL",
  notesLabel: "Notes",
  notesPlaceholder: "Additional notes...",
  addMore: "Add More",
  changeIcon: "Custom Icon",
  resetIcon: "Reset Icon",
  removePasskeyTitle: "Remove Passkey?",
  removePasskeyDesc: "This passkey will be permanently removed when you click Save.",
  fieldLabelPlaceholder: "Field Label",
  fieldValuePlaceholder: "Enter Value",
  addRecoveryCodes: "Recovery Codes",
  recoveryCodesTitle: "Recovery Codes",
  pasteAll: "Paste",
  clearAll: "Clear",
  copyAll: "Copy All",
  showCodes: "Show Codes",
  hideCodes: "Hide Codes",
  addSecQuestion: "Security Question",
  addText: "Text",
  addUrl: "URL",
  addEmail: "Email",
  addAddress: "Address",
  addDate: "Date",
  addOtp: "One-Time Password",
  addPasswordField: "Password (Hidden)",
  addPhone: "Phone",
  settingsTitle: "Settings",
  settingsTabGeneral: "General",
  settingsTabAutofill: "Autofill",
  settingsTabSecurity: "Security",
  settingsTabData: "Data",
  watchtowerTitle: "Watchtower Security",
  vaultHealthScore: "Vault Health Score",
  watchtowerLocalNote: (total) =>
    `Analyzed 100% locally in your browser across ${total} logins — zero data leaves your device.`,
  weakAndReusedCard: "Weak / Reused",
  weakReusedSummary: (w, r) => `${w} weak passwords · ${r} reused passwords.`,
  twoFaAndPasskeyCard: "2FA & Passkeys",
  twoFaPasskeySummary: (t, p) => `${t} built-in TOTP · ${p} FIDO2 Passkeys.`,
  unlockHeading: "Unlock",
  enterPinPrompt: "Enter your PIN",
  enterMasterPrompt: "Enter Master Password",
  unlocking: "Unlocking OpenVault...",
  useMasterPassword: "Use Master Password",
  usePin: (rem) => `Use PIN (${rem})`,
  signOut: "Sign out",
  signOutTitle: "Sign out of OpenVault?",
  signOutDesc: "Local encrypted cache on this browser will be cleared. Your vault on Cloudflare D1 remains safe.",
  pinRestartNotice: "Browser restarted — enter Master Password once to re-activate quick PIN unlock.",
  autoSyncActive: "Auto-sync active",
  changeAvatar: "Photo",
  removeAvatar: "Remove",
  appearanceAndLang: "Appearance & Language",
  themeSystem: "System",
  themeLight: "Light",
  themeDark: "Dark",
  languageLabel: "Language",
  autofillSection: "Autofill",
  autosuggestSection: "Autosuggest",
  passkeysSection: "Passkeys (WebAuthn)",
  browserSection: "Browser Integration",
  securitySection: "Security & Unlock",
  backupSection: "Backup & Migration",
  aboutSection: "About OpenVault",
  lockNow: "Lock now",
};

const VI: ExtStrings = {
  isVi: true,
  loadingApp: "Đang khởi động OpenVault...",
  searchPlaceholder: "Tìm trong OpenVault",
  clearSearch: "Xóa tìm kiếm",
  newItem: "Mục mới",
  addAccount: "+ Thêm tài khoản",
  save: "Lưu",
  saving: "Đang lưu...",
  cancel: "Hủy",
  delete: "Xóa",
  remove: "Xóa",
  edit: "Sửa",
  close: "Đóng",
  back: "Quay lại",
  copy: "Sao chép",
  done: "Xong",
  allItems: "Tất cả mục",
  favorites: "Yêu thích",
  logins: "Đăng nhập",
  totpCodes: "Mã 2FA (TOTP)",
  passkeys: "Passkeys",
  creditCards: "Thẻ Tín Dụng",
  identities: "Danh Tính",
  secureNotes: "Ghi Chú",
  categorySoftware: "Phần Mềm",
  categoryNotes: "Ghi Chú",
  categoryPassport: "Passport",
  categoryIdCard: "ID Card",
  trash: "Thùng Rác",
  folders: "Thư Mục",
  weakOrReused: "Mật Khẩu Yếu / Trùng",
  suggestedFor: "Gợi Ý",
  results: "Kết Quả",
  noMatchingItems: "Không Tìm Thấy Mục Nào",
  emptyVaultTitle: "Chưa Có Mục Nào",
  tryOtherKeyword: "Thử từ khóa khác.",
  emptyVaultHint: "Nhấn + Mục Mới hoặc mở Cài Đặt để Import dữ liệu.",
  welcomeTitle: "Chào Mừng Đến OpenVault",
  welcomeSubtitle: "Chọn một mục bên trái để xem chi tiết, hoặc thêm tài khoản mới được mã hóa Zero-Knowledge.",
  importBtn: "Import Proton / Bitwarden",
  syncNow: "Đồng Bộ",
  passwordGenerator: "Trình Tạo Mật Khẩu",
  watchtower: "Watchtower",
  watchtowerAlerts: (n) => `Watchtower (${n} Cảnh Báo)`,
  settingsAndImport: "Cài Đặt",
  lockVault: "Khóa",
  pendingSaveTitle: "Lưu Đăng Nhập Mới?",
  pendingUpdateTitle: "Cập Nhật Mật Khẩu Đã Lưu?",
  dismiss: "Bỏ Qua",
  personalVault: "Cá Nhân",
  fillNow: "Điền Ngay",
  openAndFill: "Mở & Điền",
  itemOptions: "Tùy Chọn Mục",
  editItem: "Chỉnh Sửa Mục",
  addFavorite: "Thêm Vào Yêu Thích",
  removeFavorite: "Bỏ Yêu Thích",
  fillCurrentTab: "Điền Vào Tab Hiện Tại",
  restoreFromTrash: "Khôi Phục Từ Thùng Rác",
  deletePermanently: "Xóa Vĩnh Viễn",
  moveToTrash: "Chuyển Vào Thùng Rác",
  inTrash: "Trong Thùng Rác",
  largeType: "Phóng To Mật Khẩu",
  showPassword: "Hiện Mật Khẩu",
  hidePassword: "Ẩn Mật Khẩu",
  openWebsite: "Mở Trang Web",
  lastEdited: "Chỉnh Sửa Lần Cuối",
  added: "Đã Thêm",
  customFields: "Trường Tùy Chỉnh",
  deletePermTitle: "Xóa Vĩnh Viễn Mục Này?",
  moveTrashTitle: "Chuyển Vào Thùng Rác?",
  deletePermDesc: (name) => `"${name}" sẽ bị xóa hoàn toàn khỏi Cloudflare D1 và không thể khôi phục.`,
  moveTrashDesc: (name) => `"${name}" sẽ được chuyển vào Thùng rác. Bạn vẫn có thể khôi phục lại sau.`,
  strengthLabels: {
    0: "Yếu",
    1: "Trung Bình",
    2: "Khá Tốt",
    3: "Mạnh",
    4: "Tuyệt Vời",
  },
  editItemTitle: "Chỉnh Sửa Mục",
  newItemTitle: "Thêm Mục Mới",
  itemNameLabel: "Tên Mục *",
  itemNamePlaceholder: "GitHub, Gmail, Ngân Hàng...",
  folderLabel: "Thư Mục",
  noFolder: "Không Có Thư Mục",
  favoriteLabel: "Yêu Thích",
  usernameLabel: "Tên Đăng Nhập / Email",
  passwordLabel: "Mật Khẩu",
  generateStrong: "Tạo Mật Khẩu Mạnh",
  passwordPlaceholder: "Nhập Hoặc Tạo Mật Khẩu",
  totpLabel: "Mã Xác Thực 2FA (TOTP Secret Hoặc otpauth://)",
  totpFieldShortLabel: "Mã Xác Thực 2FA",
  websitesLabel: "Trang Web (URL)",
  addWebsite: "Thêm URL",
  notesLabel: "Ghi Chú",
  notesPlaceholder: "Ghi chú bảo mật...",
  addMore: "Thêm Trường",
  changeIcon: "Đổi Ảnh",
  resetIcon: "Mặc Định",
  removePasskeyTitle: "Xóa Khóa Truy Cập (Passkey)?",
  removePasskeyDesc: "Passkey này sẽ bị xóa vĩnh viễn khi bạn nhấn Lưu.",
  fieldLabelPlaceholder: "Tên Trường",
  fieldValuePlaceholder: "Nội Dung",
  addRecoveryCodes: "Mã Khôi Phục (20 Ô)",
  recoveryCodesTitle: "Mã Khôi Phục",
  pasteAll: "Dán Nhanh",
  clearAll: "Xóa Hết",
  copyAll: "Sao Chép Tất Cả",
  showCodes: "Hiện Mã",
  hideCodes: "Ẩn Mã",
  addSecQuestion: "Câu Hỏi Bảo Mật",
  addText: "Văn Bản (Text)",
  addUrl: "Đường Dẫn (URL)",
  addEmail: "Email",
  addAddress: "Địa Chỉ",
  addDate: "Ngày Tháng",
  addOtp: "Mã 2FA (OTP)",
  addPasswordField: "Mật Khẩu Ẩn",
  addPhone: "Số Điện Thoại",
  watchtowerTitle: "Watchtower Bảo Mật",
  vaultHealthScore: "Điểm An Toàn Kho",
  watchtowerLocalNote: (total) =>
    `Phân tích 100% cục bộ trên trình duyệt đối với ${total} tài khoản đăng nhập — không gửi dữ liệu lên mạng.`,
  weakAndReusedCard: "Mật Khẩu Yếu / Trùng",
  weakReusedSummary: (w, r) => `${w} Mật Khẩu Yếu · ${r} Mật Khẩu Dùng Lặp Lại.`,
  twoFaAndPasskeyCard: "2FA & Passkey",
  twoFaPasskeySummary: (t, p) => `${t} Mã TOTP Tích Hợp · ${p} Passkey FIDO2.`,
  unlockHeading: "Mở Khóa",
  enterPinPrompt: "Nhập Mã PIN",
  enterMasterPrompt: "Nhập Master Password",
  unlocking: "Đang Mở Khóa OpenVault...",
  useMasterPassword: "Dùng Master Password",
  usePin: (rem) => `Mở Bằng Mã PIN (${rem})`,
  signOut: "Đăng Xuất",
  signOutTitle: "Đăng Xuất Khỏi OpenVault?",
  signOutDesc: "Bộ nhớ đệm mã hóa cục bộ trên trình duyệt này sẽ bị xóa sạch. Dữ liệu trên Cloudflare D1 vẫn an toàn.",
  pinRestartNotice: "Trình duyệt vừa khởi động lại — nhập Master Password 1 lần, sau đó mã PIN sẽ tự kích hoạt lại.",
  settingsTitle: "Cài Đặt",
  settingsTabGeneral: "Chung",
  settingsTabAutofill: "Tự Điền",
  settingsTabSecurity: "Bảo Mật",
  settingsTabData: "Dữ Liệu",
  autoSyncActive: "Tự Động Đồng Bộ Đang Bật",
  changeAvatar: "Đổi Ảnh",
  removeAvatar: "Xóa",
  appearanceAndLang: "Giao Diện & Ngôn Ngữ",
  themeSystem: "Hệ Thống",
  themeLight: "Sáng",
  themeDark: "Tối",
  languageLabel: "Ngôn Ngữ",
  autofillSection: "Tự Động Điền",
  autosuggestSection: "Tự Động Đề Xuất",
  passkeysSection: "Mã Khóa (Passkeys)",
  browserSection: "Thiết Lập Trình Duyệt",
  securitySection: "Bảo Mật & Mở Khóa",
  backupSection: "Sao Lưu & Di Chuyển Dữ Liệu",
  aboutSection: "Giới Thiệu OpenVault",
  lockNow: "Khóa",
};

export function getStrings(language?: AppLanguage): ExtStrings {
  return language === "vi" ? VI : EN;
}

/**
 * Formats an ISO-8601 UTC timestamp from Cloudflare D1 in the user's local device timezone.
 */
export function formatLocalTimestamp(iso: string | null | undefined, lang: AppLanguage = "en"): string {
  if (!iso) return "";
  const trimmed = iso.trim();
  const parsed = new Date(trimmed.includes("T") || trimmed.endsWith("Z") ? trimmed : `${trimmed.replace(" ", "T")}Z`);
  if (Number.isNaN(parsed.getTime())) return trimmed;
  return new Intl.DateTimeFormat(lang === "vi" ? "vi-VN" : "en-US", {
    weekday: "short",
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).format(parsed);
}
