# Cognitive Doc AI - Website Deployment

This is the fully responsive Web version of the **Cognitive Doc AI** dashboard, matching the visual styles, color spaces, layouts, multi-agent pipeline simulations, interactive knowledge graphs, and custom image upload processing from the Android Kotlin Application.

You can deploy this folder directly to Vercel in 1 click!

---

## 🚀 Speedrun Vercel Deployment

### Option A: Using Vercel Web Dashboard (No Code)
1. Copy or move this folder (`web/`) to an independent GitHub Repository.
2. Log in to your [Vercel Dashboard](https://vercel.com).
3. Click **Add New Project**, and select your GitHub repository.
4. Keep the default settings (Vercel automatically identifies it as a static client-side application due to `index.html`).
5. Click **Deploy**. Your site is now live on a `.vercel.app` URL!

### Option B: Using Vercel CLI (Super Fast)
1. Install Vercel CLI if you haven't already:
   ```bash
   npm install -g vercel
   ```
2. Navigate to this directory in your terminal:
   ```bash
   cd web
   ```
3. Deploy directly using the `vercel` command:
   ```bash
   vercel --prod
   ```
4. Follow the interactive steps (defaults are perfectly valid). Within 5 seconds, your URL is online!

---

## 🛠 Features Replicated
- **Interactive Knowledge Graph Fragment**: Dynamic SVG lines, custom nodes matching presets, and details popouts upon selection.
- **Cinematic Multi-Agent Simulated Pipeline**: Full progression checking (OCR, Domain, Risk, Graph, and Explainers) with real-time logging output console streams.
- **Dynamic Bento Widgets**: Custom styling, confidence rating, late payment warns, and risk index meters.
- **Uplink Sandbox Upload**: Drop/Choose custom images of docs to parse local compliance structures immediately.
- **History Logs**: Easily switch back to previous analyses. Powered securely by high-capacity `localStorage`.
- **Private Key Storage**: Users can enter their Gemini API key directly into the secure UI dialog to preserve maximum compliance privacy.
