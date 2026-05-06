-- Lunar REPL Bootstrap Script (Research Artifact)
-- This script manages the evaluation loop for the integrated IDE console.
-- It handles version compatibility (5.1-5.4) and incomplete input detection.

local function get_load()
    if _VERSION == "Lua 5.1" then
        return loadstring
    else
        return load
    end
end

local load_chunk = get_load()
local buffer = ""

-- Detection logic inspired by hoelzro/lua-repl
local function is_incomplete(err)
    if not err then return false end
    return err:match("'<eof>'$") or err:match("<eof>$")
end

local function run_repl()
    while true do
        local line = io.read()
        if not line then break end
        
        buffer = buffer .. line .. "\n"
        
        -- Attempt to compile the current buffer
        -- We try wrapping it in 'return' first to handle expressions
        local func, err = load_chunk("return " .. buffer, "=(console)")
        if not func then
            -- If 'return' failed, try compiling as a statement
            func, err = load_chunk(buffer, "=(console)")
        end

        if func then
            -- Execution successful, clear buffer
            local success, results = pcall(func)
            if success then
                -- TODO: Format results (potentially as JSON or pretty-printed)
                if results ~= nil then print(results) end
            else
                io.stderr:write("[Runtime Error] " .. tostring(results) .. "\n")
            end
            buffer = ""
        elseif is_incomplete(err) then
            -- Incomplete input, wait for more
            io.write(">>") -- Signal for more input
        else
            -- Real syntax error, report and clear buffer
            io.stderr:write("[Syntax Error] " .. tostring(err) .. "\n")
            buffer = ""
        end
    end
end

run_repl()
