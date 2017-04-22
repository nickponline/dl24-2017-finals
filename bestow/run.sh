#!/bin/bash
if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <game>" 1>&2
    exit 1
fi
game="$1"
let port=19999+game
while true; do
    ./bestow.py localhost:$port game$game.db --prometheus-port 601$game
    aplay /usr/share/sounds/purple/alert.wav
    sleep 1
done
