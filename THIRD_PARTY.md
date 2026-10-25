# Archive libraries

The app bundles the following Java libraries for offline extraction. The build downloads them from Maven Central using the URLs and SHA-256 hashes in `app/libs/dependencies.json`. JARs are ignored by Git. Android Studio runs the same verification through its pre-build task; the SDK builder and host tests also verify all libraries.

| Library | Version | License |
| --- | --- | --- |
| [Junrar](https://github.com/junrar/junrar/tree/v8.0.0) | 8.0.0 | UnRAR license |
| [Apache Commons Compress](https://commons.apache.org/proper/commons-compress/) | 1.28.0 | Apache-2.0 |
| Apache Commons IO | 2.20.0 | Apache-2.0 |
| Apache Commons Lang | 3.18.0 | Apache-2.0 |
| Apache Commons Codec | 1.19.0 | Apache-2.0 |
| [SLF4J API](https://www.slf4j.org/) | 2.0.17 | MIT |

License and notice texts are in `app/src/main/assets/third-party/` and are included in the APK. Junrar's license prohibits using its code to recreate the RAR compression algorithm; NuttyVR uses it only for extraction.

RAR test fixtures are from Junrar v8.0.0; source URLs and SHA-256 hashes are recorded in `tests/fixtures/provenance.json`. They retain the upstream UnRAR license and are used only by host tests, not bundled in the APK.
