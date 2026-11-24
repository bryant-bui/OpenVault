import type { BgRequest, BgResponse } from "../../shared/types.js";

export async function sendBg<T = unknown>(request: BgRequest): Promise<T> {
  const response = (await chrome.runtime.sendMessage(request)) as BgResponse<T> | undefined;
  if (!response) {
    throw new Error("Không nhận được phản hồi từ OpenVault Service Worker.");
  }
  if (!response.ok) {
    throw new Error(response.error);
  }
  return response.data;
}

export async function getActiveTab(): Promise<chrome.tabs.Tab | null> {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  return tab ?? null;
}
