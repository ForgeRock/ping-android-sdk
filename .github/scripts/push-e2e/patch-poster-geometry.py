#!/usr/bin/env python3
#
# Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
#
# This software may be modified and distributed under the terms
# of the MIT license. See the LICENSE file for details.
#

# Patch the emulator's virtual-scene poster geometry so the `table` poster sits
# flat, centered, and close to the camera — the push-registration QR must fill
# the scanner's view for the sample app's ML-Kit barcode analyzer to decode it.
#
# The emulator reads Toren1BD.posters from ANDROID_HOME/emulator/resources at
# boot; the workflow backs the file up and runs this script before the flows.
#
# Only the `poster table` block's position/rotation are rewritten; every other
# line (including the `wall` poster) is preserved verbatim.

import os
import re
import sys

sdk_root = (
    os.environ.get("ANDROID_HOME")
    or os.environ.get("ANDROID_SDK_ROOT")
    or os.path.expanduser("~/Library/Android/sdk")
)
path = os.path.join(sdk_root, "emulator", "resources", "Toren1BD.posters")

content = open(path).read()

block = re.search(r"poster table\n(.*?)(?:\n\n|\Z)", content, re.S)
if not block:
    sys.exit("poster table block not found")

new_block = "poster table\nsize 2 2\nposition 0 0 -1.0\nrotation 0 0 0\n"
content = content[: block.start()] + new_block + content[block.end() :]

open(path, "w").write(content)
print(f"patched {path}: table poster 2x2 at position 0 0 -1.0, rotation 0 0 0")
