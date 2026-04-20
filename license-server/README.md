# CloudVisuals Sales + License Server

Сервер объединяет:
- сайт продаж и регистрацию пользователей;
- личный кабинет клиента;
- админ-панель обработки заявок;
- API проверки лицензии по HWID для клиента.

## Страницы сайта
- `/` — главная;
- `/register` — регистрация;
- `/login` — вход;
- `/dashboard` — кабинет пользователя;
- `/admin` — админ-панель (только для admin-аккаунта).

## API
- `POST /api/v1/license/verify` — проверка ключа (использует клиент);
- `POST /api/v1/admin/licenses/create` — создать ключ;
- `POST /api/v1/admin/licenses/reset-hwid` — сбросить HWID;
- `POST /api/v1/admin/licenses/toggle` — включить/выключить ключ;
- `GET /api/v1/admin/licenses/list` — список ключей.

## Быстрый деплой (Ubuntu 22)
1. Скопируй папку `license-server` на сервер в `/opt/cloudvisuals-license`.
2. В папке сервера:
```bash
cd /opt/cloudvisuals-license
cp .env.example .env
nano .env
```
3. Обязательно заполни:
- `LICENSE_ADMIN_TOKEN`
- `LICENSE_KEY_SALT`
- `LICENSE_ADMIN_PASSWORD`
- `ADMIN_UNLOCK_CODE` (код для открытия админ-панели из профиля, по умолчанию `F2D3FCA46`)

4. Установка и запуск:
```bash
chmod +x deploy/install_ubuntu.sh
./deploy/install_ubuntu.sh
```

5. Проверка:
```bash
curl http://127.0.0.1:8080/health
```

## Публикация сайта через Nginx
```bash
apt install -y nginx
cp /opt/cloudvisuals-license/deploy/cloudvisuals-license.nginx.conf /etc/nginx/sites-available/cloudvisuals-license
ln -s /etc/nginx/sites-available/cloudvisuals-license /etc/nginx/sites-enabled/cloudvisuals-license
nginx -t
systemctl restart nginx
```

## Первая авторизация в админке
- Логин: значение `LICENSE_ADMIN_USERNAME` (по умолчанию `admin`)
- Пароль: `LICENSE_ADMIN_PASSWORD`

После входа открой `/admin`.

## Примеры API запросов
Создать ключ:
```bash
curl -X POST http://127.0.0.1:8080/api/v1/admin/licenses/create \
  -H "Content-Type: application/json" \
  -H "X-Admin-Token: YOUR_ADMIN_TOKEN" \
  -d '{"days":30,"max_hwid":1,"note":"buyer-001"}'
```

Сбросить HWID:
```bash
curl -X POST http://127.0.0.1:8080/api/v1/admin/licenses/reset-hwid \
  -H "Content-Type: application/json" \
  -H "X-Admin-Token: YOUR_ADMIN_TOKEN" \
  -d '{"key":"CV-AAAA-BBBB-CCCC-DDDD"}'
```
