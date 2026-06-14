package com.example

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

data class WebApp(
    val id: String,
    val name: String,
    val url: String,
    val description: String
)

data class BrowserState(
    val currentUrl: String = "about:home",
    val title: String = "Cloud Browser",
    val progress: Int = 0,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val cloudEngineEnabled: Boolean = true,
    val flashSupportEnabled: Boolean = true,
    val adBlockEnabled: Boolean = true,
    val dataSavedMb: Float = 14.5f,
    val showCloudSettings: Boolean = false,
    
    // AI Variables
    val isCloudProcessing: Boolean = false,
    val cloudOptimizedContent: String? = null,
    
    // Cloudflare Workers Endpoint
    val customCloudUrl: String = "",
    
    // Custom Saved Web Apps / Bookmarks
    val customWebApps: List<WebApp> = emptyList(),

    // Cloudflare Instant Deployment State
    val cfAccountId: String = "",
    val cfApiToken: String = "",
    val cfWorkerName: String = "cloud-browser-proxy",
    val isCfDeploying: Boolean = false,
    val cfDeployError: String? = null,
    val cfDeploySuccess: Boolean = false
)

class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("cloud_browser_prefs", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _state.asStateFlow()

    init {
        // Load initial state
        val customUrl = prefs.getString("custom_cloud_url", "") ?: ""
        val adBlock = prefs.getBoolean("ad_block_enabled", true)
        val cloudEngine = prefs.getBoolean("cloud_engine_enabled", true)
        val flashSupport = prefs.getBoolean("flash_support_enabled", true)
        val savedData = prefs.getFloat("data_saved_mb", 14.5f)
        val webAppsJson = prefs.getString("custom_web_apps", "[]") ?: "[]"
        
        // Cloudflare Credentials
        val cfAccountId = prefs.getString("cf_account_id", "") ?: ""
        val cfApiToken = prefs.getString("cf_api_token", "") ?: ""
        val cfWorkerName = prefs.getString("cf_worker_name", "cloud-browser-proxy") ?: "cloud-browser-proxy"
        
        val loadedWebApps = mutableListOf<WebApp>()
        try {
            val jsonArray = JSONArray(webAppsJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                loadedWebApps.add(
                    WebApp(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        name = obj.optString("name", ""),
                        url = obj.optString("url", ""),
                        description = obj.optString("description", "")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Setup initial system shortcuts if empty
        if (loadedWebApps.isEmpty()) {
            loadedWebApps.add(WebApp("default_vercel", "Vercel Dashboard", "https://vercel.com/dashboard", "Manage and deploy your web apps"))
            loadedWebApps.add(WebApp("default_netlify", "Netlify Dashboard", "https://app.netlify.com", "Deploy sites in minutes"))
            loadedWebApps.add(WebApp("default_github", "GitHub", "https://github.com", "Host and manage your source repositories"))
            loadedWebApps.add(WebApp("default_cloudflare", "Cloudflare Dashboard", "https://dash.cloudflare.com", "Deploy and manage serverless edge Workers"))
            saveWebAppsList(loadedWebApps)
        }

        _state.update { 
            it.copy(
                customCloudUrl = customUrl,
                adBlockEnabled = adBlock,
                cloudEngineEnabled = cloudEngine,
                flashSupportEnabled = flashSupport,
                dataSavedMb = savedData,
                customWebApps = loadedWebApps,
                cfAccountId = cfAccountId,
                cfApiToken = cfApiToken,
                cfWorkerName = cfWorkerName
            ) 
        }
    }

    fun updateUrl(url: String) {
        _state.update { it.copy(currentUrl = url, cloudOptimizedContent = null) }
    }

    fun updateTitle(title: String) {
        _state.update { it.copy(title = title) }
    }
    
    fun updateProgress(progress: Int) {
        _state.update { it.copy(progress = progress, isLoading = progress < 100) }
    }

    fun updateNavButtons(canGoBack: Boolean, canGoForward: Boolean) {
        _state.update { it.copy(canGoBack = canGoBack, canGoForward = canGoForward) }
    }

    fun toggleCloudSettings() {
        _state.update { it.copy(showCloudSettings = !it.showCloudSettings) }
    }

    fun toggleCloudEngine(enabled: Boolean) {
        _state.update { it.copy(cloudEngineEnabled = enabled) }
        prefs.edit().putBoolean("cloud_engine_enabled", enabled).apply()
    }

    fun toggleFlashSupport(enabled: Boolean) {
        _state.update { it.copy(flashSupportEnabled = enabled) }
        prefs.edit().putBoolean("flash_support_enabled", enabled).apply()
    }

    fun toggleAdBlock(enabled: Boolean) {
        _state.update { it.copy(adBlockEnabled = enabled) }
        prefs.edit().putBoolean("ad_block_enabled", enabled).apply()
    }
    
    fun updateCustomCloudUrl(url: String) {
        _state.update { it.copy(customCloudUrl = url) }
        prefs.edit().putString("custom_cloud_url", url).apply()
    }
    
    fun clearCloudOptimizedContent() {
        _state.update { it.copy(cloudOptimizedContent = null) }
    }

    // Web App Actions
    fun addCustomWebApp(name: String, url: String, description: String) {
        val updatedList = _state.value.customWebApps.toMutableList().apply {
            add(
                WebApp(
                    id = java.util.UUID.randomUUID().toString(),
                    name = name,
                    url = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url,
                    description = description
                )
            )
        }
        _state.update { it.copy(customWebApps = updatedList) }
        saveWebAppsList(updatedList)
    }

    fun deleteCustomWebApp(id: String) {
        val updatedList = _state.value.customWebApps.filter { it.id != id }
        _state.update { it.copy(customWebApps = updatedList) }
        saveWebAppsList(updatedList)
    }

    private fun saveWebAppsList(list: List<WebApp>) {
        val jsonArray = JSONArray()
        list.forEach { 
            val obj = JSONObject()
            obj.put("id", it.id)
            obj.put("name", it.name)
            obj.put("url", it.url)
            obj.put("description", it.description)
            jsonArray.put(obj)
        }
        prefs.edit().putString("custom_web_apps", jsonArray.toString()).apply()
    }

    fun addDataSavings(amtSavings: Float) {
        val newSavings = _state.value.dataSavedMb + amtSavings
        _state.update { it.copy(dataSavedMb = newSavings) }
        prefs.edit().putFloat("data_saved_mb", newSavings).apply()
    }

    fun optimizePageWithAI(pageText: String) {
        if (!_state.value.cloudEngineEnabled) return
        if (pageText.isBlank()) return
        if (_state.value.currentUrl == "about:home") return
        // If Custom Cloud URL is used, we bypass Gemini Fallback Optimization to keep loading physical
        if (_state.value.customCloudUrl.isNotBlank() && _state.value.customCloudUrl.startsWith("http")) return
        
        _state.update { it.copy(isCloudProcessing = true) }
        
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val apiKey = BuildConfig.GEMINI_API_KEY
                    if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                        return@withContext "<div style='padding: 20px; color: red;'><b>Cloud Engine Error:</b><br/>Please configure your Gemini API Key in the AI Studio Secrets panel.</div>"
                    }
                    
                    val request = GenerateContentRequest(
                        contents = listOf(
                            Content(
                                parts = listOf(
                                    Part("Extract the main content from the following webpage text, format it as clean minimalistic HTML suitable for reading mode. Remove all ads, navigations, and tracker scripts. Provide ONLY the HTML output.\\n\\nWebpage Text:\\n$pageText")
                                )
                            )
                        )
                    )
                    val response = RetrofitClient.service.generateContent(apiKey, request)
                    val reply = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    reply?.replace("```html", "")?.replace("```", "")
                } catch (e: Exception) {
                    "<div style='padding: 20px; color: red;'><b>Cloud AI Failed:</b> ${e.message}</div>"
                }
            }
            
            val addedSavings = (Math.random() * 2.5).toFloat()
            _state.update { 
                it.copy(
                    isCloudProcessing = false, 
                    cloudOptimizedContent = result,
                    dataSavedMb = it.dataSavedMb + addedSavings
                )
            }
            addDataSavings(addedSavings)
        }
    }

    fun updateCfCredentials(accountId: String, apiToken: String, workerName: String) {
        _state.update { 
            it.copy(
                cfAccountId = accountId,
                cfApiToken = apiToken,
                cfWorkerName = workerName
            ) 
        }
        prefs.edit()
            .putString("cf_account_id", accountId)
            .putString("cf_api_token", apiToken)
            .putString("cf_worker_name", workerName)
            .apply()
    }

    fun clearCfStatus() {
        _state.update { it.copy(cfDeployError = null, cfDeploySuccess = false) }
    }

    fun deployToCloudflare() {
        val accountId = _state.value.cfAccountId.trim()
        val apiToken = _state.value.cfApiToken.trim()
        val workerName = _state.value.cfWorkerName.trim()

        if (accountId.isBlank() || apiToken.isBlank() || workerName.isBlank()) {
            _state.update { it.copy(cfDeployError = "Account ID, API Token, and Worker Name are required.") }
            return
        }

        _state.update { it.copy(isCfDeploying = true, cfDeployError = null, cfDeploySuccess = false) }

        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val client = okhttp3.OkHttpClient()
                    
                    // Step 1: Get Subdomain
                    val subdomainReq = okhttp3.Request.Builder()
                        .url("https://api.cloudflare.com/client/v4/accounts/$accountId/workers/subdomain")
                        .header("Authorization", "Bearer $apiToken")
                        .header("Content-Type", "application/json")
                        .get()
                        .build()
                        
                    var subdomain = ""
                    client.newCall(subdomainReq).execute().use { response ->
                        val bodyString = response.body?.string() ?: ""
                        if (!response.isSuccessful) {
                            throw Exception("Failed to get subdomain (HTTP ${response.code}): $bodyString")
                        }
                        val json = JSONObject(bodyString)
                        if (json.getBoolean("success")) {
                            val result = json.getJSONObject("result")
                            subdomain = result.getString("subdomain")
                        } else {
                            throw Exception("Cloudflare API returned error: $bodyString")
                        }
                    }

                    // Step 2: Upload Script
                    val scriptBody = WORKER_SCRIPT.toRequestBody("application/javascript; charset=utf-8".toMediaTypeOrNull())
                    
                    val scriptReq = okhttp3.Request.Builder()
                        .url("https://api.cloudflare.com/client/v4/accounts/$accountId/workers/scripts/$workerName")
                        .header("Authorization", "Bearer $apiToken")
                        .put(scriptBody)
                        .build()

                    client.newCall(scriptReq).execute().use { response ->
                        if (!response.isSuccessful) {
                            val bodyString = response.body?.string() ?: ""
                            throw Exception("Script upload failed (HTTP ${response.code}): $bodyString")
                        }
                    }

                    // Step 3: Enable workers.dev subdomain for this script
                    val subdomainToggleBody = "{\"enabled\":true}".toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                    
                    val toggleReq = okhttp3.Request.Builder()
                        .url("https://api.cloudflare.com/client/v4/accounts/$accountId/workers/scripts/$workerName/subdomain")
                        .header("Authorization", "Bearer $apiToken")
                        .post(subdomainToggleBody)
                        .build()

                    client.newCall(toggleReq).execute().use { response ->
                        if (!response.isSuccessful && response.code != 409) {
                            val bodyString = response.body?.string() ?: ""
                            throw Exception("Route activation failed (HTTP ${response.code}): $bodyString")
                        }
                    }

                    val deployedUrl = "https://$workerName.$subdomain.workers.dev"
                    
                    withContext(Dispatchers.Main) {
                        updateCustomCloudUrl(deployedUrl)
                    }
                    true
                } catch (e: Exception) {
                    val rootMsg = e.message ?: "Unknown API error"
                    _state.update { it.copy(cfDeployError = rootMsg) }
                    false
                }
            }

            _state.update { 
                it.copy(
                    isCfDeploying = false,
                    cfDeploySuccess = success
                ) 
            }
        }
    }
}

private const val WORKER_SCRIPT = """
addEventListener('fetch', event => {
  event.respondWith(handleRequest(event.request))
});

async function handleRequest(request) {
  const url = new URL(request.url);
  
  if (url.pathname === '/' || url.pathname === '') {
    const targetUrl = url.searchParams.get('url');
    if (!targetUrl) {
       return new Response(JSON.stringify({
         status: "ok",
         service: "Cloud Browser Proxy Rendering Engine",
         deployment: "Cloudflare Workers",
         version: "1.0.0",
         uptime: "Active on the Edge"
       }), {
         status: 200,
         headers: { 
           "Content-Type": "application/json", 
           "Access-Control-Allow-Origin": "*" 
         }
       });
    }
    return handleProxy(targetUrl, request);
  }
  
  if (url.pathname === '/render') {
    const targetUrl = url.searchParams.get('url');
    if (!targetUrl) {
      return new Response("Missing 'url' query parameter.", { 
        status: 400,
        headers: { "Access-Control-Allow-Origin": "*" }
      });
    }
    return handleProxy(targetUrl, request);
  }
  
  return new Response("Not Found", { status: 404 });
}

async function handleProxy(targetUrl, request) {
  try {
    const parsedTarget = new URL(targetUrl);
    
    const response = await fetch(targetUrl, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36',
        'Accept-Language': 'en-US,en;q=0.9',
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8'
      }
    });
    
    const contentType = response.headers.get('content-type') || '';
    if (!contentType.includes('text/html')) {
      const passThroughHeaders = new Headers(response.headers);
      passThroughHeaders.set('Access-Control-Allow-Origin', '*');
      return new Response(response.body, {
        status: response.status,
        headers: passThroughHeaders
      });
    }
    
    const baseUrl = parsedTarget.href;
    const workerUrl = new URL(request.url);
    const workerOrigin = workerUrl.origin;
    
    const rewriter = new HTMLRewriter()
      .on('script', {
        element(el) { el.remove(); }
      })
      .on('iframe', {
        element(el) { el.remove(); }
      })
      .on('.ads, .ad, .advertisement, [id^="google_ads"], [class*="ad-"]', {
        element(el) { el.remove(); }
      })
      .on('a', {
        element(el) {
          const href = el.getAttribute('href');
          if (href) {
            try {
              const absoluteUrl = new URL(href, baseUrl).href;
              if (absoluteUrl.startsWith('http')) {
                el.setAttribute('href', workerOrigin + '/render?url=' + encodeURIComponent(absoluteUrl));
              }
            } catch (_) {}
          }
        }
      })
      .on('img', {
        element(el) {
          const src = el.getAttribute('src');
          if (src) {
            try {
              el.setAttribute('src', new URL(src, baseUrl).href);
            } catch (_) {}
          }
        }
      })
      .on('link[rel="stylesheet"]', {
        element(el) {
          const href = el.getAttribute('href');
          if (href) {
            try {
              el.setAttribute('href', new URL(href, baseUrl).href);
            } catch (_) {}
          }
        }
      });
      
    const transformedResponse = rewriter.transform(response);
    const finalHeaders = new Headers(transformedResponse.headers);
    finalHeaders.set('Access-Control-Allow-Origin', '*');
    finalHeaders.set('Content-Type', 'text/html; charset=utf-8');
    
    return new Response(transformedResponse.body, {
      status: transformedResponse.status,
      headers: finalHeaders
    });
    
  } catch (error) {
    return new Response(
      '<div style="font-family: -apple-system, BlinkMacSystemFont, \'Segoe UI\', Roboto, Helvetica, Arial, sans-serif; padding: 24px; max-width: 600px; margin: 40px auto; border-radius: 16px; border: 1px solid #FFDAD6; background-color: #FAFDFD; color: #191C1D; box-shadow: 0 4px 12px rgba(0,0,0,0.05);">' +
      '<h3 style="color: #BA1A1A; font-size: 20px; font-weight: 600; margin-top: 0;">Cloudflare Worker - Edge Rendering Error</h3>' +
      '<p style="font-size: 14px; line-height: 1.5; color: #3F484A;">The browser failed to tunnel or filter the target URL at the Cloudflare Edge network.</p>' +
      '<div style="background: #EFF1F1; padding: 12px 16px; border-radius: 8px; font-family: monospace; font-size: 13px; margin: 16px 0; overflow-x: auto; color: #001F24;">' +
      '<b>Requested URL:</b> ' + targetUrl + '<br/>' +
      '<b>Execution Error:</b> ' + error.message +
      '</div>' +
      '<button onclick="window.history.back()" style="padding: 10px 20px; font-weight: 500; font-size: 14px; background-color: #006874; color: white; border: none; border-radius: 20px; cursor: pointer;">Go Back</button>' +
      '</div>',
      {
        status: 200,
        headers: { 
          'Content-Type': 'text/html; charset=utf-8',
          'Access-Control-Allow-Origin': '*'
        }
      }
    );
  }
}
"""
