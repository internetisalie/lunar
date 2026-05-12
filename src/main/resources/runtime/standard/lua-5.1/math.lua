-- MIT License
--
-- Copyright © 1994–2025 Lua.org, PUC-Rio.
--
-- Permission is hereby granted, free of charge, to any person obtaining a copy
-- of this software and associated documentation files (the "Software"), to deal
-- in the Software without restriction, including without limitation the rights
-- to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
-- copies of the Software, and to permit persons to whom the Software is
-- furnished to do so, subject to the following conditions:
--
-- The above copyright notice and this permission notice shall be included in all
-- copies or substantial portions of the Software.
--
-- THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
-- IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
-- FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
-- AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
-- LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
-- OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
-- SOFTWARE.

---@meta

---@class math
math = {}

---Returns absolute value
---@param x number
---@return number
function math.abs(x) end

---Arc cosine in radians
---@param x number
---@return number
function math.acos(x) end

---Arc sine in radians
---@param x number
---@return number
function math.asin(x) end

---Arc tangent in radians
---@param x number
---@return number
function math.atan(x) end

---Arc tangent of y/x using both arguments' signs for quadrant
---@param y number
---@param x number
---@return number
function math.atan2(y, x) end

---Smallest integer >= x
---@param x number
---@return integer
function math.ceil(x) end

---Cosine of x in radians
---@param x number
---@return number
function math.cos(x) end

---Hyperbolic cosine
---@param x number
---@return number
function math.cosh(x) end

---e raised to power x
---@param x number
---@return number
function math.exp(x) end

---Largest integer <= x
---@param x number
---@return integer
function math.floor(x) end

---Remainder of x/y (rounds towards zero)
---@param x number
---@param y number
---@return number
function math.fmod(x, y) end

---Returns m, e where x = m * 2^e and |m| in [0.5, 1)
---@param x number
---@return number m, integer e
function math.frexp(x) end

---Returns m * 2^e
---@param m number
---@param e integer
---@return number
function math.ldexp(m, e) end

---Natural logarithm
---@param x number
---@return number
function math.log(x) end

---Base-10 logarithm
---@param x number
---@return number
function math.log10(x) end

---Maximum value among arguments
---@vararg number
---@return number
function math.max(...) end

---Minimum value among arguments
---@vararg number
---@return number
function math.min(...) end

---Returns integer part and fractional part of x
---@param x number
---@return integer int, number frac
function math.modf(x) end

---x raised to power y
---@param x number
---@param y number
---@return number
function math.pow(x, y) end

---Returns uniformly distributed random number
---No args: [0,1); with m: [1,m]; with m,n: [m,n]
---@overload fun(): number
---@overload fun(m: integer): integer
---@param m? integer
---@param n? integer
---@return number|integer
function math.random(m, n) end

---Seeds pseudo-random generator
---Equal seeds produce equal sequences
---@param x integer|number
function math.randomseed(x) end

---Sine in radians
---@param x number
---@return number
function math.sin(x) end

---Hyperbolic sine
---@param x number
---@return number
function math.sinh(x) end

---Square root
---@param x number
---@return number
function math.sqrt(x) end

---Tangent in radians
---@param x number
---@return number
function math.tan(x) end

---Hyperbolic tangent
---@param x number
---@return number
function math.tanh(x) end

---Pi constant
math.pi = 3.1415926535897932384626433832795

---Infinity constant
math.huge = 1.7976931348623157e+308
