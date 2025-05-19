local function sleep(sec)
    local socket = require "socket"
    socket.select(nil, nil, sec)
end

package.path = os.getenv("LUNAR_LUA_PATH_TEMPLATE") .. ";" .. package.path

local debugger = require(os.getenv("LUNAR_DEBUGGER_PACKAGE"))

local RETRY_COUNT = 5
for i = 1, RETRY_COUNT do
    if debugger.start() then break
    elseif i == RETRY_COUNT then
        os.exit(1)
    end
    sleep(1.0)
end
