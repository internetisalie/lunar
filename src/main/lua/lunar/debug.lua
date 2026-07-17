--- De-schedule the thread for the requested number of seconds
local function sleep(sec)
    local socket = require "socket"
    socket.select(nil, nil, sec)
end

--- Connect the debugger to the listening remote connection
local function start()
    local debugger_package = os.getenv("LUNAR_DEBUGGER_PACKAGE")
    local debugger = require(debugger_package)

    local host = os.getenv("MOBDEBUG_HOST") or "localhost"
    local port = tonumber(os.getenv("MOBDEBUG_PORT")) or 8172

    local RETRY_COUNT = 5
    for i = 1, RETRY_COUNT do
        if debugger.start(host, port) then break
        elseif i == RETRY_COUNT then
            os.exit(1)
        end
        sleep(1.0)
    end
end

return {
    start = start,
}