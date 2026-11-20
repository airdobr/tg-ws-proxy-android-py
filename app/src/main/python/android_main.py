"""Точка входа для Android: запускает неизменённое ядро Flowseal через его же main()."""
import sys


def run(argv, log_file):
    sys.argv = ["tg_ws_proxy", *list(argv), "--log-file", log_file]
    from proxy.tg_ws_proxy import main
    main()
