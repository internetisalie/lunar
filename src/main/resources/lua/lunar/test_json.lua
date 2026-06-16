local json = require("lunar.json")

-- Test basic types
print("Testing nil:", json.encode(nil))
print("Testing boolean true:", json.encode(true))
print("Testing boolean false:", json.encode(false))
print("Testing number:", json.encode(42))
print("Testing string:", json.encode("hello world"))
print("Testing string with quote:", json.encode('he said "hello"'))
print("Testing string with newline:", json.encode("line1\nline2"))

-- Test arrays
print("Testing empty array:", json.encode({}))
print("Testing simple array:", json.encode({1, 2, 3}))
print("Testing mixed array:", json.encode({1, "hello", true, nil}))

-- Test objects
print("Testing empty object:", json.encode({}))
print("Testing simple object:", json.encode({name = "John", age = 30}))
print("Testing nested object:", json.encode({person = {name = "John", age = 30}, city = "NYC"}))

-- Test mixed (array part ignored in object encoding)
print("Testing array-like with holes:", json.encode({[1] = "a", [3] = "c"}))