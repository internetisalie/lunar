export DISPLAY=:99
for x in 1280 1300 1320 1340; do
    for y in 730 740 750 760; do
        xdotool mousemove $x $y click 1
        sleep 0.1
    done
done
sleep 1
scrot /tmp/ide_screen12.png
