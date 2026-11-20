# TG WS Proxy (Android, Python-ядро)

Android-обёртка над [Flowseal/tg-ws-proxy](https://github.com/Flowseal/tg-ws-proxy).
Ядро (`proxy/`) запускается без изменений через Chaquopy, поэтому обновления
берутся прямо из оригинала: `scripts/sync_upstream.sh [тег]`.

Версия ядра: см. `UPSTREAM_VERSION`.

## Сборка
`gradle :app:assembleRelease` (JDK 17, Android SDK 35). Подписанный APK берите в Releases.
