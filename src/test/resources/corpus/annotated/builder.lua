--- The BUG-473 reproduction shape, as an on-disk corpus fixture.
---
--- A `---@class` tag plus 40 colon-call sites against it. Removing the tag makes the same file
--- cost a small fraction of this one: the tag is what connects the type graph enough for the
--- walk roots to be deep, which is the whole of BUG-473.
---
--- The call-site count is chosen, not arbitrary — see LuaAnnotatedFixtureSweepTest.

---@class LunarFixtureBuilder
---@field name string
local LunarFixtureBuilder = {}

---@param name string
---@return LunarFixtureBuilder
function LunarFixtureBuilder:setName(name)
  self.name = name
  return self
end

local builder = LunarFixtureBuilder
builder:setName("field0")
builder:setName("field1")
builder:setName("field2")
builder:setName("field3")
builder:setName("field4")
builder:setName("field5")
builder:setName("field6")
builder:setName("field7")
builder:setName("field8")
builder:setName("field9")
builder:setName("field10")
builder:setName("field11")
builder:setName("field12")
builder:setName("field13")
builder:setName("field14")
builder:setName("field15")
builder:setName("field16")
builder:setName("field17")
builder:setName("field18")
builder:setName("field19")
builder:setName("field20")
builder:setName("field21")
builder:setName("field22")
builder:setName("field23")
builder:setName("field24")
builder:setName("field25")
builder:setName("field26")
builder:setName("field27")
builder:setName("field28")
builder:setName("field29")
builder:setName("field30")
builder:setName("field31")
builder:setName("field32")
builder:setName("field33")
builder:setName("field34")
builder:setName("field35")
builder:setName("field36")
builder:setName("field37")
builder:setName("field38")
builder:setName("field39")
