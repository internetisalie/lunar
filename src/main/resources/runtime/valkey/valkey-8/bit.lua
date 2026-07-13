---Bitwise operations on numbers.
---@class bit
bit = {}

---Normalize a number to the range of a signed 32-bit integer.
---@param x number
---@return number
function bit.tobit(x) end

---Convert a number to a hex string.
---@param x number
---@param n? number
---@return string
function bit.tohex(x, n) end

---Bitwise NOT.
---@param x number
---@return number
function bit.bnot(x) end

---Bitwise OR.
---@param x number
---@param ... number
---@return number
function bit.bor(x, ...) end

---Bitwise AND.
---@param x number
---@param ... number
---@return number
function bit.band(x, ...) end

---Bitwise XOR.
---@param x number
---@param ... number
---@return number
function bit.bxor(x, ...) end

---Bitwise left shift.
---@param x number
---@param n number
---@return number
function bit.lshift(x, n) end

---Bitwise logical right shift.
---@param x number
---@param n number
---@return number
function bit.rshift(x, n) end

---Bitwise arithmetic right shift.
---@param x number
---@param n number
---@return number
function bit.arshift(x, n) end

---Bitwise left rotation.
---@param x number
---@param n number
---@return number
function bit.rol(x, n) end

---Bitwise right rotation.
---@param x number
---@param n number
---@return number
function bit.ror(x, n) end

---Byte swap.
---@param x number
---@return number
function bit.bswap(x) end
