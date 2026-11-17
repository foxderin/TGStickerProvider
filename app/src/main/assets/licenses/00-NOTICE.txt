# Third-party notices: FFmpeg video integration

This source project is GPL-3.0 (see LICENSE). The optional APNG/libvpx integration uses
`io.github.maitrungduc1410:ffmpeg-kit-video:6.0.6`, published as LGPL v3.0. The inspected
arm64 binary identifies LGPL version 3 or later, enables libvpx and does not enable
`--enable-gpl`. This does not waive the licenses of its bundled components.

- Publisher release: https://github.com/maitrungduc1410/ffmpeg-kit/releases/tag/android-6.0.6
- Publisher source tag commit: `998a26054f430e07ee1b7f4ff9dee1e2ad987d7e`
- Maven AAR SHA-256: `2603a45d9595f0f3f4b36614404335b8b724f2d0e3a107295ad4da7ebd422bbe`
- Artifact: https://repo.maven.apache.org/maven2/io/github/maitrungduc1410/ffmpeg-kit-video/6.0.6/

Copies of the publisher's LGPLv3/GPLv3 text and upstream pinned-source notices are in
`app/src/main/assets/licenses/`, accessible from the app's Third-party licenses button.
`sources.json` records provenance and hashes, including explicitly extracted notice headers.
The notices cover FFmpegKit, FFmpeg, libvpx (including PATENTS/AUTHORS), fontconfig,
FreeType, FriBidi, libass, libiconv, Theora, Vorbis, WebP, dav1d, Kvazaar, Snappy, zimg,
and downloaded transitive/build-source notices. smart-exception-java/common 0.2.1 use the
shared project's BSD-3-Clause notice. Included transitive notices are a source-pin inventory,
not an assertion that every named component is present in the Maven binary.

## Distribution hold: unresolved provenance/notice coverage

Do not publish the new APK as redistribution-compliant based only on this file:

1. The exact NDK provenance and complete libc++/libc++abi/compiler-runtime notices for
   `libc++_shared.so` have not been verified. The tagged workflow mentions r27c but is
   arm64-only, whereas the Maven AAR contains another ABI as well.
2. Pinned libuuid's COPYING refers to a missing BSD-3 notice; exact source-header coverage
   still needs resolving.
3. Tagged source pins have not been proven to reproduce the Maven binary. Full corresponding
   source, modifications, build/relink instructions, applicable installation information and
   exhaustive embedded per-file notices have not been verified. A source link alone is not
   a substitute for those obligations where applicable.

No new license agreement was accepted in obtaining or inspecting these artifacts. Local
build verification is not a legal-compliance certification or permission to publish.

The libvpx/APNG, source-order and source-quality ideas were informed by ACaiCat's fork:
https://github.com/ACaiCat/TGStickerProvider/tree/325913a3dc76369d9f207826ea7d750474f37446
Our implementation retains the project's content-based conversion receipts and atomic
per-file publication rather than adopting that fork's fixed staging directory/32-worker design.
