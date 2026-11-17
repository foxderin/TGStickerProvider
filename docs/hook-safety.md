# Telegram Hook export safety

## Scope and compatibility

The Hook remains a polling exporter, not a hook on Telegram download callbacks. It runs once in the host's main process, retries initialization and scans every 40 seconds. No client-version compatibility claim follows from the recommended Xposed scope list. The supported database adapter is `files/cache4.db` plus `files/account<digits>/cache4.db`, table `stickers_v2` with `id` and `data` columns. Missing schemas are reported, not guessed. Context-derived paths support the current Android user; external cache directories and the known NekoX `cache_path/caches` preference are searched. No private client API is called reflectively.

## Parsing and database lifetime

Database connections and cursors are closed on all normal exception paths; one database/account failure does not skip subsequent accounts. A cursor-local 16 MiB window replaces the previous global 100 MiB CursorWindow override. Rows above 8 MiB are explicitly skipped and logged, and at most 10,000 sticker sets are accepted in a row. These are safety limits, not a guarantee that arbitrarily large collections can be imported. The database hash column is not a stable identity and is not used for deduplication.

Both a single serialized messages.StickerSet and a count-prefixed list are accepted. The whole row must parse successfully, have no trailing bytes, and contain real sets/documents rather than NotModified placeholders. TL objects have no independent length envelope: an unknown constructor or malformed field rejects the row instead of attempting to continue from an unknown offset. Empty packs do not index documents[0]. Unsupported source MIME types remain unsupported; this does not add TGS conversion.

## Atomic files and cooperative locking

All cooperating new Hook writers use a JVM monitor and an OS file lock for an export transaction. A busy lock, inaccessible shared file or unsupported atomic rename is an explicit failure; the exporter retries on the next scan rather than silently writing without protection. This does not override Android scoped-storage ownership rules or coordinate old module versions that do not use the lock. Explicit cache deletion uses the same lock; clearing all exports retains the lock inode and its ancestor directories so a second writer cannot lock a replacement file. Failed cleanup is reported, including partial cleanup.

Source media is copied into unique same-directory temporary files. Streaming SHA-256 plus size, modification time and available file identity are checked before/after copying. Successful publication is an atomic replacement. An existing but truncated/different export is repaired when its source is available. If the Telegram source itself is stable but invalid, file consistency checks cannot establish media decodability. Files removed from Telegram cache cannot be reconstructed.

Metadata is atomically published last and only if bytes changed; a failed media export does not publish new metadata for that pack. Individual source files are atomic, but the directory is not a generation-based all-or-nothing snapshot. Previously published valid files are retained after a later failure. Readers may observe mixed generations; conversion still checks its own source fingerprints before publication. Killing a process can leave ignored temporary files; there is no destructive startup sweep of shared directories.

## Stable metadata, nondestructive migration

Metadata is named `pack_<64-bit set ID>.stickerData.txt.jpg` rather than using the mutable 32-bit content hash. Its first four lines and following sticker-ID order stay backward compatible. Source/output directories and saved UI preferences continue to use the existing short name, avoiding a destructive directory migration. Short names are restricted to safe ASCII basenames, titles are normalized to a single line.

The app prefers stable metadata over retained legacy hash files and shows one row per short-name directory. Old files are not deleted, including data from other accounts/clients. Legacy records with no new counterpart remain visible. Explicit user deletion of a pack removes its shadowed legacy metadata as well; this is separate from automatic migration. Renamed packs can leave old directories; removed stickers/packs are not automatically purged. Metadata-only migration is not a full provenance-aware multi-account catalog. High and low source variants are both retained when available; the conversion preference decides which to use. The exported total counts unique sticker IDs, while high/low counts report available variants and can overlap.

## Verification limits

Build/JVM checks cover parser boundaries and publication mechanics. Actual Telegram versions, Android scoped storage across different app owners, SQLite WAL during host writes, and device process lifecycle still require device verification. No benchmark or broad client compatibility guarantee is implied. CI remains manual-only and these changes do not initiate a release.
