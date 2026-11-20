"""Точка входа для Android: запускает неизменённое ядро Flowseal через его же main()."""
import sys


def run(port, secret, log_file, extra_args=()):
    sys.argv = [
        "tg_ws_proxy",
        "--host", "127.0.0.1",
        "--port", str(port),
        "--secret", secret,
        "--log-file", log_file,
        *extra_args,
    ]
    from proxy.tg_ws_proxy import main
    main()
