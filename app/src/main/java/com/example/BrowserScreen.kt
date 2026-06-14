package com.example

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled", "DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(viewModel: BrowserViewModel) {
    val state by viewModel.state.collectAsState()
    var webView: WebView? by remember { mutableStateOf(null) }
    var urlInput by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.currentUrl) {
        urlInput = if (state.currentUrl == "about:home") "" else state.currentUrl
        if (state.currentUrl == "about:home") {
            webView?.loadUrl("about:blank")
        }
    }
    
    LaunchedEffect(state.cloudOptimizedContent) {
        state.cloudOptimizedContent?.let { content ->
            webView?.loadDataWithBaseURL(
                state.currentUrl, 
                content, 
                "text/html", 
                "UTF-8", 
                state.currentUrl
            )
        }
    }

    // Smart back control
    BackHandler(enabled = state.currentUrl != "about:home") {
        if (state.canGoBack && webView != null) {
            webView?.goBack()
        } else {
            viewModel.updateUrl("about:home")
        }
    }

    // Function to navigate safely
    val navigateToUrl = { input: String ->
        focusManager.clearFocus()
        var targetUrl = input.trim()
        if (targetUrl.isNotBlank()) {
            if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
                targetUrl = if (targetUrl.contains(".") && !targetUrl.contains(" ")) {
                    "https://$targetUrl"
                } else {
                    "https://www.google.com/search?q=$targetUrl"
                }
            }
            
            val finalUrl = if (state.cloudEngineEnabled && state.customCloudUrl.isNotBlank()) {
                try {
                    val encoded = java.net.URLEncoder.encode(targetUrl, "UTF-8")
                    "${state.customCloudUrl.trimEnd('/')}/render?url=$encoded"
                } catch (e: Exception) {
                    targetUrl
                }
            } else {
                targetUrl
            }
            viewModel.updateUrl(targetUrl)
            webView?.loadUrl(finalUrl)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier.background(MaterialTheme.colorScheme.background)
            ) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    title = {
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("url_input"),
                            placeholder = { Text("Search or type Web URL", style = MaterialTheme.typography.bodyMedium) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                            ),
                            shape = RoundedCornerShape(percent = 50),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = { navigateToUrl(urlInput) }
                            ),
                            leadingIcon = {
                                Icon(
                                    imageVector = if (state.cloudEngineEnabled) Icons.Default.Cloud else Icons.Default.Public,
                                    contentDescription = "Cloud Status",
                                    modifier = Modifier.size(20.dp),
                                    tint = if (state.cloudEngineEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingIcon = {
                                if (urlInput.isNotEmpty()) {
                                    IconButton(onClick = { urlInput = "" }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear URL", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.toggleCloudSettings() },
                            modifier = Modifier.testTag("toggle_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SettingsApplications,
                                contentDescription = "Cloud Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
                if (state.isLoading) {
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer,
                    )
                }
            }
        },
        bottomBar = {
            BottomAppBar(
                contentPadding = PaddingValues(horizontal = 16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                tonalElevation = 8.dp
            ) {
                IconButton(
                    onClick = { webView?.goBack() },
                    enabled = state.canGoBack && state.currentUrl != "about:home",
                    modifier = Modifier.testTag("back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { webView?.goForward() },
                    enabled = state.canGoForward && state.currentUrl != "about:home",
                    modifier = Modifier.testTag("forward_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp))
                        .clickable { 
                            viewModel.updateUrl("about:home")
                            urlInput = ""
                        }
                        .testTag("home_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Home, contentDescription = "Home Launcher", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.testTag("add_webapp_bottom_bar")
                ) {
                    Icon(Icons.Default.AddBox, contentDescription = "Add WebApp Shortcut", tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { viewModel.toggleCloudSettings() }
                ) {
                    Icon(Icons.Default.CloudQueue, contentDescription = "Cloud Status Panel", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            
            if (state.currentUrl == "about:home") {
                // Interactive Cloud Hub / Launch Dashboard
                CloudHomeDashboard(
                    state = state,
                    viewModel = viewModel,
                    onNavigate = { navigateToUrl(it) },
                    onAddNewApp = { showAddDialog = true }
                )
            } else {
                // Main WebView Core
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            webView = this
                            
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                cacheMode = WebSettings.LOAD_DEFAULT
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    if (url != "about:blank" && url != null) {
                                        viewModel.updateUrl(url)
                                    }
                                    viewModel.updateNavButtons(canGoBack(), canGoForward())
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    if (url != "about:blank" && url != null) {
                                        viewModel.updateUrl(url)
                                        view?.title?.let { viewModel.updateTitle(it) }
                                    }
                                    viewModel.updateNavButtons(canGoBack(), canGoForward())
                                    
                                    if (state.adBlockEnabled && state.cloudOptimizedContent == null) {
                                        view?.evaluateJavascript("""
                                            (function() {
                                                var style = document.createElement('style');
                                                style.innerHTML = '.ad, .ads, .ad-banner, .advertisement, [id^=google_ads] { display: none !important; }';
                                                document.head.appendChild(style);
                                            })();
                                        """.trimIndent(), null)
                                    }

                                    if (state.cloudEngineEnabled && state.cloudOptimizedContent == null && !state.isCloudProcessing && url != "about:blank" && !(url?.startsWith("data:") == true) && url != null) {
                                        // Extract text to optimize with local AI content pipeline fallback
                                        view?.evaluateJavascript(
                                            "(function() { return document.body.innerText; })();"
                                        ) { html ->
                                            if (html != null && html != "null") {
                                                val decodedHtml = html.replace("\\\\u003C", "<").replace("\\\"", "\"").replace("\\\\n", "\n")
                                                viewModel.optimizePageWithAI(decodedHtml)
                                            }
                                        }
                                    }
                                }
                                
                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    return false
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    super.onProgressChanged(view, newProgress)
                                    viewModel.updateProgress(newProgress)
                                }
                            }
                        }
                    },
                    update = { view ->
                        // System-level rendering sync 
                    }
                )
            }
        }
    }

    // Add Custom Web App Dialog Box
    if (showAddDialog) {
        var hostName by remember { mutableStateOf("") }
        var hostUrl by remember { mutableStateOf("") }
        var hostDesc by remember { mutableStateOf("") }
        var isError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Personal WebApp", style = MaterialTheme.typography.titleMedium)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Add a hosted link or custom URL bookmark to easily access, test, and render your published web apps here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    OutlinedTextField(
                        value = hostName,
                        onValueChange = { hostName = it },
                        label = { Text("WebApp Name") },
                        placeholder = { Text("My Vercel App") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = hostUrl,
                        onValueChange = { hostUrl = it; isError = false },
                        label = { Text("Web URL") },
                        placeholder = { Text("mysite.vercel.app or IP address") },
                        singleLine = true,
                        isError = isError,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = hostDesc,
                        onValueChange = { hostDesc = it },
                        label = { Text("Short Description") },
                        placeholder = { Text("React single page web app") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    if (isError) {
                        Text("URL cannot be empty", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (hostUrl.isBlank()) {
                            isError = true
                        } else {
                            val finalName = if (hostName.isBlank()) hostUrl else hostName
                            viewModel.addCustomWebApp(finalName, hostUrl, hostDesc)
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Add WebApp")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (state.showCloudSettings) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { viewModel.toggleCloudSettings() },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cloud Hub Configuration",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = Icons.Default.CloudQueue,
                        contentDescription = "Cloud Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Stats Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Simulated Web Data Safe", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            text = String.format("%.1f MB", state.dataSavedMb),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("Rendered content optimized by Cloud Proxy Engine", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                // Custom Cloudflare Worker Proxy Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Custom Cloudflare Worker",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Akselerasikan browsing Anda menggunakan Cloudflare Edge Workers secara offline-first. Anda bisa menginputkan URL Worker manual atau melakukan deploy otomatis instan dari aplikasi!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        var configModeByAuto by remember { mutableStateOf(false) }
                        
                        TabRow(
                            selectedTabIndex = if (configModeByAuto) 1 else 0,
                            modifier = Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(8.dp)),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Tab(
                                selected = !configModeByAuto,
                                onClick = { configModeByAuto = false },
                                text = { Text("Direct Worker URL", style = MaterialTheme.typography.labelMedium) }
                            )
                            Tab(
                                selected = configModeByAuto,
                                onClick = { configModeByAuto = true },
                                text = { Text("Instant Auto-Deploy", style = MaterialTheme.typography.labelMedium) }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (!configModeByAuto) {
                            Text(
                                text = "Masukkan URL custom Cloudflare Worker Anda:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            var cloudUrlInput by remember { mutableStateOf(state.customCloudUrl) }
                            OutlinedTextField(
                                value = cloudUrlInput,
                                onValueChange = { 
                                    cloudUrlInput = it
                                    viewModel.updateCustomCloudUrl(it)
                                },
                                modifier = Modifier.fillMaxWidth().testTag("custom_cloud_endpoint_input"),
                                placeholder = { Text("https://my-proxy.username.workers.dev") },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                )
                            )
                        } else {
                            Text(
                                text = "Deploy Worker langsung ke akun Cloudflare Anda:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            var accountIdInput by remember { mutableStateOf(state.cfAccountId) }
                            var apiTokenInput by remember { mutableStateOf(state.cfApiToken) }
                            var workerNameInput by remember { mutableStateOf(state.cfWorkerName) }
                            var showToken by remember { mutableStateOf(false) }

                            OutlinedTextField(
                                value = accountIdInput,
                                onValueChange = { 
                                    accountIdInput = it
                                    viewModel.updateCfCredentials(it, apiTokenInput, workerNameInput)
                                    viewModel.clearCfStatus()
                                },
                                label = { Text("Cloudflare Account ID") },
                                placeholder = { Text("contoh: 5ab64cc...") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = apiTokenInput,
                                onValueChange = { 
                                    apiTokenInput = it
                                    viewModel.updateCfCredentials(accountIdInput, it, workerNameInput)
                                    viewModel.clearCfStatus()
                                },
                                label = { Text("Cloudflare API Token") },
                                placeholder = { Text("Dengan izin Edit Workers") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium,
                                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showToken = !showToken }) {
                                        Icon(
                                            imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (showToken) "Sembunyikan" else "Tampilkan"
                                        )
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = workerNameInput,
                                onValueChange = { 
                                    workerNameInput = it
                                    viewModel.updateCfCredentials(accountIdInput, apiTokenInput, it)
                                    viewModel.clearCfStatus()
                                },
                                label = { Text("Worker Name") },
                                placeholder = { Text("cloud-browser-proxy") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            if (state.isCfDeploying) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Mengunggah script ke global Edge cloud...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            } else {
                                Button(
                                    onClick = { 
                                        viewModel.deployToCloudflare()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.CloudSync, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Instant Deploy & Connect")
                                }
                            }

                            if (state.cfDeployError != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Error, contentDescription = "Error", tint = MaterialTheme.colorScheme.error)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = state.cfDeployError ?: "",
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }

                            if (state.cfDeploySuccess) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = Color(0xFF2E7D32))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "Deployment Sukses!",
                                                color = Color(0xFF1B5E20),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Worker diaktifkan di global edge! URL diatur otomatis.",
                                                color = Color(0xFF1B5E20),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        if (state.customCloudUrl.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Connected to Live Edge Workers",
                                    color = Color(0xFF4CAF50),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Bypassing proxy: Running on built-in AI mode.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Settings List
                ListItem(
                    headlineContent = { Text("Cloud Rendering Engine", style = MaterialTheme.typography.bodyLarge) },
                    supportingContent = { Text("Accelerate loading and compress data flow", style = MaterialTheme.typography.bodyMedium) },
                    trailingContent = {
                        Switch(
                            checked = state.cloudEngineEnabled,
                            onCheckedChange = { viewModel.toggleCloudEngine(it) }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
 
                ListItem(
                    headlineContent = { Text("AdBlock & Tracking Protection", style = MaterialTheme.typography.bodyLarge) },
                    supportingContent = { Text("Block unnecessary scripts and malicious trackers", style = MaterialTheme.typography.bodyMedium) },
                    trailingContent = {
                        Switch(
                            checked = state.adBlockEnabled,
                            onCheckedChange = { viewModel.toggleAdBlock(it) }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                
                ListItem(
                    headlineContent = { Text("Flash Player Support", style = MaterialTheme.typography.bodyLarge) },
                    supportingContent = { Text("Enable rich media playback support", style = MaterialTheme.typography.bodyMedium) },
                    trailingContent = {
                        Switch(
                            checked = state.flashSupportEnabled,
                            onCheckedChange = { viewModel.toggleFlashSupport(it) }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CloudHomeDashboard(
    state: BrowserState,
    viewModel: BrowserViewModel,
    onNavigate: (String) -> Unit,
    onAddNewApp: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedAppToDelete by remember { mutableStateOf<WebApp?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        
        // Brand Logo and Title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Cloud Browser",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1).sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        
        Text(
            text = "Your Secure WebApps & Proxy Portal",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Web Address Search Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Launch Web Server / Engine URL",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("E.g., localhost:3000, portalku.net, google.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = { if (searchQuery.isNotBlank()) onNavigate(searchQuery) }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(
                            onClick = { if (searchQuery.isNotBlank()) onNavigate(searchQuery) },
                            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.ArrowCircleUp, contentDescription = "Load WebApp")
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // Live Server Integration Status Panel
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state.customCloudUrl.isNotBlank()) Icons.Default.Dns else Icons.Default.FilterList,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (state.customCloudUrl.isNotBlank()) "Cloudflare Workers Active" else "AI Reading Proxy Active",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = if (state.customCloudUrl.isNotBlank()) "Tunnels page data via Cloudflare Global Edge" else "Local crawler + Gemini dynamic reader optimization",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Section Title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "My WebApps Launcher",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(
                onClick = onAddNewApp,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add New WebApp Shortcut",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Grid of Web Apps (both preloads and custom)
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            state.customWebApps.forEach { webApp ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onNavigate(webApp.url) },
                            onLongClick = { selectedAppToDelete = webApp }
                        ),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (webApp.id.startsWith("default_")) Icons.Default.SettingsSystemDaydream else Icons.Default.Public,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = webApp.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = webApp.description.ifEmpty { webApp.url },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        IconButton(
                            onClick = { onNavigate(webApp.url) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ArrowOutward, contentDescription = "Open WebApp", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                        
                        if (!webApp.id.startsWith("default_")) {
                            IconButton(
                                onClick = { viewModel.deleteCustomWebApp(webApp.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Shortcut", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Instructional footer 
        Text(
            text = "Tip: Long-press custom bookmarks to open quick settings or delete them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
    }

    // Modal Confirmation to delete custom WebApp
    selectedAppToDelete?.let { appToDelete ->
        AlertDialog(
            onDismissRequest = { selectedAppToDelete = null },
            title = { Text("Delete WebApp Shortcut") },
            text = { Text("Are you sure you want to remove '${appToDelete.name}' shortcut?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCustomWebApp(appToDelete.id)
                        selectedAppToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedAppToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// Draw border helper 
@Composable
fun BorderStroke(width: androidx.compose.ui.unit.Dp, color: Color) = 
    androidx.compose.foundation.BorderStroke(width, color)
