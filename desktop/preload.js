/* Convo Chat desktop preload — minimal, sandbox-safe bridge that only exposes
 * non-sensitive platform facts so the shared web client can adapt its layout. */
"use strict";

const { contextBridge } = require("electron");

contextBridge.exposeInMainWorld("convoDesktop", {
  isDesktop: true,
  platform: process.platform,
  arch: process.arch,
  versions: {
    electron: process.versions.electron,
    chrome: process.versions.chrome,
    node: process.versions.node,
  },
});
