#!/usr/bin/env python3
"""pack.py BASE_APK CLASSES_DEX OUT_APK
把 aapt2 生成的资源 APK 和 dex 合成一个 APK，并对 resources.arsc 做 4 字节对齐
（API 30+ 强制要求，等价于 zipalign 的那一步）。
"""
import sys, os, struct, zipfile

base, dex, out = sys.argv[1], sys.argv[2], sys.argv[3]

entries = []
with zipfile.ZipFile(base) as z:
    for info in z.infolist():
        if info.filename.endswith("/"):
            continue
        entries.append((info.filename, z.read(info.filename)))

dexdata = open(dex, "rb").read()

# 排序：清单、资源表在前，dex 最后
order = {"AndroidManifest.xml": 0, "resources.arsc": 1}
entries.sort(key=lambda e: (order.get(e[0], 2), e[0]))
entries.append(("classes.dex", dexdata))

ALIGN = 4
tmp = out + ".tmp"
with open(tmp, "wb") as f, zipfile.ZipFile(f, "w", zipfile.ZIP_DEFLATED) as z:
    for name, data in entries:
        store = name in ("resources.arsc",) or name.endswith(".so")
        zi = zipfile.ZipInfo(name, date_time=(2024, 1, 1, 0, 0, 0))
        zi.external_attr = 0o644 << 16
        zi.compress_type = zipfile.ZIP_STORED if store else zipfile.ZIP_DEFLATED
        if store:
            pos = z.fp.tell()
            base_len = 30 + len(name.encode("utf-8"))
            need = (ALIGN - ((pos + base_len + 4) % ALIGN)) % ALIGN
            extra_len = 4 + need
            pad = extra_len - 4
            zi.extra = b"\xd9\x35" + struct.pack("<H", pad) + b"\x00" * pad
        z.writestr(zi, data)

os.replace(tmp, out)
print("packed", out, os.path.getsize(out), "bytes")
with zipfile.ZipFile(out) as z:
    bad = z.testzip()
    print("zip test:", "OK" if bad is None else "CORRUPT " + str(bad))
    for i in z.infolist():
        off = i.header_offset + 30 + len(i.filename.encode()) + len(i.extra)
        print(f"  {i.filename:28} {i.compress_type and 'deflate' or 'stored ':8} "
              f"size={i.file_size:9} dataoff={off} align4={off % 4 == 0}")
