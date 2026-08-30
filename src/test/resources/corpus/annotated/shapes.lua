--- The LuaCATS tag vocabulary the corpus has never carried.
---
--- Deliberately few call sites: this file exists so the sweep's parse, lex and highlight passes
--- see `@class` inheritance, `@field`, `@alias`, `@param`, `@return` and `@type` at all — not to
--- carry the BUG-473 budget, which `builder.lua` does.

---@alias LunarFixtureUnit "px" | "em"

---@class LunarFixtureShape
---@field width number
---@field unit LunarFixtureUnit
local LunarFixtureShape = {}

---@param width number
---@param unit LunarFixtureUnit
---@return LunarFixtureShape
function LunarFixtureShape.of(width, unit)
  return { width = width, unit = unit }
end

---@class LunarFixtureBox : LunarFixtureShape
---@field height number
local LunarFixtureBox = {}

---@return number
function LunarFixtureBox:area()
  return self.width * self.height
end

---@type LunarFixtureShape
local defaultShape = LunarFixtureShape.of(1, "px")

return {
  shape = defaultShape,
  box = LunarFixtureBox,
}
