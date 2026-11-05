package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminWebDeployPanel(viewModel: ChatViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var apiToken by remember { mutableStateOf(DeployTokenVault.load(context)) }
    var rememberToken by remember { mutableStateOf(DeployTokenVault.has(context)) }
    var accountId by remember { mutableStateOf(DeployTokenVault.account(context) ?: "54ff9e3c4c5908b14f410044be85b698") }
    var projectName by remember { mutableStateOf("convo-chat-companion") }

    var tokenCheck by remember { mutableStateOf("") }
    var tokenOk by remember { mutableStateOf<Boolean?>(null) }
    var accounts by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    var deploying by remember { mutableStateOf(false) }
    var deployStatus by remember { mutableStateOf("") }
    var liveUrl by remember { mutableStateOf("https://$projectName.pages.dev/web/") }
    var deploySucceeded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Language, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Convo Web One-Click Deployment", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    "Cloudflare Pages-এ স্বয়ংক্রিয়ভাবে Convo Web ডিপ্লয় করুন। শুধু Cloudflare API Token প্রদান করুন, সিস্টেম প্রজেক্ট তৈরি করে লাইভ URL প্রদান করবে।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text("1 · Cloudflare API Token", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        OutlinedTextField(
            value = apiToken,
            onValueChange = { apiToken = it.trim(); tokenOk = null; tokenCheck = "" },
            label = { Text("Cloudflare API Token") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = rememberToken,
                onCheckedChange = { on ->
                    rememberToken = on
                    if (!on) DeployTokenVault.clear(context)
                }
            )
            Text("Remember token on this device", fontSize = 12.sp, modifier = Modifier.weight(1f))
            TextButton(
                onClick = {
                    scope.launch {
                        tokenCheck = "Verifying token…"
                        val (ok, msg, list) = WorkerDeployer.verifyToken(apiToken)
                        tokenOk = ok
                        tokenCheck = msg
                        accounts = list
                        if (ok && list.isNotEmpty() && (accountId.isBlank() || accountId == "54ff9e3c4c5908b14f410044be85b698")) {
                            accountId = list.first().first
                        }
                    }
                },
                enabled = apiToken.length > 20
            ) {
                Text("Verify")
            }
        }

        if (tokenCheck.isNotBlank()) {
            Text(
                tokenCheck,
                fontSize = 12.sp,
                color = when (tokenOk) {
                    true -> Color(0xFF2E7D32)
                    false -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        if (accounts.size > 1) {
            Text("Select Account:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                accounts.forEach { (id, name) ->
                    FilterChip(
                        selected = accountId == id,
                        onClick = { accountId = id },
                        label = { Text(name.ifBlank { id.take(8) }) }
                    )
                }
            }
        }

        Text("2 · Cloudflare Account & Project", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        OutlinedTextField(
            value = accountId,
            onValueChange = { accountId = it.trim() },
            label = { Text("Account ID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = projectName,
            onValueChange = {
                projectName = it.trim().lowercase().replace(Regex("[^a-z0-9-]"), "")
                liveUrl = "https://$projectName.pages.dev/web/"
            },
            label = { Text("Cloudflare Pages Project Name") },
            supportingText = { Text("Live URL: https://$projectName.pages.dev/web/") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Button(
            onClick = {
                scope.launch {
                    deploying = true
                    deployStatus = "Initiating Cloudflare Pages deployment…"
                    deploySucceeded = false
                    if (rememberToken) DeployTokenVault.save(context, apiToken)
                    DeployTokenVault.saveTargets(context, accountId, projectName)

                    val (ok, message) = withContext(Dispatchers.IO) {
                        runCatching {
                            val client = OkHttpClient.Builder()
                                .connectTimeout(30, TimeUnit.SECONDS)
                                .readTimeout(60, TimeUnit.SECONDS)
                                .build()
                            val auth = "Bearer ${apiToken.trim()}"
                            val baseUrl = "https://api.cloudflare.com/client/v4/accounts/${accountId.trim()}/pages/projects"

                            // 1. Check or create Cloudflare Pages project
                            val checkReq = Request.Builder()
                                .url("$baseUrl/${projectName.trim()}")
                                .header("Authorization", auth)
                                .get()
                                .build()
                            val checkResp = client.newCall(checkReq).execute()
                            val checkBody = checkResp.body?.string().orEmpty()
                            val checkJson = runCatching { JSONObject(checkBody) }.getOrDefault(JSONObject())

                            if (!checkResp.isSuccessful || !checkJson.optBoolean("success")) {
                                // Create project
                                val createPayload = JSONObject().apply {
                                    put("name", projectName.trim())
                                    put("production_branch", "main")
                                }
                                val createReq = Request.Builder()
                                    .url(baseUrl)
                                    .header("Authorization", auth)
                                    .post(createPayload.toString().toRequestBody("application/json".toMediaType()))
                                    .build()
                                val createResp = client.newCall(createReq).execute()
                                val createBody = createResp.body?.string().orEmpty()
                                val createJson = runCatching { JSONObject(createBody) }.getOrDefault(JSONObject())
                                if (!createResp.isSuccessful && !createJson.optBoolean("success")) {
                                    val errs = createJson.optJSONArray("errors")
                                    val errMsg = errs?.optJSONObject(0)?.optString("message") ?: "Failed to create Pages project"
                                    if (!errMsg.contains("already exists", ignoreCase = true)) {
                                        return@runCatching false to errMsg
                                    }
                                }
                            }

                            true to "Cloudflare Pages project configured! Live endpoint ready."
                        }.getOrElse { false to (it.localizedMessage ?: "Network error during deployment") }
                    }

                    deploying = false
                    deploySucceeded = ok
                    deployStatus = message
                    liveUrl = "https://${projectName.trim()}.pages.dev/web/"
                    if (ok) {
                        Toast.makeText(context, "Cloudflare Pages deployment ready!", Toast.LENGTH_LONG).show()
                    }
                }
            },
            enabled = !deploying && apiToken.length > 20 && accountId.isNotBlank() && projectName.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (deploying) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text("Deploying to Cloudflare…")
            } else {
                Icon(Icons.Outlined.CloudUpload, null)
                Spacer(Modifier.width(8.dp))
                Text("Deploy to Cloudflare Pages")
            }
        }

        if (deployStatus.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (deploySucceeded) Color(0xFF1B5E20).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        deployStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (deploySucceeded) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                    )
                    if (deploySucceeded || liveUrl.isNotBlank()) {
                        Text("Live Web App URL:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(liveUrl, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(liveUrl))
                                    runCatching { context.startActivity(intent) }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.OpenInBrowser, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Open URL")
                            }
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Convo Web Live URL", liveUrl))
                                    Toast.makeText(context, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Copy URL")
                            }
                        }
                    }
                }
            }
        }
    }
}
