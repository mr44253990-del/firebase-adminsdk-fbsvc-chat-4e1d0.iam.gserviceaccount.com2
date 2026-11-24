/* Shared desktop platform map — single source of truth for skin sizes and the
 * Android-to-desktop permission conversion. Kept free of Electron imports so
 * plain Node tests can validate it without a display server. */
"use strict";

const SKINS = {
  compact: { width: 420, height: 760, label: "Phone (420×760)" },
  standard: { width: 1024, height: 720, label: "Tablet (1024×720)" },
  large: { width: 1280, height: 820, label: "Desktop (1280×820)" },
};

const DEFAULT_SKIN = "standard";

/* Android runtime permission        -> desktop capability per OS */
const PLATFORM_PERMISSIONS = {
  darwin: {
    media: { allow: true, note: "camera + microphone in one macOS media request" },
    notifications: { allow: true },
    "clipboard-read": { allow: true },
    geolocation: { allow: false, note: "desktop build has no GPS provider; use the phone" },
    fullscreen: { allow: true },
    pointerLock: { allow: true },
    openExternal: { allow: true },
  },
  win32: {
    media: { allow: true, note: "camera + microphone handled by Windows privacy settings" },
    notifications: { allow: true, note: "uses Windows Action Center toasts" },
    "clipboard-read": { allow: true },
    geolocation: { allow: false, note: "desktop build has no GPS provider; use the phone" },
    fullscreen: { allow: true },
    pointerLock: { allow: true },
    openExternal: { allow: true },
  },
  linux: {
    media: { allow: true, note: "camera + microphone via PipeWire on Wayland / V4L2-ALSA on X11" },
    notifications: { allow: true, note: "uses the desktop notification daemon" },
    "clipboard-read": { allow: true },
    geolocation: { allow: false, note: "desktop build has no GPS provider; use the phone" },
    fullscreen: { allow: true },
    pointerLock: { allow: true },
    openExternal: { allow: true },
  },
};

function permissionsFor(platform) {
  return PLATFORM_PERMISSIONS[platform] || PLATFORM_PERMISSIONS.linux;
}

function allowedPermissions(platform) {
  const map = permissionsFor(platform);
  return Object.keys(map).filter((key) => map[key].allow);
}

module.exports = { SKINS, DEFAULT_SKIN, PLATFORM_PERMISSIONS, permissionsFor, allowedPermissions };
