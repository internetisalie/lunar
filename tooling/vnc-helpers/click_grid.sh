export DISPLAY=:99
for x in 980 1000 1020 1040 1060; do
    for y in 310 320 330 340; do
        xdotool mousemove $x $y click 1
        sleep 0.1
    done
done
scrot /tmp/ide_screen10.png
