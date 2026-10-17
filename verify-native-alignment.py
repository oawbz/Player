#!/usr/bin/env python3
"""Inspect ELF program headers in the APK; never patch a prebuilt binary."""
import struct
import sys
import zipfile

with zipfile.ZipFile(sys.argv[1]) as apk:
    for name in sorted(apk.namelist()):
        if not name.endswith('.so'):
            continue
        data = apk.read(name)
        is64 = data[4] == 2
        endian = '<' if data[5] == 1 else '>'
        phoff = struct.unpack_from(endian + ('Q' if is64 else 'I'), data, 32 if is64 else 28)[0]
        entsize, count = struct.unpack_from(endian + 'HH', data, 54 if is64 else 42)
        loads, relro = [], []
        for index in range(count):
            row = struct.unpack_from(endian + ('IIQQQQQQ' if is64 else 'IIIIIIII'), data, phoff + index * entsize)
            kind = row[0]
            address, size, align = (row[3], row[6], row[7]) if is64 else (row[2], row[5], row[7])
            if kind == 1:
                loads.append(align)
            elif kind == 0x6474e552:
                relro.append((address + size) % 16384 == 0)
        aligned = bool(loads) and min(loads) >= 16384 and all(relro)
        print(name, '16KB=' + str(aligned), 'LOAD=' + str(loads), 'RELRO=' + str(all(relro)))
        assert aligned, name + ': native library must have 16 KB LOAD and RELRO alignment'
