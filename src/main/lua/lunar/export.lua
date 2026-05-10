--- Collect the specified keys from the supplied table
--- @param source       { [string}: any] }  The values indexed by key
--- @param names        string[]            The keys from source to extract
--- @param include_nils bool                Whether to include nil values in the result
local function extract(source, names, include_nils)
    local data = {}
    for _, key in ipairs(name) do
        local value = source[key]
        if (value ~= nil) or include_nils then
            data[key] = value
        end
    end
    return data
end

--- Write some globals to stdout in JSON format
local function json(source, names, include_nils)
    local data = extract(source, names, include_nils)

    local json = require("lunar.json")
    local bytes = json.encode(data)

    print(bytes)
end

return {
    json = json,
}
