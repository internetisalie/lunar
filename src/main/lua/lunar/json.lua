--- Encodes Lua values to JSON strings.
-- Supports strings, numbers, booleans, nil, and tables (as arrays or objects).
-- @param value The Lua value to encode
-- @return JSON string representation
local function encode(value)
    local t = type(value)
    
    if t == "nil" then
        return "null"
    elseif t == "boolean" then
        return tostring(value)
    elseif t == "number" then
        -- Handle special float values
        if value ~= value then  -- NaN
            return "null"
        elseif value == math.huge or value == -math.huge then  -- Infinity
            return "null"
        end
        return tostring(value)
    elseif t == "string" then
        -- Escape special characters
        return '"' .. value:gsub('["\\]', '\\%1')
                       :gsub('%z', '\\u0000')
                       :gsub('%c', function(c) 
                           return string.format('\\u%04x', string.byte(c))
                       end) .. '"'
     elseif t == "table" then
         -- Check if it's an array-like table (consecutive integer keys from 1)
         local is_array = true
         local max_index = 0
         for k, v in pairs(value) do
             if type(k) ~= "number" or k < 1 or math.floor(k) ~= k then
                 is_array = false
                 break
             end
             if k > max_index then max_index = k end
         end
         
         if is_array and max_index > 0 then
             -- Array-like table
             local elements = {}
             for i = 1, max_index do
                 local v = value[i]
                 if v == nil then
                     elements[i] = "null"  -- JSON doesn't have undefined, use null for missing values
                 else
                     elements[i] = encode(v)
                 end
             end
             return "[" .. table.concat(elements, ",") .. "]"
         else
             -- Object-like table
             local kv_pairs = {}
             for k, v in pairs(value) do
                 if type(k) == "string" then
                     table.insert(kv_pairs, encode(k) .. ":" .. encode(v))
                 end
                 -- Skip non-string keys as they're not valid in JSON objects
             end
             return "{" .. table.concat(kv_pairs, ",") .. "}"
         end
    else
        error("Unsupported type for JSON encoding: " .. t)
    end
end

return {
    encode = encode
}