"""Точка входа для Android: запускает неизменённое ядро Flowseal через его же main()."""
import sys


def run(argv, log_file):
    sys.argv = ["tg_ws_proxy", *list(argv), "--log-file", log_file]
    from proxy.tg_ws_proxy import main
    main()


def set_no_secure(value):
    """Переключает TLS для CF-прокси/worker на ходу (при смене Wi-Fi / мобильной сети).

    Флаг proxy_config.disable_secure ядро читает при каждом новом подключении,
    а пул HTTP/2 (работает только с TLS) пересоздаём в цикле событий ядра.
    Возвращает True, если режим изменился.
    """
    import asyncio
    import logging
    import proxy.tg_ws_proxy as core
    from proxy.cf_h2 import CfH2Pool
    from proxy.config import proxy_config

    value = bool(value)
    if proxy_config.disable_secure == value:
        return False
    proxy_config.disable_secure = value
    logging.getLogger("tg-ws-proxy").info(
        "Сеть сменилась: TLS для CF %s", "выключен (порт 80)" if value else "включён (порт 443)")

    srv = core._server_instance
    if srv is None:
        return True

    async def swap_h2():
        old = core.cf_h2_pool
        core.cf_h2_pool = CfH2Pool() if proxy_config.h2_enabled else None
        if old is not None:
            try:
                await old.close()
            except Exception:
                pass

    asyncio.run_coroutine_threadsafe(swap_h2(), srv.get_loop())
    return True
