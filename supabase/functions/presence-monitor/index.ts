// Convo Chat Presence Monitor — Supabase Edge Function entry point.
// Logic lives in handler.ts (routes) and presence.js (Firebase + FCM helpers).
import { handleRequest } from "./handler.ts";

Deno.serve((request) => handleRequest(request));
