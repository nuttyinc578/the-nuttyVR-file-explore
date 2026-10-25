"use strict";

// Exercise the published project path and real browser interactions before Pages deployment.
const assert = require("node:assert/strict");
const fs = require("node:fs/promises");
const http = require("node:http");
const path = require("node:path");
const { chromium } = require(process.env.NUTTYVR_PLAYWRIGHT_PATH || "playwright");
const root = path.resolve(__dirname, "../website");
const prefix = "/the-nuttyVR-file-explore/";
const output = path.resolve(__dirname, "../build/website-qa");
const types = { ".html": "text/html", ".css": "text/css", ".js": "text/javascript", ".svg": "image/svg+xml", ".xml": "application/xml", ".txt": "text/plain" };

const server = http.createServer(async (request, response) => {
  try {
    const pathname = new URL(request.url, "http://localhost").pathname;
    if (!pathname.startsWith(prefix)) { response.writeHead(404); response.end(); return; }
    const relative = decodeURIComponent(pathname.slice(prefix.length)) || "index.html";
    const file = path.resolve(root, relative);
    if (!file.startsWith(root + path.sep)) { response.writeHead(403); response.end(); return; }
    const bytes = await fs.readFile(file);
    response.writeHead(200, { "Content-Type": `${types[path.extname(file)] || "application/octet-stream"}; charset=utf-8` });
    response.end(bytes);
  } catch { response.writeHead(404); response.end(); }
});

(async () => {
  await fs.mkdir(output, { recursive: true });
  await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
  const origin = `http://127.0.0.1:${server.address().port}`;
  const browser = await chromium.launch({ channel: "chrome", headless: true });
  try {
    for (const viewport of [{ width: 1440, height: 1000, name: "desktop" }, { width: 768, height: 1024, name: "tablet" }, { width: 390, height: 844, name: "mobile" }, { width: 320, height: 812, name: "small-mobile" }]) {
      const context = await browser.newContext({ viewport: { width: viewport.width, height: viewport.height }, reducedMotion: "reduce" });
      const page = await context.newPage();
      const errors = [];
      page.on("pageerror", error => errors.push(error.message));
      // Verify that rate limits or an unavailable metadata API cannot break the page or its downloads.
      await page.route("https://api.github.com/**", route => route.abort());
      try {
        await page.goto(origin + prefix, { waitUntil: "networkidle" });
        await page.locator("[data-demo-grid] .demo-file").first().waitFor();
        assert.equal(await page.locator("[data-demo-grid] .demo-file").count(), 6);
        await page.screenshot({ path: path.join(output, `${viewport.name}.png`), fullPage: true });
        const overflow = await page.evaluate(() => ({ viewport: innerWidth, width: document.documentElement.scrollWidth }));
        assert.ok(overflow.width <= overflow.viewport + 1, `Horizontal page overflow at ${viewport.name}: ${JSON.stringify(overflow)}`);
        assert.equal(await page.locator("h1").count(), 1);
        const download = page.getByRole("link", { name: "Download nightly APK", exact: true });
        assert.equal(await download.getAttribute("href"), "https://github.com/nuttyinc578/the-nuttyVR-file-explore/releases/download/nightly/NuttyVR-File-Explorer-nightly.apk");
        await page.getByRole("button", { name: "Open sample Documents folder", exact: true }).click();
        assert.equal(await page.locator("[data-demo-path]").textContent(), "Documents");
        assert.equal(await page.locator("[data-demo-grid] .demo-file").count(), 3);
        await page.getByRole("button", { name: "Back to all sample files" }).click();
        const search = page.getByRole("searchbox", { name: "Search sample files in this folder" });
        await search.fill("does-not-exist");
        assert.equal(await page.locator("[data-demo-grid] .demo-file").count(), 0);
        assert.match(await page.locator("[data-demo-grid]").textContent(), /No matching/);
        await search.fill("space");
        assert.equal(await page.locator("[data-demo-grid] .demo-file").count(), 1);
        await search.fill("");
        await page.getByRole("button", { name: "Select sample file your-next-app.apk", exact: true }).click();
        assert.match(await page.locator("[data-demo-hint]").textContent(), /install, then Open app/);
        await page.getByRole("button", { name: "List view", exact: true }).click();
        assert.equal(await page.locator(".file-grid.list-view").count(), 1);
        await page.getByRole("button", { name: "Grid view", exact: true }).click();
        const xr = page.getByRole("tab", { name: "Android XR", exact: true });
        await xr.click();
        assert.equal(await page.locator("#xr-guide").isVisible(), true);
        assert.equal(await page.locator("#quest-guide").isVisible(), false);
        await xr.press("ArrowLeft");
        assert.equal(await page.getByRole("tab", { name: "Meta Quest", exact: true }).getAttribute("aria-selected"), "true");
        const question = page.locator(".faq summary").first();
        await question.click();
        assert.equal(await page.locator(".faq details").first().getAttribute("open"), "");
        await context.grantPermissions(["clipboard-read", "clipboard-write"], { origin });
        await page.getByRole("button", { name: "Copy APK installation command", exact: true }).click();
        await page.getByRole("status").filter({ hasText: "Command copied" }).waitFor();
        assert.equal(await page.evaluate(() => navigator.clipboard.readText()), "adb install -r NuttyVR-File-Explorer-nightly.apk");
        assert.deepEqual(errors, [], "Unexpected browser errors");
        console.log(`PASS ${viewport.name} (${viewport.width}px): project-path assets, responsive layout, folders, search, selection, grid/list, keyboard tabs, FAQ, clipboard and download fallback`);
      } finally { await context.close(); }
    }
  } finally { await browser.close(); server.close(); }
})().catch(error => { console.error(error); server.close(); process.exitCode = 1; });
