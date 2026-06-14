export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    
    // Health check or index page
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
      
      // Serve direct proxy handling if passed in query param
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
};

async function handleProxy(targetUrl, request) {
  try {
    const parsedTarget = new URL(targetUrl);
    
    // Fetch origin webpage content with modern mobile User-Agent
    const response = await fetch(targetUrl, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36',
        'Accept-Language': 'en-US,en;q=0.9',
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8'
      }
    });
    
    const contentType = response.headers.get('content-type') || '';
    if (!contentType.includes('text/html')) {
      // Direct pass-through for assets/images to load with CORS support
      const passThroughHeaders = new Headers(response.headers);
      passThroughHeaders.set('Access-Control-Allow-Origin', '*');
      return new Response(response.body, {
        status: response.status,
        headers: passThroughHeaders
      });
    }
    
    // Configure HTMLRewriter to drop bloated ads/trackers and adapt links on-the-fly
    const baseUrl = parsedTarget.href;
    const workerUrl = new URL(request.url);
    const workerOrigin = workerUrl.origin;
    
    const rewriter = new HTMLRewriter()
      // 1. Remove dangerous or non-functional scripts and inline components
      .on('script', {
        element(el) { el.remove(); }
      })
      .on('iframe', {
        element(el) { el.remove(); }
      })
      // 2. Remove typical visual clutter, popups, ads, trackers
      .on('.ads, .ad, .advertisement, [id^="google_ads"], [class*="ad-"]', {
        element(el) { el.remove(); }
      })
      // 3. Keep standard navigation flow mapped through our Cloudflare Worker
      .on('a', {
        element(el) {
          const href = el.getAttribute('href');
          if (href) {
            try {
              const absoluteUrl = new URL(href, baseUrl).href;
              if (absoluteUrl.startsWith('http')) {
                el.setAttribute('href', `${workerOrigin}/render?url=${encodeURIComponent(absoluteUrl)}`);
              }
            } catch (_) {}
          }
        }
      })
      // 4. Resolve relative URLs so images and styling rules render perfectly
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
    return new Response(`
      <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; padding: 24px; max-width: 600px; margin: 40px auto; border-radius: 16px; border: 1px solid #FFDAD6; background-color: #FAFDFD; color: #191C1D; box-shadow: 0 4px 12px rgba(0,0,0,0.05);">
        <h3 style="color: #BA1A1A; font-size: 20px; font-weight: 600; margin-top: 0;">Cloudflare Worker - Edge Rendering Error</h3>
        <p style="font-size: 14px; line-height: 1.5; color: #3F484A;">The browser failed to tunnel or filter the target URL at the Cloudflare Edge network.</p>
        <div style="background: #EFF1F1; padding: 12px 16px; border-radius: 8px; font-family: monospace; font-size: 13px; margin: 16px 0; overflow-x: auto; color: #001F24;">
          <b>Requested URL:</b> ${targetUrl}<br/>
          <b>Execution Error:</b> ${error.message}
        </div>
        <button onclick="window.history.back()" style="padding: 10px 20px; font-weight: 500; font-size: 14px; background-color: #006874; color: white; border: none; border-radius: 20px; cursor: pointer;">Go Back</button>
      </div>
    `, {
      status: 200,
      headers: { 
        'Content-Type': 'text/html; charset=utf-8',
        'Access-Control-Allow-Origin': '*'
      }
    });
  }
}
