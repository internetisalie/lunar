from vncdotool import api
import sys

client = api.connect('127.0.0.1::5900', password='vncpass')
client.timeout = 5
if sys.argv[1] == 'click':
    x = int(sys.argv[2])
    y = int(sys.argv[3])
    client.mouseMove(x, y)
    client.mousePress(1)
    client.captureScreen(sys.argv[4])
elif sys.argv[1] == 'capture':
    client.captureScreen(sys.argv[2])
elif sys.argv[1] == 'key':
    client.keyPress(sys.argv[2])
    client.captureScreen(sys.argv[3])
