---A Valkey compatibility alias for the `redis` API. All members mirror `redis.*`.
---@class server : redis
---@field LOG_DEBUG number
---@field LOG_VERBOSE number
---@field LOG_NOTICE number
---@field LOG_WARNING number
---@field REPL_ALL number
---@field REPL_AOF number
---@field REPL_REPLICA number
---@field REPL_NONE number
---@field REDIS_VERSION string
---@field REDIS_VERSION_NUM number
server = {}

---@type number
server.LOG_DEBUG = 0

---@type number
server.LOG_VERBOSE = 0

---@type number
server.LOG_NOTICE = 0

---@type number
server.LOG_WARNING = 0

---@type number
server.REPL_ALL = 0

---@type number
server.REPL_AOF = 0

---@type number
server.REPL_REPLICA = 0

---@type number
server.REPL_NONE = 0

---@type string
server.REDIS_VERSION = ""

---@type number
server.REDIS_VERSION_NUM = 0

---Executes a Redis command. If the command fails, it raises a Lua error that terminates the script.
---@param command string
---@param ... any
---@return any
function server.call(command, ...) end

---Executes a Redis command but catches errors. Instead of raising an exception, it returns a table with an err field containing the error message.
---@param command string
---@param ... any
---@return any
function server.pcall(command, ...) end

---Returns a table {err = message} to signal an error to the client.
---@param message string
---@return table
function server.error_reply(message) end

---Returns a table {ok = message} to signal a status (like "OK").
---@param message string
---@return table
function server.status_reply(message) end

---Returns the SHA1 hexadecimal digest of the input.
---@param string string
---@return string
function server.sha1hex(string) end

---Checks if the current user has ACL permissions for the command.
---@param command string
---@param ... any
---@return boolean
function server.acl_check_cmd(command, ...) end

---Writes to the Redis server log.
---@param level number
---@param message string
function server.log(level, message) end

---Switches between RESP2 (default) and RESP3 (version 3) for command replies.
---@param version number
function server.setresp(version) end

---Controls how write effects are propagated.
---@param mode number
function server.set_repl(mode) end

---Used with the Redis Lua debugger (redis-cli --ldb).
function server.breakpoint() end

---Used with the Redis Lua debugger (redis-cli --ldb).
---@param x any
function server.debug(x) end
