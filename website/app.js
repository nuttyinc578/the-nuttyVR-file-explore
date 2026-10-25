"use strict";

(() => {
  const folders = {
    root: { name: "All files", files: [
      { name: "Downloads", kind: "folder", note: "Folder", target: "downloads" },
      { name: "Pictures", kind: "folder", note: "Folder", target: "pictures" },
      { name: "Documents", kind: "folder", note: "Folder", target: "documents" },
      { name: "relax_360.mp4", kind: "video", note: "42.6 MB" },
      { name: "Space notes.txt", kind: "file", note: "2.4 KB" },
      { name: "your-next-app.apk", kind: "apk", note: "Android app" }
    ] },
    downloads: { name: "Downloads", files: [
      { name: "your-next-app.apk", kind: "apk", note: "Android app" },
      { name: "Getting started.txt", kind: "file", note: "4.1 KB" },
      { name: "forest_walk.mp4", kind: "video", note: "28.4 MB" }
    ] },
    pictures: { name: "Pictures", files: [
      { name: "Golden hour.jpg", kind: "image", note: "3.2 MB" },
      { name: "A little space.png", kind: "image", note: "1.8 MB" },
      { name: "Weekend.jpg", kind: "image", note: "2.6 MB" }
    ] },
    documents: { name: "Documents", files: [
      { name: "Space notes.txt", kind: "file", note: "2.4 KB" },
      { name: "Ideas.md", kind: "file", note: "1.1 KB" },
      { name: "Read me.txt", kind: "file", note: "3.7 KB" }
    ] }
  };
  const grid = document.querySelector("[data-demo-grid]");
  const search = document.querySelector("#demo-search");
  const path = document.querySelector("[data-demo-path]");
  const count = document.querySelector("[data-demo-count]");
  const hint = document.querySelector("[data-demo-hint]");
  const announcement = document.querySelector("[data-demo-announcement]");
  const back = document.querySelector("[data-demo-back]");
  let place = "root";
  let selected = "";

  function icon(kind) {
    const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    svg.setAttribute("class", "file-icon");
    svg.setAttribute("aria-hidden", "true");
    const use = document.createElementNS("http://www.w3.org/2000/svg", "use");
    use.setAttribute("href", `#i-${kind}`);
    svg.append(use);
    return svg;
  }

  function render(announce = false) {
    const folder = folders[place];
    const term = search.value.trim().toLocaleLowerCase();
    const files = folder.files.filter(file => file.name.toLocaleLowerCase().includes(term));
    grid.replaceChildren();
    for (const file of files) {
      const item = document.createElement("button");
      item.type = "button";
      item.className = `demo-file ${file.kind}${selected === file.name ? " selected" : ""}`;
      item.setAttribute("aria-label", file.target ? `Open sample ${file.name} folder` : `Select sample file ${file.name}`);
      if (!file.target) item.setAttribute("aria-pressed", String(selected === file.name));
      const name = document.createElement("strong");
      name.textContent = file.name;
      const detail = document.createElement("small");
      detail.textContent = file.note;
      item.append(icon(file.kind), name, detail);
      item.addEventListener("click", () => {
        if (file.target) navigate(file.target);
        else {
          selected = selected === file.name ? "" : file.name;
          render();
          const position = files.indexOf(file);
          grid.children[position]?.focus({ preventScroll: true });
          hint.textContent = selected ? (file.kind === "apk" ? "In the app: install, then Open app." : `${file.name} · sample file`) : "Try a folder or change the view.";
          announcement.textContent = selected ? `${file.name} selected. This is a sample file.` : "Selection cleared.";
        }
      });
      grid.append(item);
    }
    if (!files.length) {
      const empty = document.createElement("p");
      empty.className = "preview-placeholder";
      empty.textContent = "No matching sample files.";
      grid.append(empty);
    }
    path.textContent = folder.name;
    count.textContent = `${files.length} sample ${files.length === 1 ? "item" : "items"}`;
    back.disabled = place === "root";
    document.querySelectorAll("[data-place]").forEach(button => {
      const active = button.dataset.place === place;
      button.classList.toggle("active", active);
      button.setAttribute("aria-pressed", String(active));
    });
    if (announce) announcement.textContent = `${folder.name}. ${files.length} sample ${files.length === 1 ? "item" : "items"}.`;
  }

  function navigate(next) {
    if (!Object.hasOwn(folders, next)) return;
    place = next;
    selected = "";
    search.value = "";
    hint.textContent = next === "root" ? "Try a folder or change the view." : "Sample folder · use the back arrow.";
    render(true);
  }
  document.querySelectorAll("[data-place]").forEach(button => button.addEventListener("click", () => navigate(button.dataset.place)));
  back.addEventListener("click", () => navigate("root"));
  search.addEventListener("input", () => { selected = ""; hint.textContent = "Search stays in this sample folder."; render(true); });
  document.querySelectorAll("[data-view]").forEach(button => button.addEventListener("click", () => {
    grid.classList.toggle("list-view", button.dataset.view === "list");
    document.querySelectorAll("[data-view]").forEach(other => {
      const active = other === button;
      other.classList.toggle("active", active);
      other.setAttribute("aria-pressed", String(active));
    });
    announcement.textContent = `Sample ${button.dataset.view} view selected.`;
  }));
  render();

  const tabs = [...document.querySelectorAll("[data-platform]")];
  function activateTab(tab, focus = false) {
    tabs.forEach(button => {
      const active = button === tab;
      button.setAttribute("aria-selected", String(active));
      button.tabIndex = active ? 0 : -1;
      document.getElementById(button.getAttribute("aria-controls")).hidden = !active;
    });
    if (focus) tab.focus();
  }
  tabs.forEach((tab, index) => {
    tab.addEventListener("click", () => activateTab(tab));
    tab.addEventListener("keydown", event => {
      const direction = event.key === "ArrowRight" ? 1 : event.key === "ArrowLeft" ? -1 : 0;
      if (direction) { event.preventDefault(); activateTab(tabs[(index + direction + tabs.length) % tabs.length], true); }
      else if (event.key === "Home" || event.key === "End") { event.preventDefault(); activateTab(event.key === "Home" ? tabs[0] : tabs.at(-1), true); }
    });
  });
  activateTab(tabs[0]);

  const copy = document.querySelector("[data-copy]");
  const copyStatus = document.querySelector("[data-copy-status]");
  let copyTimeout;
  copy.addEventListener("click", async () => {
    const command = document.querySelector("#install-command").textContent;
    try {
      if (!navigator.clipboard?.writeText) throw new Error("Clipboard unavailable");
      await navigator.clipboard.writeText(command);
      copyStatus.textContent = "Command copied. Ready for your terminal.";
      copy.setAttribute("aria-label", "Installation command copied");
      copy.querySelector("use").setAttribute("href", "#i-check");
    } catch {
      const selection = window.getSelection();
      const range = document.createRange();
      range.selectNodeContents(document.querySelector("#install-command"));
      selection.removeAllRanges();
      selection.addRange(range);
      copyStatus.textContent = "Command selected. Copy it with your browser’s copy action.";
    }
    clearTimeout(copyTimeout);
    copyTimeout = setTimeout(() => { copy.querySelector("use").setAttribute("href", "#i-copy"); copy.setAttribute("aria-label", "Copy APK installation command"); }, 3500);
  });

  async function showRelease() {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 4500);
    try {
      const response = await fetch("https://api.github.com/repos/nuttyinc578/the-nuttyVR-file-explore/releases/tags/nightly", { signal: controller.signal, headers: { Accept: "application/vnd.github+json" } });
      if (!response.ok) return;
      const release = await response.json();
      const apk = release.assets?.find(asset => asset.name === "NuttyVR-File-Explorer-nightly.apk" && asset.state === "uploaded");
      if (!apk || !Number.isFinite(apk.size) || apk.size < 1) return;
      const size = apk.size < 1024 * 1024 ? `${Math.round(apk.size / 1024)} KB` : `${(apk.size / (1024 * 1024)).toFixed(1)} MB`;
      const date = new Date(apk.updated_at || release.published_at);
      const updated = Number.isNaN(date.getTime()) ? "" : ` · ${new Intl.DateTimeFormat("en", { month: "short", day: "numeric" }).format(date)}`;
      document.querySelector("[data-release-meta]").textContent = `Signed nightly APK · ${size}${updated}`;
    } catch { /* The permanent APK link works independently of release metadata. */ }
    finally { clearTimeout(timeout); }
  }
  showRelease();
})();
