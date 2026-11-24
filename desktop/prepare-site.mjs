#!/usr/bin/env node
/* Copies the shared web client (web-companion/site) into desktop/site so the
 * packaged app always ships the exact same UI as the deployed web version.
 * Keeping one source of truth avoids drifting desktop/web interfaces. */
import { cpSync, mkdirSync, rmSync, existsSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const src = resolve(root, "web-companion/site");
const dest = resolve(root, "desktop/site");

if (!existsSync(src)) {
  console.error("prepare-site: web-companion/site not found at " + src);
  process.exit(1);
}
rmSync(dest, { recursive: true, force: true });
mkdirSync(dest, { recursive: true });
cpSync(src, dest, { recursive: true });
console.log("prepare-site: copied web-companion/site -> desktop/site");
