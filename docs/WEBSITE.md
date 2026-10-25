# NuttyVR website

The website is built for GitHub Pages:

https://nuttyinc578.github.io/the-nuttyVR-file-explore/

## Source

`website/` contains buildless HTML, CSS, JavaScript and an SVG brand mark. It has no package installation or build step. Downloads go directly to the existing signed nightly APK on GitHub Releases.

The interactive file browser uses clearly labeled sample data in the visitor's browser. It does not read local files or install apps. Installation tabs, keyboard navigation, copying the ADB command, and the native FAQ accordions work independently of the APK. Optional GitHub release metadata has a timeout and a static fallback; it does not control the download link.

## Publish

`.github/workflows/pages.yml` deploys `website/` when its files change on `main`, or when run manually from Actions.

Before deploying, the workflow checks JavaScript syntax and runs Chrome checks at desktop, tablet and mobile widths. It exercises the sample browser, grid/list views, search, APK selection, keyboard tabs, FAQ expansion and copying the install command. It also checks for horizontal page overflow under the real GitHub Pages project path, and saves preview screenshots as the `website-previews` workflow artifact. Playwright is installed only in the runner's temporary directory for QA; the website has no runtime package dependency.

For the first deployment, the repository owner may need to select **Settings → Pages → Build and deployment → Source → GitHub Actions**. The connected GitHub app can push the site and workflow, but its account does not currently have repository administrator access. The standard Pages deployment token cannot enable Pages on a repository that has never enabled it.

After enabling Pages, rerun **Deploy NuttyVR website** from Actions. A successful deployment reports the live Pages URL in its environment.

Only `website/` is uploaded to Pages. Android source, build outputs and local signing files are outside that directory.

## Preview

From the repository checkout, use a local static server:

```sh
python3 -m http.server 8080 --directory website
```

Open `http://localhost:8080`. Check desktop and mobile layouts, sample folders and search, grid/list views, installation tabs, copy-command behavior, FAQ expansion and the APK download links.

## References

- [GitHub Pages custom workflows](https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages)
- [Configure Pages and first-time enablement permissions](https://github.com/actions/configure-pages/blob/main/action.yml)
