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
  openSidePanel: string;
  settingsAndImport: string;
  lockVault: string;
  pendingSaveTitle: string;
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
  // Add More menu
  addSecQuestion: string;
  addText: string;
  addUrl: string;
  addEmail: string;
  addAddress: string;
  addDate: string;
  addOtp: string;
  addPasswordField: string;
  addPhone: string;
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
  // Settings
  settingsTitle: string;
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
  secureNotes: "Secure Notes",
  trash: "Trash",
  folders: "Folders",
  weakOrReused: "Weak / Reused",
  suggestedFor: "Suggested",
  results: "Results",
  noMatchingItems: "No matching items",
  emptyVaultTitle: "No items yet",
  tryOtherKeyword: "Try a different keyword.",
  emptyVaultHint: "Click + New Item or open Settings to import your vault.",
  welcomeTitle: "Welcome to OpenVault",
  welcomeSubtitle: "Select an item on the left to inspect it, or add a new Zero-Knowledge encrypted item.",
  importBtn: "Import Proton / Bitwarden",
  syncNow: "Sync now",
  passwordGenerator: "Password Generator",
  watchtower: "Watchtower",
  watchtowerAlerts: (n) => `Watchtower (${n} alerts)`,
  openSidePanel: "Open in Side Panel",
  settingsAndImport: "Settings & Import",
  lockVault: "Lock OpenVault",
  pendingSaveTitle: "Save new login?",
  dismiss: "Dismiss",
  personalVault: "Personal",
  fillNow: "Autofill",
  openAndFill: "Open & Fill",
  itemOptions: "Item options",
  editItem: "Edit item",
  addFavorite: "Add to Favorites",
  removeFavorite: "Remove from Favorites",
  fillCurrentTab: "Autofill in current tab",
  restoreFromTrash: "Restore from Trash",
  deletePermanently: "Delete Permanently",
  moveToTrash: "Move to Trash",
  inTrash: "In Trash",
  largeType: "Large Type",
  showPassword: "Show password",
  hidePassword: "Hide password",
  openWebsite: "Open website",
  lastEdited: "Last edited",
  added: "Added",
  customFields: "Custom fields",
  deletePermTitle: "Delete permanently?",
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
  itemNamePlaceholder: "e.g., GitHub, Gmail, Bank...",
  folderLabel: "Folder",
  noFolder: "No folder",
  favoriteLabel: "Favorite",
  usernameLabel: "Username / Email",
  passwordLabel: "Password",
  generateStrong: "Generate strong",
  passwordPlaceholder: "Enter or generate password",
  totpLabel: "One-Time Password (TOTP Secret or otpauth://)",
  websitesLabel: "Websites (URL)",
  addWebsite: "Add URL",
  notesLabel: "Secure Notes",
  notesPlaceholder: "Recovery codes, notes...",
  addMore: "Add more",
  changeIcon: "Custom icon",
  resetIcon: "Reset icon",
  removePasskeyTitle: "Remove passkey?",
  removePasskeyDesc: "This passkey will be permanently removed when you click Save.",
  fieldLabelPlaceholder: "field label",
  fieldValuePlaceholder: "value",
  addSecQuestion: "Security Question",
  addText: "Text",
  addUrl: "URL",
  addEmail: "Email",
  addAddress: "Address",
  addDate: "Date",
  addOtp: "One-Time Password",
  addPasswordField: "Password (Hidden)",
  addPhone: "Phone",
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
  settingsTitle: "OpenVault Settings",
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
  creditCards: "Thẻ tín dụng",
  identities: "Danh tính",
  secureNotes: "Ghi chú bảo mật",
  trash: "Thùng rác",
  folders: "Thư mục",
  weakOrReused: "Mật khẩu yếu / trùng",
  suggestedFor: "Gợi ý",
  results: "Kết quả",
  noMatchingItems: "Không tìm thấy mục nào",
  emptyVaultTitle: "Chưa có mục nào",
  tryOtherKeyword: "Thử từ khóa khác.",
  emptyVaultHint: "Nhấn + Mục mới hoặc mở Cài đặt để Import dữ liệu.",
  welcomeTitle: "Chào mừng đến OpenVault",
  welcomeSubtitle: "Chọn một mục bên trái để xem chi tiết, hoặc thêm tài khoản mới được mã hóa Zero-Knowledge.",
  importBtn: "Import Proton / Bitwarden",
  syncNow: "Đồng bộ ngay",
  passwordGenerator: "Trình tạo mật khẩu",
  watchtower: "Watchtower",
  watchtowerAlerts: (n) => `Watchtower (${n} cảnh báo)`,
  openSidePanel: "Mở ở Side Panel",
  settingsAndImport: "Cài đặt & Import",
  lockVault: "Khóa OpenVault",
  pendingSaveTitle: "Lưu đăng nhập mới?",
  dismiss: "Bỏ",
  personalVault: "Cá nhân",
  fillNow: "Điền ngay",
  openAndFill: "Mở & Điền",
  itemOptions: "Tùy chọn mục",
  editItem: "Chỉnh sửa mục",
  addFavorite: "Thêm vào yêu thích",
  removeFavorite: "Bỏ yêu thích",
  fillCurrentTab: "Điền vào tab hiện tại",
  restoreFromTrash: "Khôi phục từ thùng rác",
  deletePermanently: "Xóa vĩnh viễn",
  moveToTrash: "Chuyển vào thùng rác",
  inTrash: "Trong thùng rác",
  largeType: "Phóng to mật khẩu",
  showPassword: "Hiện mật khẩu",
  hidePassword: "Ẩn mật khẩu",
  openWebsite: "Mở trang web",
  lastEdited: "Chỉnh sửa lần cuối",
  added: "Đã thêm",
  customFields: "Trường tùy chỉnh",
  deletePermTitle: "Xóa vĩnh viễn mục này?",
  moveTrashTitle: "Chuyển vào thùng rác?",
  deletePermDesc: (name) => `"${name}" sẽ bị xóa hoàn toàn khỏi Cloudflare D1 và không thể khôi phục.`,
  moveTrashDesc: (name) => `"${name}" sẽ được chuyển vào Thùng rác. Bạn vẫn có thể khôi phục lại sau.`,
  strengthLabels: {
    0: "Yếu",
    1: "Trung bình",
    2: "Khá tốt",
    3: "Mạnh",
    4: "Tuyệt vời",
  },
  editItemTitle: "Chỉnh sửa mục",
  newItemTitle: "Thêm mục mới",
  itemNameLabel: "Tên mục *",
  itemNamePlaceholder: "Ví dụ: GitHub, Gmail, Vietcombank...",
  folderLabel: "Thư mục",
  noFolder: "Không có thư mục",
  favoriteLabel: "Yêu thích",
  usernameLabel: "Tên đăng nhập / Email",
  passwordLabel: "Mật khẩu",
  generateStrong: "Tạo mật khẩu mạnh",
  passwordPlaceholder: "Nhập hoặc tạo mật khẩu",
  totpLabel: "Khóa xác thực 2FA (TOTP Secret hoặc otpauth://)",
  websitesLabel: "Trang web (URL)",
  addWebsite: "Thêm URL",
  notesLabel: "Ghi chú bảo mật",
  notesPlaceholder: "Mã khôi phục, ghi chú...",
  addMore: "Thêm trường",
  changeIcon: "Đổi ảnh",
  resetIcon: "Mặc định",
  removePasskeyTitle: "Xóa khóa truy cập (Passkey)?",
  removePasskeyDesc: "Passkey này sẽ bị xóa vĩnh viễn khi bạn nhấn Lưu.",
  fieldLabelPlaceholder: "tên trường",
  fieldValuePlaceholder: "nội dung",
  addSecQuestion: "Câu hỏi bảo mật",
  addText: "Văn bản (Text)",
  addUrl: "Đường dẫn (URL)",
  addEmail: "Email",
  addAddress: "Địa chỉ",
  addDate: "Ngày tháng",
  addOtp: "Mã 2FA (OTP)",
  addPasswordField: "Mật khẩu ẩn",
  addPhone: "Số điện thoại",
  watchtowerTitle: "Watchtower Bảo Mật",
  vaultHealthScore: "Điểm An Toàn Kho",
  watchtowerLocalNote: (total) =>
    `Phân tích 100% cục bộ trên trình duyệt đối với ${total} tài khoản đăng nhập — không gửi dữ liệu lên mạng.`,
  weakAndReusedCard: "Mật khẩu yếu / trùng",
  weakReusedSummary: (w, r) => `${w} mật khẩu yếu · ${r} mật khẩu dùng lặp lại.`,
  twoFaAndPasskeyCard: "2FA & Passkey",
  twoFaPasskeySummary: (t, p) => `${t} mã TOTP tích hợp · ${p} Passkey FIDO2.`,
  unlockHeading: "Mở khóa",
  enterPinPrompt: "Nhập mã PIN",
  enterMasterPrompt: "Nhập Master Password",
  unlocking: "Đang mở khóa OpenVault...",
  useMasterPassword: "Dùng Master Password",
  usePin: (rem) => `Mở bằng mã PIN (${rem})`,
  signOut: "Đăng xuất",
  signOutTitle: "Đăng xuất khỏi OpenVault?",
  signOutDesc: "Bộ nhớ đệm mã hóa cục bộ trên trình duyệt này sẽ bị xóa sạch. Dữ liệu trên Cloudflare D1 vẫn an toàn.",
  pinRestartNotice: "Trình duyệt vừa khởi động lại — nhập Master Password 1 lần, sau đó mã PIN sẽ tự kích hoạt lại.",
  settingsTitle: "Cài đặt OpenVault",
  autoSyncActive: "Tự động đồng bộ đang bật",
  changeAvatar: "Đổi ảnh",
  removeAvatar: "Xóa",
  appearanceAndLang: "Giao diện & Ngôn ngữ",
  themeSystem: "Hệ thống",
  themeLight: "Sáng",
  themeDark: "Tối",
  languageLabel: "Ngôn ngữ",
  autofillSection: "Tự động điền",
  autosuggestSection: "Tự động đề xuất",
  passkeysSection: "Mã khóa (Passkeys)",
  browserSection: "Thiết lập trình duyệt",
  securitySection: "Bảo mật & Mở khóa",
  backupSection: "Sao lưu & Di chuyển dữ liệu",
  aboutSection: "Giới thiệu OpenVault",
  lockNow: "Khóa ngay",
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
