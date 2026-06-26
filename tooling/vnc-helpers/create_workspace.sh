#!/bin/bash
set -e
WORKSPACE="/home/mini/test-workspace"
rm -rf "$WORKSPACE"
mkdir -p "$WORKSPACE/rocks/rock-a"
mkdir -p "$WORKSPACE/rocks/rock-b"
mkdir -p "$WORKSPACE/rocks/rock-c"

cat << 'SPEC' > "$WORKSPACE/rocks/rock-a/rock-a-1.0-1.rockspec"
package = "rock-a"
version = "1.0-1"
source = { url = "git://example.com/a.git" }
description = { summary = "Rock A" }
dependencies = { "lua >= 5.1" }
build = { type = "builtin", modules = { rock_a = "rock_a.lua" } }
SPEC
echo "print('rock-a')" > "$WORKSPACE/rocks/rock-a/rock_a.lua"

cat << 'SPEC' > "$WORKSPACE/rocks/rock-b/rock-b-1.0-1.rockspec"
package = "rock-b"
version = "1.0-1"
source = { url = "git://example.com/b.git" }
description = { summary = "Rock B" }
dependencies = { "lua >= 5.1", "rock-a" }
build = { type = "builtin", modules = { rock_b = "rock_b.lua" } }
SPEC
echo "print('rock-b')" > "$WORKSPACE/rocks/rock-b/rock_b.lua"

cat << 'SPEC' > "$WORKSPACE/rocks/rock-c/rock-c-1.0-1.rockspec"
package = "rock-c"
version = "1.0-1"
source = { url = "git://example.com/c.git" }
description = { summary = "Rock C" }
dependencies = { "lua >= 5.1", "rock-b" }
build = { type = "builtin", modules = { rock_c = "rock_c.lua" } }
SPEC
echo "print('rock-c')" > "$WORKSPACE/rocks/rock-c/rock_c.lua"

echo "Workspace created at $WORKSPACE"
