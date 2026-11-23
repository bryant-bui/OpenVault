/**
 * OpenVault MV3 Offscreen Document (Reason: CLIPBOARD)
 * Only uses chrome.runtime messaging + Web DOM copy event (per MV3 restrictions).
 * Handles background 2FA clipboard copy and automatic timed clipboard clearing
 * even after the popup/sidepanel has closed.
 */
function writeClipboardPlaintext(text) {
  let wrote = false;
  const onCopy = (event) => {
    event.preventDefault();
    if (event.clipboardData) {
      event.clipboardData.setData("text/plain", text);
      wrote = true;
    }
  };

  document.addEventListener("copy", onCopy, { once: true });
  const execOk = document.execCommand("copy");
  if (!execOk) {
    document.removeEventListener("copy", onCopy);
  }
  return execOk && wrote;
}

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (!message || message.target !== "offscreen" || message.type !== "OV_OFFSCREEN_CLIPBOARD") {
    return false;
  }

  const text = typeof message.text === "string" ? message.text : "";
  const ok = writeClipboardPlaintext(text);
  sendResponse({ ok });
  return false;
});
