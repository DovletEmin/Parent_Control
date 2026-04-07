# ParentControl — План проекта

## Обзор
Легитимное приложение родительского контроля. Родитель устанавливает приложение на телефон ребёнка, настраивает его и управляет через веб-панель. Приложение видно на устройстве, ребёнок не может удалить его без пароля.

---

## Технологический стек

### Backend (Монолит)
| Компонент | Технология |
|-----------|------------|
| Язык | Python 3.12+ |
| Фреймворк | FastAPI |
| База данных | PostgreSQL 16 |
| ORM | SQLAlchemy 2.0 + Alembic (миграции) |
| Кэш / Брокер | Redis |
| Фоновые задачи | Celery + Redis |
| Real-time | WebSocket (FastAPI native) |
| Медиа-стриминг | WebRTC (камера) через signaling server |
| Хранение файлов | MinIO (S3-compatible, self-hosted) |
| Аутентификация | JWT (access + refresh tokens) |
| Push-уведомления | Firebase Cloud Messaging (FCM) |
| Контейнеризация | Docker + Docker Compose |

### Mobile App (Телефон ребёнка — Android)
| Компонент | Технология |
|-----------|------------|
| Язык | Kotlin |
| Min SDK | Android 8.0 (API 26) |
| Сеть | Retrofit + OkHttp |
| WebSocket | OkHttp WebSocket |
| WebRTC | Google WebRTC SDK |
| Работа в фоне | WorkManager + Foreground Service |
| Защита от удаления | Device Admin API |

### Parent Dashboard (Веб-панель родителя)
| Компонент | Технология |
|-----------|------------|
| Фреймворк | React 18 + TypeScript |
| UI | Tailwind CSS + shadcn/ui |
| State | Zustand |
| Real-time | WebSocket client |
| WebRTC | Simple-peer |

---

## Архитектура Backend (Монолит)

```
mspy-backend/
├── alembic/                    # Миграции БД
│   └── versions/
├── app/
│   ├── __init__.py
│   ├── main.py                 # FastAPI entrypoint
│   ├── config.py               # Настройки (pydantic-settings)
│   ├── database.py             # SQLAlchemy engine, session
│   │
│   ├── models/                 # SQLAlchemy модели
│   │   ├── __init__.py
│   │   ├── user.py             # Parent user
│   │   ├── device.py           # Child device
│   │   ├── app_usage.py        # Использование приложений
│   │   ├── call_log.py         # Журнал звонков
│   │   ├── message.py          # SMS/мессенджеры
│   │   ├── media.py            # Фото/видео
│   │   ├── location.py         # Геолокация
│   │   └── notification.py     # Уведомления
│   │
│   ├── schemas/                # Pydantic schemas (request/response)
│   │   ├── __init__.py
│   │   ├── auth.py
│   │   ├── device.py
│   │   ├── app_usage.py
│   │   ├── call_log.py
│   │   ├── message.py
│   │   ├── media.py
│   │   └── location.py
│   │
│   ├── api/                    # Роуты (endpoints)
│   │   ├── __init__.py
│   │   ├── router.py           # Главный роутер
│   │   ├── auth.py             # Регистрация, логин, токены
│   │   ├── devices.py          # CRUD устройств
│   │   ├── app_usage.py        # Трекинг приложений
│   │   ├── calls.py            # Журнал звонков
│   │   ├── messages.py         # Сообщения
│   │   ├── media.py            # Фото/видео (upload/download)
│   │   ├── location.py         # Геолокация
│   │   ├── camera.py           # WebRTC signaling
│   │   └── commands.py         # Команды для устройства
│   │
│   ├── services/               # Бизнес-логика
│   │   ├── __init__.py
│   │   ├── auth_service.py
│   │   ├── device_service.py
│   │   ├── sync_service.py     # Приём данных от устройства
│   │   ├── media_service.py    # Работа с MinIO
│   │   └── notification_service.py
│   │
│   ├── ws/                     # WebSocket handlers
│   │   ├── __init__.py
│   │   ├── manager.py          # Connection manager
│   │   ├── device_ws.py        # WS для устройства ребёнка
│   │   └── parent_ws.py        # WS для панели родителя
│   │
│   ├── tasks/                  # Celery tasks
│   │   ├── __init__.py
│   │   ├── celery_app.py
│   │   └── cleanup.py          # Очистка старых данных
│   │
│   ├── core/                   # Утилиты
│   │   ├── __init__.py
│   │   ├── security.py         # JWT, хэширование паролей
│   │   └── dependencies.py     # FastAPI dependencies
│   │
│   └── middleware/
│       ├── __init__.py
│       └── auth_middleware.py
│
├── tests/
├── docker-compose.yml
├── Dockerfile
├── requirements.txt
├── alembic.ini
└── .env.example
```

---

## Модули и функциональность

### 1. Аутентификация и устройства
- Регистрация/логин родителя (email + password)
- JWT access (15 мин) + refresh (30 дней) токены
- Привязка устройства по уникальному коду (6-значный PIN)
- Device token для авторизации мобильного приложения

### 2. Отслеживание приложений (App Usage)
- Список установленных приложений на устройстве
- Время использования каждого приложения (по дням/часам)
- Запуск/остановка приложений (лог)
- Возможность заблокировать приложение (отправка команды на устройство)

### 3. Журнал звонков (Call Logs)
- Входящие / исходящие / пропущенные
- Номер, контакт, длительность, время
- Устройство синхронизирует данные каждые N минут

### 4. Сообщения (Messages)
- SMS: отправитель, текст, время
- (Опционально) уведомления мессенджеров через Accessibility Service

### 5. Медиа (Фото/Видео)
- Новые фото/видео загружаются на сервер (MinIO)
- Просмотр галереи через веб-панель
- Thumbnails для быстрой загрузки

### 6. Камера в реальном времени
- WebRTC: signaling через WebSocket сервер
- Родитель запрашивает стрим → сервер не уведомляет устройство → P2P соединение
- TURN/STUN серверы для NAT traversal

### 7. Геолокация
- Периодическая отправка координат (GPS + Network)
- История перемещений
- Отображение на карте в веб-панели

### 8. Команды (Remote Commands)
- Родитель → сервер → устройство (через WebSocket / FCM)
- Типы команд:
  - Заблокировать приложение
  - Запросить камеру
  - Запросить текущую геолокацию
  - Воспроизвести звук (найти телефон)

---

## Модели базы данных (основные)

```
users
├── id (UUID, PK)
├── email (unique)
├── password_hash
├── created_at

devices
├── id (UUID, PK)
├── user_id (FK → users)
├── name (имя ребёнка)
├── device_model
├── android_version
├── pairing_code (6-значный, временный)
├── device_token (для авторизации)
├── fcm_token
├── is_online (bool)
├── last_seen_at
├── created_at

app_usage
├── id (UUID, PK)
├── device_id (FK → devices)
├── package_name
├── app_name
├── usage_seconds (за период)
├── date
├── started_at
├── ended_at

call_logs
├── id (UUID, PK)
├── device_id (FK → devices)
├── phone_number
├── contact_name (nullable)
├── call_type (incoming/outgoing/missed)
├── duration_seconds
├── called_at

messages
├── id (UUID, PK)
├── device_id (FK → devices)
├── sender
├── receiver
├── body
├── message_type (sms/notification)
├── sent_at

media_files
├── id (UUID, PK)
├── device_id (FK → devices)
├── file_type (photo/video)
├── file_path (MinIO key)
├── thumbnail_path
├── file_size
├── created_at_device
├── uploaded_at

locations
├── id (UUID, PK)
├── device_id (FK → devices)
├── latitude
├── longitude
├── accuracy
├── recorded_at
```

---

## API Endpoints (основные)

### Auth
```
POST   /api/v1/auth/register          — Регистрация
POST   /api/v1/auth/login             — Логин (получить JWT)
POST   /api/v1/auth/refresh           — Обновить access token
```

### Devices
```
POST   /api/v1/devices                — Создать устройство + pairing code
POST   /api/v1/devices/pair           — Привязка устройства (мобильное)
GET    /api/v1/devices                — Список устройств родителя
GET    /api/v1/devices/{id}           — Информация об устройстве
DELETE /api/v1/devices/{id}           — Удалить устройство
```

### App Usage
```
POST   /api/v1/devices/{id}/apps/sync       — Синхронизация данных (от устройства)
GET    /api/v1/devices/{id}/apps             — Список приложений
GET    /api/v1/devices/{id}/apps/usage       — Статистика использования
POST   /api/v1/devices/{id}/apps/block       — Заблокировать приложение
```

### Calls
```
POST   /api/v1/devices/{id}/calls/sync      — Синхронизация (от устройства)
GET    /api/v1/devices/{id}/calls            — Журнал звонков
```

### Messages
```
POST   /api/v1/devices/{id}/messages/sync   — Синхронизация (от устройства)
GET    /api/v1/devices/{id}/messages         — Список сообщений
```

### Media
```
POST   /api/v1/devices/{id}/media/upload    — Загрузка файла (от устройства)
GET    /api/v1/devices/{id}/media           — Список медиа
GET    /api/v1/media/{id}/download          — Скачать файл
GET    /api/v1/media/{id}/thumbnail         — Thumbnail
```

### Location
```
POST   /api/v1/devices/{id}/location/sync   — Отправка координат (от устройства)
GET    /api/v1/devices/{id}/location         — История координат
GET    /api/v1/devices/{id}/location/latest  — Последняя позиция
```

### Commands
```
POST   /api/v1/devices/{id}/commands         — Отправить команду
```

### WebSocket
```
WS     /ws/device/{device_token}             — Канал устройства
WS     /ws/parent/{access_token}             — Канал родителя
```

---

## Этапы разработки

### Фаза 1 — Фундамент (Backend)
1. [x] Инициализация проекта, Docker Compose (PostgreSQL, Redis, MinIO)
2. [ ] Модели БД + миграции Alembic
3. [ ] Auth: регистрация, логин, JWT
4. [ ] CRUD устройств + система pairing

### Фаза 2 — Сбор данных (Backend)
5. [ ] API синхронизации приложений
6. [ ] API синхронизации звонков
7. [ ] API синхронизации сообщений
8. [ ] API загрузки медиа (MinIO интеграция)
9. [ ] API геолокации

### Фаза 3 — Real-time (Backend)
10. [ ] WebSocket: connection manager
11. [ ] WebSocket: каналы устройства и родителя
12. [ ] WebRTC signaling для камеры
13. [ ] Система команд (device commands)

### Фаза 4 — Android приложение
14. [ ] Базовый сервис + авторизация устройства
15. [ ] Сбор данных: приложения, звонки, SMS
16. [ ] Фоновая синхронизация (WorkManager)
17. [ ] Camera streaming (WebRTC)
18. [ ] Device Admin (защита от удаления)
19. [ ] Foreground Service с уведомлением "Родительский контроль активен"

### Фаза 5 — Веб-панель родителя
20. [ ] Авторизация + дашборд
21. [ ] Просмотр всех данных
22. [ ] Real-time камера
23. [ ] Карта с геолокацией
24. [ ] Отправка команд

---

## Запуск (Docker Compose)

```yaml
services:
  app:        # FastAPI + Uvicorn
  celery:     # Celery worker
  postgres:   # PostgreSQL
  redis:      # Redis
  minio:      # MinIO (S3)
```

---

## Безопасность
- Все API требуют JWT (кроме auth endpoints)
- Device token — отдельный механизм авторизации для устройств
- HTTPS обязателен в production
- Пароли хэшируются bcrypt
- Rate limiting на auth endpoints
- Медиа-файлы доступны только владельцу (родителю)
- Приложение на устройстве ребёнка показывает уведомление что мониторинг активен
