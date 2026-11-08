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

---MATH LIBRARY
---@class math
math = {}

---Returns the absolute value of x
---@param x number
---@return number
function math.abs(x) end

---Arc cosine of x, in radians
---@param x number
---@return number
function math.acos(x) end

---Arc sine of x, in radians
---@param x number
---@return number
function math.asin(x) end

---Arc tangent of x, in radians
---@param x number
---@return number
function math.atan(x) end

---Arc tangent of y/x, in radians, using the signs of both arguments to find the quadrant
---@param y number
---@param x number
---@return number
function math.atan2(y, x) end

---Smallest integral value larger than or equal to x
---@param x number
---@return integer
function math.ceil(x) end

---Cosine of x, in radians
---@param x number
---@return number
function math.cos(x) end

---Hyperbolic cosine of x
---@param x number
---@return number
function math.cosh(x) end

---Converts the angle x from radians to degrees
---@param x number
---@return number
function math.deg(x) end

---Returns e raised to the power x
---@param x number
---@return number
function math.exp(x) end

---Largest integral value smaller than or equal to x
---@param x number
---@return integer
function math.floor(x) end

---Remainder of the division of x by y that rounds the quotient towards zero
---@param x number
---@param y number
---@return number
function math.fmod(x, y) end

---Returns m and e such that x = m * 2^e, e is an integer and |m| is in [0.5, 1) (or zero when x is zero)
---@param x number
---@return number m, integer e
function math.frexp(x) end

---Returns m * 2^e (e should be an integer)
---@param m number
---@param e integer
---@return number
function math.ldexp(m, e) end

---Logarithm of x in the given base; the default base is e
---@param x number
---@param base? number
---@return number
function math.log(x, base) end

---Maximum value among its arguments
---@param x number
---@vararg number
---@return number
function math.max(x, ...) end

---Minimum value among its arguments
---@param x number
---@vararg number
---@return number
function math.min(x, ...) end

---Returns the integral part of x and the fractional part of x
---@param x number
---@return number int, number frac
function math.modf(x) end

---Returns x raised to the power y (also available as the expression x^y)
---@param x number
---@param y number
---@return number
function math.pow(x, y) end

---Converts the angle x from degrees to radians
---@param x number
---@return number
function math.rad(x) end

---Pseudo-random number
---No arguments: a real number in [0,1); with m: an integer in [1, m]; with m and n: an integer in [m, n]
---@overload fun(): number
---@overload fun(m: integer): integer
---@overload fun(m: integer, n: integer): integer
---@param m? integer
---@param n? integer
---@return number
function math.random(m, n) end

---Sets x as the seed for the pseudo-random generator; equal seeds produce equal sequences
---@param x number
function math.randomseed(x) end

---Sine of x, in radians
---@param x number
---@return number
function math.sin(x) end

---Hyperbolic sine of x
---@param x number
---@return number
function math.sinh(x) end

---Square root of x (x^0.5 also computes it)
---@param x number
---@return number
function math.sqrt(x) end

---Tangent of x, in radians
---@param x number
---@return number
function math.tan(x) end

---Hyperbolic tangent of x
---@param x number
---@return number
function math.tanh(x) end

---The value of pi
math.pi = 3.1415926535897932384626433832795

---The value HUGE_VAL, a value larger than or equal to any other numerical value
math.huge = 1.7976931348623157e+308
