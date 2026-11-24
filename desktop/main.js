/* Convo Chat desktop main process.
 * Runs the shared web client on a loopback-only HTTP server so localStorage,
 * microphone/camera and notifications behave exactly like the deployed web app,
 * without exposing the app to the network. */
"use strict";

const { app, BrowserWindow, Menu, session, shell, dialog, nativeTheme } = require("electron");
const http = require("node:http");
const path = require("node:path");
const crypto = require("node:crypto");

const SITE_DIR = app.isPackaged
  ? path.join(process.resourcesPath, "site")
  : path.join(__dirname, "site");

const MIME = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".webmanifest": "application/manifest+json; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".gif": "image/gif",
  ".webp": "image/webp",
  ".ico": "image/x-icon",
};

const { SKINS, DEFAULT_SKIN, permissionsFor } = require("./desktop-map");

function defaultSkin() {
  return DEFAULT_SKIN;
}

/* --------------------------------------------------------- permissions */
/* The Android app requests runtime permissions for camera, microphone,
 * notifications and location. The desktop client converts those into the
 * closest platform capability and grants them per operating system. */
function installPermissionHandlers(ses) {
  const map = permissionsFor(process.platform);
  ses.setPermissionRequestHandler((wc, permission, callback) => {
    const rule = map[permission];
    if (rule && rule.allow) return callback(true);
    if (rule && rule.note) console.log(`[permissions] denied ${permission}: ${rule.note}`);
    return callback(false);
  });
  ses.setPermissionCheckHandler((wc, permission) => {
    const rule = map[permission];
    return Boolean(rule && rule.allow);
  });
  const granted = Object.keys(map).filter((k) => map[k].allow).join(", ");
  console.log(`[permissions] ${process.platform}: granted -> ${granted}`);
  return map;
}

/* ------------------------------------------------------------ loopback */
function startSiteServer() {
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, "http://127.0.0.1");
    let pathname = decodeURIComponent(url.pathname);
    if (pathname === "/") pathname = "/index.html";
    const file = path.normalize(path.join(SITE_DIR, pathname));
    if (!file.startsWith(SITE_DIR)) {
      res.writeHead(403).end("forbidden");
      return;
    }
    const ext = path.extname(file).toLowerCase();
    const mime = MIME[ext] || "application/octet-stream";
    const cache = app.isPackaged ? "public,max-age=86400" : "no-store";
    const { readFile } = require("node:fs");
    readFile(file, (err, data) => {
      if (err) {
        res.writeHead(404).end("not found");
        return;
      }
      res.writeHead(200, {
        "Content-Type": mime,
        "Cache-Control": cache,
        "X-Content-Type-Options": "nosniff",
        "Referrer-Policy": "strict-origin-when-cross-origin",
      });
      res.end(data);
    });
  });
  return new Promise((resolve, reject) => {
    // Bind to loopback only — the app must never listen on the network.
    server.once("error", reject);
    const token = crypto.randomBytes(8).toString("hex");
    server.listen(0, "127.0.0.1", () => {
      const { port } = server.address();
      resolve({ port, token });
    });
  });
}

/* ---------------------------------------------------------------- window */
let mainWindow = null;

function readWindowState() {
  try {
    const file = path.join(app.getPath("userData"), "window-state.json");
    return JSON.parse(require("node:fs").readFileSync(file, "utf8"));
  } catch {
    return null;
  }
}

function writeWindowState(win, skin) {
  try {
    const bounds = win.getNormalBounds ? win.getNormalBounds() : win.getBounds();
    const state = Object.assign(bounds, { skin, maximized: win.isMaximized() });
    require("node:fs").writeFileSync(
      path.join(app.getPath("userData"), "window-state.json"),
      JSON.stringify(state)
    );
  } catch {}
}

function applySkin(win, skin) {
  const size = SKINS[skin] || SKINS[defaultSkin()];
  if (win.isMaximized()) win.unmaximize();
  const { workArea } = require("electron").screen.getPrimaryDisplay();
  const width = Math.min(size.width, workArea.width);
  const height = Math.min(size.height, workArea.height);
  win.setContentSize(width, height);
  const b = win.getBounds();
  win.setPosition(
    Math.max(workArea.x, workArea.x + Math.round((workArea.width - b.width) / 2)),
    Math.max(workArea.y, workArea.y + Math.round((workArea.height - b.height) / 2))
  );
  writeWindowState(win, skin);
}

function createWindow(skin) {
  const saved = readWindowState();
  const chosen = skin || (saved && SKINS[saved.skin] ? saved.skin : defaultSkin());
  const size = SKINS[chosen];
  mainWindow = new BrowserWindow({
    width: size.width,
    height: size.height,
    minWidth: 360,
    minHeight: 540,
    show: false,
    backgroundColor: nativeTheme.shouldUseDarkColors ? "#0b0f16" : "#f5f7fb",
    title: "Convo Chat",
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      mediaFeaturesPrivileged: true,
    },
  });
  if (saved && saved.maximized) mainWindow.maximize();
  if (saved) {
    mainWindow.setBounds({
      x: saved.x,
      y: saved.y,
      width: Math.min(saved.width, SKINS[chosen].width * 1.5),
      height: Math.min(saved.height, SKINS[chosen].height * 1.5),
    });
  }
  applySkin(mainWindow, chosen);
  mainWindow.once("ready-to-show", () => mainWindow.show());
  mainWindow.on("resized", () => writeWindowState(mainWindow, chosen));
  mainWindow.on("moved", () => writeWindowState(mainWindow, chosen));
  mainWindow.on("close", () => writeWindowState(mainWindow, chosen));
  mainWindow.on("closed", () => {
    mainWindow = null;
  });
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url);
    return { action: "deny" };
  });
  return mainWindow;
}

function buildMenu(applySkinFn, getConnectionInfo) {
  const template = [
    {
      label: "&File",
      submenu: [
        { label: "Reload", accelerator: "CmdOrCtrl+R", click: () => mainWindow && mainWindow.reload() },
        { type: "separator" },
        { role: "quit" },
      ],
    },
    {
      label: "&View",
      submenu: [
        ...Object.keys(SKINS).map((key) => ({
          label: `Skin size: ${SKINS[key].label}`,
          click: () => applySkinFn(mainWindow, key),
        })),
        { type: "separator" },
        { role: "toggleDevTools" },
        { role: "resetZoom" },
        { role: "zoomIn" },
        { role: "zoomOut" },
        { role: "togglefullscreen" },
      ],
    },
    {
      label: "&Help",
      submenu: [
        {
          label: "About Convo Chat Desktop",
          click: () => {
            dialog.showMessageBox({
              type: "info",
              title: "About",
              message: "Convo Chat Desktop",
              detail:
                `Version: ${app.getVersion()}\nPlatform: ${process.platform} (${process.arch})\n` +
                `Local server: ${getConnectionInfo()}\n\n` +
                "Same Convo Chat experience as the Android app — login with your existing account.",
            });
          },
        },
      ],
    },
  ];
  Menu.setApplicationMenu(Menu.buildFromTemplate(template));
}

/* ----------------------------------------------------------------- boot */
const gotLock = app.requestSingleInstanceLock();
if (!gotLock) {
  app.quit();
} else {
  app.on("second-instance", () => {
    if (mainWindow) {
      if (mainWindow.isMinimized()) mainWindow.restore();
      mainWindow.focus();
    }
  });

  app.whenReady().then(async () => {
    const { port, token } = await startSiteServer();
    const url = `http://127.0.0.1:${port}/?desktop=${token}&platform=${process.platform}&arch=${process.arch}`;
    installPermissionHandlers(session.defaultSession);
    createWindow();
    buildMenu(applySkin, () => `127.0.0.1:${port} (loopback only)`);
    mainWindow.loadURL(url);
    app.on("activate", () => {
      if (BrowserWindow.getAllWindows().length === 0) createWindow();
    });
  });

  app.on("window-all-closed", () => {
    if (process.platform !== "darwin") app.quit();
  });
}
