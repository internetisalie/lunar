export DISPLAY=:99
for x in 900 950 1000 1050; do
    for y in 470 480 490 500; do
        xdotool mousemove $x $y click 1
        sleep 0.1
    done
done
sleep 2
scrot /tmp/ide_screen11.png
