/**
 * FireChat Cloudflare Worker
 * Handles FCM v1 Push Notification Delivery with Zero-Dependencies.
 *
 * This worker accepts a POST request with the target FCM token, title, and body,
 * signs an OAuth2 JWT using the Web Crypto API, exchanges it for a Google Access Token,
 * and calls the Firebase Cloud Messaging v1 API.
 *
 * Configure the secret env variable:
 * - FIREBASE_SERVICE_ACCOUNT: The entire JSON string of your Firebase Service Account Private Key.
 */

export default {
  async fetch(request, env, ctx) {
    // Enable CORS
    if (request.method === "OPTIONS") {
      return new Response("OK", {
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "POST, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type, Authorization",
        },
      });
    }

    if (request.method === "GET") {
      let projectId = null;
      let serviceAccountConfigured = false;
      try {
        const account = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT || "{}");
        projectId = account.project_id || null;
        serviceAccountConfigured = Boolean(account.private_key && account.client_email && projectId);
      } catch (_) {}
      return new Response(JSON.stringify({
        ok: serviceAccountConfigured,
        service: "FireChat Direct FCM Gateway",
        version: "3.2.0",
        projectId,
        serviceAccountConfigured,
        authenticatedCallsRequired: true,
        timestamp: Date.now()
      }), {
        status: serviceAccountConfigured ? 200 : 503,
        headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*", "Cache-Control": "no-store" }
      });
    }

    if (request.method !== "POST") {
      return new Response(JSON.stringify({ error: "Only GET health checks and POST notification requests are supported" }), {
        status: 405,
        headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
      });
    }

    try {
      const payload = await request.json();
      const { token, title, body, senderId, senderName, senderProfileUrl, notificationType, targetId } = payload;

      if (!token || !title || !body) {
        return new Response(JSON.stringify({ error: "Missing required fields: token, title, body" }), {
          status: 400,
          headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
        });
      }

      // 1. Parse Firebase Service Account Key
      if (!env.FIREBASE_SERVICE_ACCOUNT) {
        return new Response(JSON.stringify({ error: "Server configuration missing: FIREBASE_SERVICE_ACCOUNT env is not set." }), {
          status: 500,
          headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
        });
      }

      const serviceAccount = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT);
      const projectId = serviceAccount.project_id;
      const clientEmail = serviceAccount.client_email;
      const privateKeyPem = serviceAccount.private_key;

      if (!projectId || !clientEmail || !privateKeyPem) {
        return new Response(JSON.stringify({ error: "Invalid FIREBASE_SERVICE_ACCOUNT format. Must contain project_id, client_email, and private_key." }), {
          status: 500,
          headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
        });
      }

      // Authenticate the Android caller with its Firebase ID token. This prevents
      // an exposed Worker URL from becoming an unauthenticated push relay.
      const caller = await verifyFirebaseIdToken(request, projectId);
      if (!caller || (senderId && caller.sub !== senderId)) {
        return new Response(JSON.stringify({ error: "Unauthorized Firebase caller" }), {
          status: 401,
          headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
        });
      }

      // 2. Fetch Google OAuth2 Access Token using Web Crypto API
      const accessToken = await getGoogleAccessToken(clientEmail, privateKeyPem);

      // 3. Send Notification via FCM v1 API
      const fcmUrl = `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`;
      
      const fcmMessage = {
        message: {
          token: token,
          // Data-only high-priority payload: Android always routes this through
          // MyFirebaseMessagingService so message/request/activity styles stay distinct.
          data: {
            title: title || "FireChat",
            body: body || "You have a new update",
            senderId: senderId || "",
            senderName: senderName || "",
            senderProfileUrl: senderProfileUrl || "",
            notificationType: notificationType || "message",
            targetId: targetId || ""
          },
          android: {
            priority: "high"
          }
        }
      };

      const fcmResponse = await fetch(fcmUrl, {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${accessToken}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify(fcmMessage)
      });

      const fcmResult = await fcmResponse.json();

      const fcmError = fcmResult?.error;
      return new Response(JSON.stringify({
        success: fcmResponse.ok,
        status: fcmResponse.status,
        messageId: fcmResponse.ok ? fcmResult?.name : null,
        error: fcmResponse.ok ? null : (fcmError?.message || "FCM rejected the request"),
        errorStatus: fcmError?.status || null,
        requiredPermission: fcmResponse.status === 403 ? "cloudmessaging.messages.create" : null,
        response: fcmResult
      }), {
        status: fcmResponse.status,
        headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
      });

    } catch (err) {
      return new Response(JSON.stringify({ error: err.message, stack: err.stack }), {
        status: 500,
        headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
      });
    }
  }
};

/** Verify a Firebase Auth ID token using Google's rotating Secure Token JWKs. */
async function verifyFirebaseIdToken(request, projectId) {
  try {
    const authorization = request.headers.get("Authorization") || "";
    if (!authorization.startsWith("Bearer ")) return null;
    const jwt = authorization.slice(7);
    const parts = jwt.split(".");
    if (parts.length !== 3) return null;

    const header = JSON.parse(new TextDecoder().decode(base64UrlToBytes(parts[0])));
    const payload = JSON.parse(new TextDecoder().decode(base64UrlToBytes(parts[1])));
    const now = Math.floor(Date.now() / 1000);
    if (header.alg !== "RS256" || !header.kid || payload.aud !== projectId ||
        payload.iss !== `https://securetoken.google.com/${projectId}` ||
        payload.exp <= now || payload.iat > now + 60 || !payload.sub) return null;

    const jwksResponse = await fetch("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com");
    if (!jwksResponse.ok) return null;
    const jwks = await jwksResponse.json();
    const jwk = jwks.keys?.find(key => key.kid === header.kid);
    if (!jwk) return null;
    const cryptoKey = await crypto.subtle.importKey(
      "jwk", jwk, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]
    );
    const valid = await crypto.subtle.verify(
      "RSASSA-PKCS1-v1_5",
      cryptoKey,
      base64UrlToBytes(parts[2]),
      new TextEncoder().encode(`${parts[0]}.${parts[1]}`)
    );
    return valid ? payload : null;
  } catch (_) {
    return null;
  }
}

function base64UrlToBytes(value) {
  const base64 = value.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(value.length / 4) * 4, "=");
  const binary = atob(base64);
  return Uint8Array.from(binary, char => char.charCodeAt(0));
}

/**
 * Signs a RS256 JWT claim and exchanges it for a Google OAuth2 access token.
 */
async function getGoogleAccessToken(clientEmail, privateKeyPem) {
  const iat = Math.floor(Date.now() / 1000);
  const exp = iat + 3600; // 1 hour token validity

  const header = {
    alg: "RS256",
    typ: "JWT"
  };

  const claim = {
    iss: clientEmail,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    exp: exp,
    iat: iat
  };

  // Base64Url helper encoding
  const base64UrlEncode = (obj) => {
    const str = typeof obj === "string" ? obj : JSON.stringify(obj);
    const base64 = btoa(unescape(encodeURIComponent(str)));
    return base64.replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
  };

  const headerEncoded = base64UrlEncode(header);
  const claimEncoded = base64UrlEncode(claim);
  const dataToSign = `${headerEncoded}.${claimEncoded}`;

  // Sign with private key using crypto.subtle
  const signature = await signRS256(dataToSign, privateKeyPem);
  const jwt = `${dataToSign}.${signature}`;

  // Request OAuth2 access token
  const tokenResponse = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded"
    },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwt}`
  });

  const tokenResult = await tokenResponse.json();
  if (!tokenResponse.ok) {
    throw new Error(`Google OAuth error: ${JSON.stringify(tokenResult)}`);
  }

  return tokenResult.access_token;
}

/**
 * Sign data with RS256 Web Crypto API
 */
async function signRS256(data, pemKey) {
  // Extract binary PEM payload
  const pemHeader = "-----BEGIN PRIVATE KEY-----";
  const pemFooter = "-----END PRIVATE KEY-----";
  
  let rawPem = pemKey.replace(/\r/g, "").replace(/\n/g, "");
  if (rawPem.includes(pemHeader)) {
    rawPem = rawPem.substring(rawPem.indexOf(pemHeader) + pemHeader.length, rawPem.indexOf(pemFooter));
  }

  // Convert Base64 back to binary array
  const binaryString = atob(rawPem);
  const len = binaryString.length;
  const bytes = new Uint8Array(len);
  for (let i = 0; i < len; i++) {
    bytes[i] = binaryString.charCodeAt(i);
  }

  // Import private key in PKCS#8 format
  const cryptoKey = await crypto.subtle.importKey(
    "pkcs8",
    bytes.buffer,
    {
      name: "RSASSA-PKCS1-v1_5",
      hash: { name: "SHA-256" }
    },
    false,
    ["sign"]
  );

  // Sign data
  const encoder = new TextEncoder();
  const signatureBuffer = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    cryptoKey,
    encoder.encode(data)
  );

  // Convert signature back to Base64Url
  const signatureBytes = new Uint8Array(signatureBuffer);
  let signatureString = "";
  for (let i = 0; i < signatureBytes.byteLength; i++) {
    signatureString += String.fromCharCode(signatureBytes[i]);
  }
  
  return btoa(signatureString)
    .replace(/=/g, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
}
