---
folders:
  - "[[features/target/requirements]]"
title: User Guide
---

# User Guide: Runtime Environment Configuration (TARGET)

The **Runtime Environment Configuration** feature allows you to tell the IDE exactly which Lua environment your code is targeting. This ensures that the IDE provides accurate code completion and static analysis results.

## Selecting a Target

To configure your project's target:

1. Go to **Settings | Languages & Frameworks | Lua | Project Settings**.
2. Select your **Platform** (e.g., Standard, Redis, LuaJIT).
3. Select the specific **Version** of that platform.
4. The IDE will automatically derive the appropriate **Language Level** and load the correct standard library definitions.
5. Click **Apply** or **OK**.

## Supported Platforms

- **Standard**: Official Lua versions 5.1 through 5.4 (+ future 5.5).
- **LuaJIT**: Performance-oriented Lua JIT compiler.
- **Redis**: Embedded Lua scripting environment for Redis versions 5, 6, and 7+.
- **Tarantool**: Lua-based application server and database.
- **OpenResty (NGX)**: Nginx-based web platform.
- **Pandoc**: Document converter scripting environment.

## Integration with Luacheck

If you have **Luacheck** enabled, the IDE will automatically pass the correct `--std` flag to match your selected target. For example, selecting **Redis** will allow you to use `redis.call` without "undefined global" warnings.

## Legacy Projects

Projects created with older versions of the plugin will be automatically migrated to the new Target system upon opening. Your existing language level settings will be preserved and mapped to the appropriate platform/version combination.
