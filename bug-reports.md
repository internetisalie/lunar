## Walkthrough 3: Duplicate problems listed for each problem

```lua
---@param count number
---@param msg string
local function log(count, msg) end

log("many", 10)
```

Shows each problem twice:
- `"many"`: `string is not assignable to number`
- `10`: `number is not assignable to string`

## Walkthrough 4: Union Types

```lua
---@type string | number
local id = "A1"
id = 123

local x = "default" or 0
```

No inlay hint is shown for `x`.

## Walthrough 6: Return types

```lua
---@return number, string
local function getData()
    return 1, 2
end
```

Parse error at `@return number,` on the `,`: `'#', <, DASHES, NAME, '[]' or '|' expected, got ','`

## Walkthrough 7: External API Stubs

```lua
local m = require("math")
local s = m.sin(1.0)
```

No inlay hint is shown for `s`.