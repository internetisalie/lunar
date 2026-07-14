---Fast JSON encoding/decoding.
---@class cjson
cjson = {}

---Encode a Lua table into a JSON string.
---@param table table
---@return string
function cjson.encode(table) end

---Decode a JSON string into a Lua table.
---@param string string
---@return table
function cjson.decode(string) end
