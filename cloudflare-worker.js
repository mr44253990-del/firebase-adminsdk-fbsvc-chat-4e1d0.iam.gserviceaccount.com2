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
          "Access-Control-Allow-Headers": "Content-Type",
        },
      });
    }

    if (request.method !== "POST") {
      return new Response(JSON.stringify({ error: "Only POST requests are supported" }), {
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

      return new Response(JSON.stringify({
        success: fcmResponse.ok,
        status: fcmResponse.status,
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
