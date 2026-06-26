export DISPLAY=:99
for y in 310 320 330 340 350; do
    xdotool mousemove 1000 $y click 1
    sleep 0.2
done
scrot /tmp/ide_screen9.png
