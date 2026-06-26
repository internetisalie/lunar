from PIL import Image
import sys

img = Image.open(sys.argv[1])
w, h = img.size
print(f"Size: {w}x{h}")
