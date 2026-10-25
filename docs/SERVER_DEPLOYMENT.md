# Серверная установка Mirutex IDE

Проверено 24.10.2026: https://193.233.247.61/

Проект: /root/mirutex на сервере. Три Docker Compose сервиса healthy; restart unless-stopped задан в compose.override.yaml. Локальный frontend доступен серверу на 127.0.0.1:5173.

Nginx публикует 443 с сертификатом Let's Encrypt для IP, 80 перенаправляет на HTTPS (кроме ACME challenge). Вход защищён Basic Auth, пользователь mirutex. Хэш пароля: /etc/nginx/mirutex.htpasswd; пароль находится в /root/mirutex-web-password на сервере. Секреты не включены в проект.

Nginx: /etc/nginx/sites-available/mirutex.
Certbot: /opt/mirutex-certbot/bin/certbot.
Сертификат: /etc/letsencrypt/live/mirutex-ip/.
Автопродление: mirutex-cert-renew.timer, дважды в сутки с задержкой до 30 минут. Deploy hook проверяет и перезагружает nginx. Пробное продление успешно.

Проверки извне: HTTP 301 → HTTPS; без пароля 401; с паролем HTML 200, health 200 (все компоненты true), /api/compile 200 с compiled=true. TLS проверялся стандартным хранилищем доверия, без отключения проверки сертификата.

До публикации выполнены 16 реальных интеграционных тестов через SSH-туннель: все passed (90.66 s). Публичный вход предназначен для доверенных участников демонстрации, не является многопользовательской платформой с аккаунтами.

## Обновление 25.10.2026

Root теперь открывает форму user без пароля. Демо в /demo/, прежний защищённый вход в /admin/. Публичный allowlist API и ограничения частоты — infrastructure/public-nginx.conf. Подробности — STUDIO_UPDATE.md.

## Аккаунты и пространства, релиз 2.0 от 25.10.2026

Корень открывает вход/регистрацию, демо user остаётся без пароля. Исполнение теперь разрешается по серверной сессии зарегистрированного аккаунта с CSRF и лимитами. `/admin/` сохранил дополнительный прежний Basic Auth. Nginx публикует `/api/auth`, приватные `/api/workspaces`, `/api/shares`, `/share/` и `/health`; авторизацию приватных API проверяет FastAPI.

Серверный `.env` включает MIRUTEX_REQUIRE_AUTH=true, MIRUTEX_COOKIE_SECURE=true, MIRUTEX_PUBLIC_ORIGIN=https://193.233.247.61, MIRUTEX_TRUST_PROXY=true. SQLite хранится в volume `mirutex_mirutex-data` по `/data/mirutex.sqlite3`, а не в слое контейнера.

Резервная копия старой версии: `/root/mirutex-backups/cloud-20261025T062119Z/`, образы `mirutex-api:before-cloud` и `mirutex-frontend:before-cloud`. Дополнительные копии исходников и согласованная SQLite-копия сделаны в `/root/mirutex-backups/cloud-patch-*/` перед финальным обновлением. Для обновления не использовать `down -v`.

Текущие функции, проверки и границы: CLOUD_RELEASE.md. Результаты ранних секций описывают предыдущие версии доступа и не заменяют этот раздел.
