# Cloud Browser - Cloudflare Worker Proxy Rendering Engine

Backend service built specifically to power the **Cloud Browser** app on **Cloudflare Workers** using fast, global-edge streaming `HTMLRewriter`.

---

## 🚀 Cara Mendeploy ke Cloudflare Workers (ID)

### Opsi A: Menggunakan Node.js di Laptop / Komputer Lokal Anda
1. Pastikan Anda sudah menginstal **Node.js** (rekomendasi Versi 18+).
2. Download atau salin isi folder `/cloud-proxy-backend` ini ke komputer lokal Anda.
3. Buka terminal di folder tersebut dan jalankan:
   ```bash
   npm install
   ```
4. Login ke akun Cloudflare Anda melalui terminal dengan perintah:
   ```bash
   npx wrangler login
   ```
5. Deploy kode ke Cloudflare Workers dengan perintah:
   ```bash
   npm run deploy
   ```
6. Setelah sukses, Cloudflare akan menampilkan alamat URL Worker Anda, seperti:
   `https://cloud-browser-proxy.[username].workers.dev`
7. Salin URL tersebut dan masukkan ke dalam panel **Cloud Hub Configuration** di aplikasi **Cloud Browser** Anda!

---

## 🚀 How to Deploy to Cloudflare Workers (EN)

### Option A: Deploy using Local Node.js Environment
1. Ensure **Node.js** (V18+) is installed.
2. Download or copy `/cloud-proxy-backend` to your workspace.
3. Open terminal in the folder and execute:
   ```bash
   npm install
   ```
4. Log into your Cloudflare account using CLI:
   ```bash
   npx wrangler login
   ```
5. Build and Deploy to the Cloudflare Global Edge Network:
   ```bash
   npm run deploy
   ```
6. Copy the output Worker URL, for example:
   `https://cloud-browser-proxy.[username].workers.dev`
7. Enter it into the **Cloud Hub Configuration** panel in your mobile **Cloud Browser** application!
