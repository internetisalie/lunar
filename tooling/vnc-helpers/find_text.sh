python3 - << 'PY'
from PIL import Image
import sys

img = Image.open("/tmp/ide_screen9.png")
w, h = img.size
pixels = img.load()

# Look for the color of the active tab vs inactive.
# Active tab "Paid license" has a lighter gray background than the rest.
# Let's find the text "Start trial". 
PY
