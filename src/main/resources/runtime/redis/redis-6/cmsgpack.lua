---MessagePack encoding/decoding.
---@class cmsgpack
cmsgpack = {}

---Pack a Lua table into a MessagePack string.
---@param table table
---@return string
function cmsgpack.pack(table) end

---Unpack a MessagePack string into a Lua table.
---@param string string
---@return table
function cmsgpack.unpack(string) end
