# Hook TL reader safety

The parser remains a bounded subset for locally cached sticker sets, not a general Telegram client or a claim that every fork/version is supported. Unknown constructors abort the enclosing database row; callers must not continue at an unknown byte offset or publish a partial row. `HookEntry` owns row framing and error isolation.

Structural failures throw even if the legacy `exception` parameter is false. This intentionally removes silent zero-filled/truncated records; valid existing formats retain their wire layout. Primitive reads and skip check availability; byte fields verify the whole payload and zero alignment padding before publication; strings reject invalid UTF-8. `getPosition()` includes actual payload/padding/skip consumption; `remaining()` is actual unread bytes (zero after close), never a huge fallback.

Safety bounds are explicit application policy: input <=64 MiB, a TL byte field <=0xffffff bytes, vectors <=100000 elements and <=remaining/minimum element size. Row-level limits may be stricter. These limits reject implausible allocations rather than trying to recover them. Existing constructor dispatch remains, with three current VideoSize variants added. Unsupported legacy/future variants fail closed, not guessed.

Video thumbnails are consumed and retained before reading document dc_id/attributes, and serialized symmetrically. Current definitions checked against official Telegram documentation on 2026-11-17:
- https://core.telegram.org/constructor/document
- https://core.telegram.org/constructor/videoSize
- https://core.telegram.org/constructor/videoSizeEmojiMarkup
- https://core.telegram.org/constructor/videoSizeStickerMarkup
- https://core.telegram.org/mtproto/serialize

Run `JAVA_HOME=/path/to/jdk tools/test-tl-parser.sh`. This JDK-only synthetic-fixture test covers little-endian primitives, EOF/short reads, length/padding/UTF-8 errors, count allocation bounds, current and layer146 set wrappers, all three current video thumbnail forms, their roundtrip and downstream dc_id alignment, every truncated prefix of a video document, unknown constructors, and short/long TL byte lengths. It does not replace captured database fixtures, Telegram client/fork version coverage, or real-device testing. No account/private database data is included in fixtures.
