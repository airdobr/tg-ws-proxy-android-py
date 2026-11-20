# TG WS Proxy (Android, Python-ядро)

Android-обёртка над [Flowseal/tg-ws-proxy](https://github.com/Flowseal/tg-ws-proxy).
Ядро (`proxy/`) запускается без изменений через Chaquopy (Python 3.11).

## Скачать
Последний APK: [Releases](https://github.com/airdobr/tg-ws-proxy-android-py/releases/latest).
Приложение само сообщает о новой версии на главном экране.

## Как приходят обновления
GitHub Actions раз в сутки проверяет последний релиз Flowseal. Если он новее
`UPSTREAM_VERSION`, workflow копирует ядро (`scripts/sync_upstream.sh`), поднимает
версию в `VERSION`, ставит тег и выкладывает подписанный APK в Releases.

Ручной релиз: Actions → Build & release → Run workflow (галочка force), либо `git tag vX.Y.Z && git push --tags`.

## Сборка локально
JDK 17, Android SDK 35, Gradle 8.7: `gradle :app:assembleRelease`.
Без переменных KEYSTORE_* APK подписывается отладочным ключом.
