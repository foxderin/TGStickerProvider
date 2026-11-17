#!/usr/bin/env python3
"""Check APK native-library 16 KiB ZIP/ELF prerequisites (not a device-load test)."""
import argparse
import struct
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("apk")
args = parser.parse_args()
page = 16384
with open(args.apk, "rb") as raw, zipfile.ZipFile(args.apk) as apk:
    libraries = [item for item in apk.infolist() if item.filename.startswith("lib/") and item.filename.endswith(".so")]
    if not libraries:
        raise SystemExit("FAIL: no packaged native libraries")
    for item in libraries:
        def require(condition, message):
            if not condition:
                raise SystemExit(f"FAIL: {item.filename}: {message}")
        require(item.compress_type == zipfile.ZIP_STORED, "library must be stored uncompressed")
        raw.seek(item.header_offset)
        header = raw.read(30)
        require(len(header) == 30 and header[:4] == b"PK\x03\x04", "invalid ZIP local header")
        name_size, extra_size = struct.unpack_from("<HH", header, 26)
        offset = item.header_offset + 30 + name_size + extra_size
        require(offset % page == 0, f"ZIP payload offset {offset} is not 16 KiB aligned")
        data = apk.read(item)
        require(len(data) >= 64 and data[:4] == b"\x7fELF" and data[4:6] == b"\x02\x01", "expected ELF64 little-endian")
        require(struct.unpack_from("<H", data, 18)[0] == 183, "expected AArch64")
        phoff = struct.unpack_from("<Q", data, 32)[0]
        phsize, phcount = struct.unpack_from("<HH", data, 54)
        require(phsize >= 56 and phoff + phsize * phcount <= len(data), "invalid program headers")
        loads = 0
        for index in range(phcount):
            fields = struct.unpack_from("<IIQQQQQQ", data, phoff + index * phsize)
            if fields[0] != 1:
                continue
            loads += 1
            require(fields[7] >= page and fields[7] & (fields[7] - 1) == 0, "PT_LOAD alignment below 16 KiB")
            require((fields[2] - fields[3]) % page == 0, "PT_LOAD offset/address incongruent for 16 KiB")
        require(loads > 0, "no PT_LOAD segments")
        print(f"PASS {item.filename}: ZIP + {loads} ELF LOAD segments")
print(f"PASS {len(libraries)} native libraries; actual 16 KiB device loading remains unverified")
