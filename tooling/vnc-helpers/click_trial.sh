export DISPLAY=:99
for x in 960 970 980 990 1000 1010 1020; do
    xdotool mousemove $x 320 click 1
    sleep 0.2
done
scrot /tmp/ide_screen8.png
