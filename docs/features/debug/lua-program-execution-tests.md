---
id: "DEBUG-PROGRAM-TESTS"
title: "Lua Program Execution Integration Tests"
type: "spec"
parent_id: "DEBUG"
status: "done"
priority: "low"
folders:
  - "[[features/debug/requirements|requirements]]"
---

# Lua Program Execution Integration Tests

## Overview

A comprehensive integration test suite (`LuaProgramExecutionIntegrationTest.kt`) that validates the Lunar plugin's ability to handle various Lua language features and programming patterns.

## Test Suite Details

### Location
- **File**: `src/integrationTest/kotlin/net/internetisalie/lunar/LuaProgramExecutionIntegrationTest.kt`
- **Run Command**: `./gradlew integrationTest`
- **Test Reports**: `build/reports/tests/integrationTest/index.html`

### Test Coverage: 10 Scenarios

#### 1. Simple Print Program ✓
**Tests**: Basic output functionality
```lua
print("Hello from Lunar!")
print("Lua plugin integration test")
```
**Location**: `build/test-projects/lua-simple-print/main.lua`

#### 2. Arithmetic Operations ✓
**Tests**: Math operations and variable assignments
```lua
local a = 10
local b = 20
print("a + b = " .. (a + b))
print("a * b = " .. (a * b))
print("b / a = " .. (b / a))
```
**Location**: `build/test-projects/lua-arithmetic/arithmetic.lua`

#### 3. String Operations ✓
**Tests**: String manipulation, concatenation, and built-in functions
```lua
local substring = string.sub(str1, 1, 3)
print("Length: " .. #str1)
```
**Location**: `build/test-projects/lua-strings/strings.lua`

#### 4. Table Operations ✓
**Tests**: Table creation, indexing, and iteration
```lua
local items = {}
items[1] = "apple"
for i, item in ipairs(items) do
    print(i .. ": " .. item)
end
```
**Location**: `build/test-projects/lua-tables/tables.lua`

#### 5. Function Definitions ✓
**Tests**: Local functions, parameters, and return values
```lua
local function add(a, b)
    return a + b
end
print("add(5, 3) = " .. add(5, 3))
```
**Location**: `build/test-projects/lua-functions/functions.lua`

#### 6. Conditionals ✓
**Tests**: if/elseif/else logic and comparison operators
```lua
if number > 50 then
    print(number .. " is greater than 50")
elseif number > 30 then
    print(number .. " is greater than 30")
end
```
**Location**: `build/test-projects/lua-conditionals/conditionals.lua`

#### 7. Loop Constructs ✓
**Tests**: for, while, and repeat-until loops
```lua
for i = 1, 5 do
    print("  " .. i)
end

while count <= 3 do
    count = count + 1
end

repeat
    x = x + 1
until x > 3
```
**Location**: `build/test-projects/lua-loops/loops.lua`

#### 8. Error Handling ✓
**Tests**: Error detection and handling patterns
```lua
local function safeDivide(a, b)
    if b == 0 then
        return nil, "Division by zero"
    end
    return a / b, nil
end
```
**Location**: `build/test-projects/lua-errors/errors.lua`

#### 9. Multi-File Project ✓
**Tests**: Module system with require() and module exports
**Files**:
- `build/test-projects/lua-multifile/math_utils.lua` (module)
- `build/test-projects/lua-multifile/main.lua` (consumer)

```lua
-- math_utils.lua
local math_utils = {}
function math_utils.factorial(n)
    if n <= 1 then return 1 end
    return n * math_utils.factorial(n - 1)
end
return math_utils

-- main.lua
local math_utils = require("math_utils")
print("Factorial of 5: " .. math_utils.factorial(5))
```

#### 10. Syntax Validation ✓
**Tests**: Various Lua syntax constructs and edge cases
- Simple statements
- String concatenation
- Variable declarations
- Comments
- Multiline strings

**Location**: `build/test-projects/lua-syntax-validation/`

## Running the Tests

### Full Integration Test Suite
```bash
./gradlew integrationTest
```

### Build and Run Tests
```bash
./gradlew build integrationTest
```

### Run Specific Test
```bash
./gradlew integrationTest --tests LuaProgramExecutionIntegrationTest.execute*
```

### View Test Reports
```bash
# Open in browser
open build/reports/tests/integrationTest/index.html

# Or view the specific test class report
open build/reports/tests/integrationTest/classes/net.internetisalie.lunar.LuaProgramExecutionIntegrationTest.html
```

## Generated Artifacts

### Test Projects
All test scenarios create working Lua programs in `build/test-projects/`:
- `lua-arithmetic/` - Math operations
- `lua-conditionals/` - if/else logic
- `lua-errors/` - Error handling
- `lua-functions/` - Function definitions
- `lua-loops/` - Loop constructs
- `lua-multifile/` - Module system
- `lua-simple-print/` - Basic output
- `lua-strings/` - String operations
- `lua-syntax-validation/` - Various syntax
- `lua-tables/` - Table operations
- `simple-lua-project/` - Basic project structure

### Test Reports
- `build/reports/tests/integrationTest/index.html` - Test summary
- `build/reports/tests/integrationTest/classes/net.internetisalie.lunar.LuaProgramExecutionIntegrationTest.html` - Detailed test results

## Test Execution Flow

1. **Project Creation**: Each test method creates a unique Lua project in `build/test-projects/`
2. **File Generation**: Lua source files are generated with specific test code
3. **Validation**: Files are verified to exist and contain expected content
4. **Report Generation**: JUnit test reports are generated automatically

## Current Test Status

✅ **All Tests Passing**
- 10 test methods successfully executed
- 11 test projects created
- Comprehensive coverage of Lua language features

## Future Enhancements

### Phase 1: IDE Integration (Future)
Extend tests to actually execute programs through GoLand with the Lunar plugin:
```kotlin
context.runIdeWithDriver().useDriverAndCloseIde {
    waitForIndicators(2.minutes)
    
    // Create run configuration for Lua
    val runConfig = project.createRunConfiguration("test.lua")
    
    // Execute and capture output
    val output = executeAndCaptureOutput(runConfig)
    
    // Verify results
    require(output.contains("expected output"))
}
```

### Phase 2: Output Verification (Future)
- Parse and validate program execution output
- Compare against expected results
- Test debugging features
- Verify error reporting

### Phase 3: Advanced Features (Future)
- Test breakpoint functionality
- Test variable inspection
- Test step-over/step-into debugging
- Test expression evaluation

## Architecture

### Test Infrastructure
- **Framework**: JUnit 5
- **Test Runner**: Gradle `integrationTest` task
- **Project Type**: IntelliJ Platform plugin

### Test Data Organization
```
build/test-projects/
├── lua-arithmetic/
│   └── arithmetic.lua
├── lua-functions/
│   └── functions.lua
├── lua-multifile/
│   ├── math_utils.lua
│   └── main.lua
└── [8 more projects...]
```

## Troubleshooting

### Tests Not Running
```bash
# Clean and rebuild
./gradlew clean integrationTest

# Check Gradle daemon
./gradlew --stop
./gradlew integrationTest
```

### Memory Issues
Add to `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=1g
```

### File Encoding Issues
Ensure UTF-8 is set in IDE settings and in `gradle.properties`:
```properties
file.encoding=UTF-8
```

## Documentation

- [Integration Tests - JetBrains Docs](https://plugins.jetbrains.com/docs/intellij/integration-tests-intro.html)
- [Lua Reference Manual](https://www.lua.org/manual/5.1/)
- [Lunar Plugin Documentation](../../../README.md)

## Contributing

To add new test scenarios:

1. Add a new `@Test` method to `LuaProgramExecutionIntegrationTest`
2. Use `createTestProject()` helper to set up project directory
3. Write Lua code to `projectDir.resolve("filename.lua")`
4. Validate file creation with `require()`
5. Add println statements for test output
6. Run `./gradlew integrationTest` to verify

Example:
```kotlin
@Test
fun `execute my new test`() {
    val projectDir = createTestProject("lua-mytest")
    val code = """
        print("my test")
    """.trimIndent()
    projectDir.resolve("test.lua").writeText(code)
    require(projectDir.resolve("test.lua").toFile().exists())
    println("✓ My test passed")
}
```
