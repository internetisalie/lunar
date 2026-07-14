---@class redis
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
redis = {}

---Executes a Redis command. If the command fails, it raises a Lua error that terminates the script.
---@param command string
---@param ... any
---@return any
function redis.call(command, ...) end

---Executes a Redis command but catches errors. Instead of raising an exception, it returns a table with an err field containing the error message.
---@param command string
---@param ... any
---@return any|{ err: string }
function redis.pcall(command, ...) end

---Returns a table {err = message} to signal an error to the client.
---@param message string
---@return table
function redis.error_reply(message) end

---Returns a table {ok = message} to signal a status (like "OK").
---@param message string
---@return table
function redis.status_reply(message) end

---Returns the SHA1 hexadecimal digest of the input.
---@param string string
---@return string
function redis.sha1hex(string) end

---Checks if the current user has ACL permissions for the command.
---@param command string
---@param ... any
---@return boolean
function redis.acl_check_cmd(command, ...) end

---Writes to the Redis server log.
---@param level number
---@param message string
function redis.log(level, message) end

---Switches between RESP2 (default) and RESP3 (version 3) for command replies.
---@param version number
function redis.setresp(version) end

---Controls how write effects are propagated.
---@param mode number
function redis.set_repl(mode) end

---Switches the script to effects replication, returning true on the first call.
---@return boolean
function redis.replicate_commands() end

---Used with the Redis Lua debugger (redis-cli --ldb).
function redis.breakpoint() end

---Used with the Redis Lua debugger (redis-cli --ldb).
---@param x any
function redis.debug(x) end
