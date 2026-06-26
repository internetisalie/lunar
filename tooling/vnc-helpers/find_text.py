from PIL import Image
import sys

img = Image.open(sys.argv[1])
w, h = img.size
pixels = img.load()

# Let's just find the bounding box of the "Start trial" box by looking for the border color or text color.
# The "Paid license" box is active. "Start trial" is next to it.
# Instead of complex vision, I will just dump a horizontal slice and see where the lines are.
slice_y = 320
for x in range(800, 1200):
    print(x, pixels[x, slice_y])
