import base64
import hashlib
import hmac
import json
import os
import secrets
import sqlite3
import time
from contextlib import contextmanager
from pathlib import Path
from typing import Any

from fastapi import FastAPI, Form, Header, HTTPException, Request
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates
from pydantic import BaseModel, Field

BASE_DIR = Path(__file__).resolve().parent
TEMPLATES_DIR = BASE_DIR / "templates"
STATIC_DIR = BASE_DIR / "static"

APP = FastAPI(title="CloudVisuals Store", version="3.0.0")
APP.mount("/static", StaticFiles(directory=str(STATIC_DIR)), name="static")
templates = Jinja2Templates(directory=str(TEMPLATES_DIR))

DB_PATH = os.getenv("LICENSE_DB_PATH", "/opt/cloudvisuals-license/license.db")
ADMIN_TOKEN = os.getenv("LICENSE_ADMIN_TOKEN", "")
KEY_SALT = os.getenv("LICENSE_KEY_SALT", "cloudvisuals-default-salt")
HWID_TRANSPORT_SECRET = os.getenv("HWID_TRANSPORT_SECRET", "cloudvisuals-hwid-transport-2026")

SESSION_COOKIE = "cv_session"
SESSION_TTL_SEC = int(os.getenv("LICENSE_SESSION_TTL_SEC", "2592000"))
COOKIE_SECURE = os.getenv("LICENSE_COOKIE_SECURE", "0") == "1"
CLIENT_DOWNLOAD_URL = os.getenv("CLIENT_DOWNLOAD_URL", "")

DEFAULT_ADMIN_USERNAME = os.getenv("LICENSE_ADMIN_USERNAME", "admin")
DEFAULT_ADMIN_PASSWORD = os.getenv("LICENSE_ADMIN_PASSWORD", "")
DEFAULT_ADMIN_EMAIL = os.getenv("LICENSE_ADMIN_EMAIL", "admin@cloudvisuals.local")
ADMIN_UNLOCK_CODE = os.getenv("ADMIN_UNLOCK_CODE", "F2D3FCA46").strip().upper()

PRODUCT_CATALOG = {
    "cv30": {
        "code": "cv30",
        "title": "Клиент на 30 дней",
        "subtitle": "Оптимально для старта",
        "days": 30,
        "price_rub": 129,
        "accent": "starter",
    },
    "cv90": {
        "code": "cv90",
        "title": "Клиент на 90 дней",
        "subtitle": "Самый выгодный по месяцу",
        "days": 90,
        "price_rub": 259,
        "accent": "popular",
    },
    "cvforever": {
        "code": "cvforever",
        "title": "Клиент навсегда",
        "subtitle": "Один раз и без продлений",
        "days": 36500,
        "price_rub": 449,
        "accent": "ultimate",
    },
}


class VerifyRequest(BaseModel):
    key: str = Field(min_length=4, max_length=128)
    hwid: str | None = Field(default=None, max_length=256)
    hwid_enc: str | None = Field(default=None, max_length=2048)
    hwid_nonce: str | None = Field(default=None, max_length=128)
    hwid_sig: str | None = Field(default=None, max_length=256)
    client_version: str = Field(default="unknown", max_length=128)


class VerifyResponse(BaseModel):
    licensed: bool
    reason: str
    message: str
    expires_at: int | None = None
    hwid_count: int | None = None


class AdminCreateRequest(BaseModel):
    key: str | None = Field(default=None, max_length=128)
    days: int = Field(default=30, ge=1, le=365000)
    max_hwid: int = Field(default=1, ge=1, le=10)
    note: str | None = Field(default=None, max_length=256)


class AdminResetRequest(BaseModel):
    key: str = Field(min_length=4, max_length=128)


class AdminToggleRequest(BaseModel):
    key: str = Field(min_length=4, max_length=128)
    enabled: bool


class BootstrapRequest(BaseModel):
    hwid_enc: str = Field(min_length=12, max_length=2048)
    hwid_nonce: str = Field(min_length=8, max_length=128)
    hwid_sig: str = Field(min_length=32, max_length=256)
    client_version: str = Field(default="unknown", max_length=128)


def now_ts() -> int:
    return int(time.time())


def key_hash(raw_key: str) -> str:
    return hashlib.sha256(f"{KEY_SALT}:{raw_key.strip()}".encode("utf-8")).hexdigest()


def _transport_mask(seed: str, length: int) -> bytes:
    seed_bytes = seed.encode("utf-8")
    out = bytearray()
    counter = 0
    while len(out) < length:
        counter_bytes = counter.to_bytes(4, "big", signed=False)
        out.extend(hashlib.sha256(seed_bytes + counter_bytes).digest())
        counter += 1
    return bytes(out[:length])


def decrypt_hwid_payload(hwid_enc: str, nonce: str) -> str | None:
    try:
        source = base64.b64decode(hwid_enc.encode("utf-8"), validate=True)
    except Exception:
        return None

    if not source:
        return None

    mask = _transport_mask(f"{HWID_TRANSPORT_SECRET}|{nonce}", len(source))
    plain = bytes(source[i] ^ mask[i] for i in range(len(source)))
    try:
        text = plain.decode("utf-8").strip()
    except Exception:
        return None
    if len(text) < 12:
        return None
    return text


def expected_hwid_sig(hwid: str, nonce: str, context: str) -> str:
    payload = f"{HWID_TRANSPORT_SECRET}|{nonce}|{hwid}|{context}"
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def decode_and_verify_hwid(
    *,
    plain_hwid: str | None,
    enc_hwid: str | None,
    nonce: str | None,
    sig: str | None,
    context: str,
) -> str | None:
    normalized_plain = (plain_hwid or "").strip()
    if len(normalized_plain) >= 12:
        return normalized_plain

    if not enc_hwid or not nonce or not sig:
        return None

    decoded = decrypt_hwid_payload(enc_hwid, nonce)
    if not decoded:
        return None

    expected = expected_hwid_sig(decoded, nonce, context)
    if not hmac.compare_digest(expected, sig):
        return None
    return decoded


def hash_password(password: str, salt: str) -> str:
    return hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt.encode("utf-8"), 120_000).hex()


def verify_password(password: str, salt: str, expected_hash: str) -> bool:
    return hmac.compare_digest(hash_password(password, salt), expected_hash)


def random_license_key() -> str:
    chunks = [secrets.token_hex(2).upper() for _ in range(4)]
    return "CV-" + "-".join(chunks)


def mask_key(value: str) -> str:
    if not value:
        return "-"
    if len(value) <= 8:
        return "****"
    return value[:4] + "****" + value[-4:]


def format_ts(ts: int | None) -> str:
    if not ts:
        return "-"
    return time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(ts))


def product_list() -> list[dict[str, Any]]:
    return [PRODUCT_CATALOG[key] for key in ("cv30", "cv90", "cvforever")]


def get_product(code: str) -> dict[str, Any] | None:
    return PRODUCT_CATALOG.get((code or "").strip().lower())


def product_by_days(days: int) -> dict[str, Any] | None:
    for product in PRODUCT_CATALOG.values():
        if int(product["days"]) == int(days):
            return product
    return None


def user_has_active_subscription(connection: sqlite3.Connection, user_id: int, current_ts: int) -> bool:
    row = connection.execute(
        """
        SELECT id
        FROM licenses
        WHERE owner_user_id = ?
          AND enabled = 1
          AND expires_at >= ?
        ORDER BY expires_at DESC
        LIMIT 1
        """,
        (int(user_id), int(current_ts)),
    ).fetchone()
    return row is not None


def enforce_hwid_account_lock(
    connection: sqlite3.Connection,
    *,
    owner_user_id: int,
    license_id: int,
    normalized_hwid: str,
    current_ts: int,
) -> tuple[bool, str]:
    existing_hwid_owner = connection.execute(
        "SELECT user_id FROM hwid_locks WHERE hwid = ?",
        (normalized_hwid,),
    ).fetchone()
    if existing_hwid_owner is not None and int(existing_hwid_owner["user_id"]) != int(owner_user_id):
        return False, "Этот HWID уже используется другим аккаунтом"

    owner_other_hwid = connection.execute(
        "SELECT hwid FROM hwid_locks WHERE user_id = ? AND hwid <> ? LIMIT 1",
        (owner_user_id, normalized_hwid),
    ).fetchone()
    if owner_other_hwid is not None:
        return False, "На аккаунт разрешено только 1 устройство"

    if existing_hwid_owner is None:
        connection.execute(
            """
            INSERT INTO hwid_locks (hwid, user_id, license_id, created_at, last_seen_at)
            VALUES (?, ?, ?, ?, ?)
            """,
            (normalized_hwid, owner_user_id, license_id, current_ts, current_ts),
        )
    else:
        connection.execute(
            "UPDATE hwid_locks SET license_id = ?, last_seen_at = ? WHERE hwid = ?",
            (license_id, current_ts, normalized_hwid),
        )

    return True, ""


@contextmanager
def db():
    connection = sqlite3.connect(DB_PATH)
    connection.row_factory = sqlite3.Row
    try:
        yield connection
        connection.commit()
    finally:
        connection.close()


def require_admin_api(x_admin_token: str | None):
    if not ADMIN_TOKEN or x_admin_token != ADMIN_TOKEN:
        raise HTTPException(status_code=401, detail="invalid_admin_token")


def ensure_column(connection: sqlite3.Connection, table_name: str, column_name: str, column_def: str):
    columns = connection.execute(f"PRAGMA table_info({table_name})").fetchall()
    exists = any(str(column["name"]).lower() == column_name.lower() for column in columns)
    if not exists:
        connection.execute(f"ALTER TABLE {table_name} ADD COLUMN {column_def}")


def ensure_db():
    Path(DB_PATH).parent.mkdir(parents=True, exist_ok=True)
    with db() as connection:
        connection.execute(
            """
            CREATE TABLE IF NOT EXISTS licenses (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                key_hash TEXT NOT NULL UNIQUE,
                display_key TEXT NOT NULL UNIQUE,
                note TEXT,
                enabled INTEGER NOT NULL DEFAULT 1,
                max_hwid INTEGER NOT NULL DEFAULT 1,
                hwids_json TEXT NOT NULL DEFAULT '[]',
                created_at INTEGER NOT NULL,
                expires_at INTEGER NOT NULL,
                last_seen_at INTEGER,
                last_client_version TEXT,
                owner_user_id INTEGER,
                source_order_id INTEGER
            )
            """
        )
        ensure_column(connection, "licenses", "owner_user_id", "owner_user_id INTEGER")
        ensure_column(connection, "licenses", "source_order_id", "source_order_id INTEGER")

        connection.execute(
            """
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL UNIQUE,
                email TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                password_salt TEXT NOT NULL,
                is_admin INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                last_login_at INTEGER
            )
            """
        )

        connection.execute(
            """
            CREATE TABLE IF NOT EXISTS sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                token TEXT NOT NULL UNIQUE,
                user_id INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                expires_at INTEGER NOT NULL,
                ip TEXT,
                user_agent TEXT
            )
            """
        )

        connection.execute(
            """
            CREATE TABLE IF NOT EXISTS hwid_locks (
                hwid TEXT PRIMARY KEY,
                user_id INTEGER NOT NULL,
                license_id INTEGER,
                created_at INTEGER NOT NULL,
                last_seen_at INTEGER NOT NULL
            )
            """
        )

        connection.execute(
            """
            CREATE TABLE IF NOT EXISTS client_bootstrap_hwid (
                hwid_hash TEXT PRIMARY KEY,
                created_at INTEGER NOT NULL,
                last_seen_at INTEGER NOT NULL,
                launch_count INTEGER NOT NULL DEFAULT 1,
                last_client_version TEXT
            )
            """
        )

        connection.execute(
            """
            CREATE TABLE IF NOT EXISTS orders (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                product_code TEXT,
                product_name TEXT,
                plan_days INTEGER NOT NULL,
                max_hwid INTEGER NOT NULL,
                price_rub INTEGER NOT NULL,
                payment_method TEXT,
                payment_status TEXT NOT NULL DEFAULT 'pending',
                paid_at INTEGER,
                contact TEXT,
                comment TEXT,
                status TEXT NOT NULL DEFAULT 'new',
                admin_note TEXT,
                license_key TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """
        )

        ensure_column(connection, "orders", "product_code", "product_code TEXT")
        ensure_column(connection, "orders", "product_name", "product_name TEXT")
        ensure_column(connection, "orders", "payment_method", "payment_method TEXT")
        ensure_column(connection, "orders", "payment_status", "payment_status TEXT NOT NULL DEFAULT 'pending'")
        ensure_column(connection, "orders", "paid_at", "paid_at INTEGER")

        connection.execute("DELETE FROM sessions WHERE expires_at < ?", (now_ts(),))


def ensure_default_admin():
    if not DEFAULT_ADMIN_PASSWORD:
        return

    created = now_ts()
    salt = secrets.token_hex(16)
    password_hash = hash_password(DEFAULT_ADMIN_PASSWORD, salt)

    with db() as connection:
        user = connection.execute(
            "SELECT id FROM users WHERE username = ?",
            (DEFAULT_ADMIN_USERNAME,),
        ).fetchone()
        if user is None:
            connection.execute(
                """
                INSERT INTO users (username, email, password_hash, password_salt, is_admin, created_at)
                VALUES (?, ?, ?, ?, 1, ?)
                """,
                (DEFAULT_ADMIN_USERNAME, DEFAULT_ADMIN_EMAIL, password_hash, salt, created),
            )
        else:
            connection.execute(
                """
                UPDATE users
                SET email = ?, password_hash = ?, password_salt = ?, is_admin = 1
                WHERE id = ?
                """,
                (DEFAULT_ADMIN_EMAIL, password_hash, salt, user["id"]),
            )


def create_session(connection: sqlite3.Connection, user_id: int, request: Request) -> str:
    token = secrets.token_urlsafe(48)
    created = now_ts()
    expires_at = created + SESSION_TTL_SEC
    connection.execute(
        """
        INSERT INTO sessions (token, user_id, created_at, expires_at, ip, user_agent)
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        (
            token,
            user_id,
            created,
            expires_at,
            request.client.host if request.client else "",
            request.headers.get("user-agent", ""),
        ),
    )
    return token


def get_current_user(request: Request) -> sqlite3.Row | None:
    token = request.cookies.get(SESSION_COOKIE)
    if not token:
        return None

    with db() as connection:
        row = connection.execute(
            """
            SELECT u.*, s.expires_at
            FROM sessions s
            JOIN users u ON u.id = s.user_id
            WHERE s.token = ?
            """,
            (token,),
        ).fetchone()
        if row is None:
            return None
        if int(row["expires_at"]) < now_ts():
            connection.execute("DELETE FROM sessions WHERE token = ?", (token,))
            return None
        return row


def set_session_cookie(response: RedirectResponse, token: str):
    response.set_cookie(
        SESSION_COOKIE,
        token,
        max_age=SESSION_TTL_SEC,
        httponly=True,
        secure=COOKIE_SECURE,
        samesite="lax",
        path="/",
    )


def clear_session_cookie(response: RedirectResponse):
    response.delete_cookie(SESSION_COOKIE, path="/")


def create_license_record(
    connection: sqlite3.Connection,
    *,
    raw_key: str,
    days: int,
    max_hwid: int,
    note: str,
    owner_user_id: int | None = None,
    source_order_id: int | None = None,
) -> dict[str, Any]:
    created = now_ts()
    expires_at = created + max(1, int(days)) * 24 * 60 * 60
    connection.execute(
        """
        INSERT INTO licenses (
            key_hash, display_key, note, enabled, max_hwid, hwids_json, created_at, expires_at, owner_user_id, source_order_id
        ) VALUES (?, ?, ?, 1, ?, '[]', ?, ?, ?, ?)
        """,
        (
            key_hash(raw_key),
            raw_key,
            note[:256],
            max_hwid,
            created,
            expires_at,
            owner_user_id,
            source_order_id,
        ),
    )
    return {"key": raw_key, "expires_at": expires_at, "max_hwid": max_hwid}


def create_unique_license_record(
    connection: sqlite3.Connection,
    *,
    preferred_key: str | None,
    days: int,
    max_hwid: int,
    note: str,
    owner_user_id: int | None = None,
    source_order_id: int | None = None,
) -> dict[str, Any]:
    if preferred_key:
        return create_license_record(
            connection,
            raw_key=preferred_key,
            days=days,
            max_hwid=max_hwid,
            note=note,
            owner_user_id=owner_user_id,
            source_order_id=source_order_id,
        )

    for _ in range(10):
        candidate = random_license_key()
        try:
            return create_license_record(
                connection,
                raw_key=candidate,
                days=days,
                max_hwid=max_hwid,
                note=note,
                owner_user_id=owner_user_id,
                source_order_id=source_order_id,
            )
        except sqlite3.IntegrityError:
            continue

    raise HTTPException(status_code=500, detail="license_generation_failed")


def render_template(request: Request, template_name: str, **ctx):
    base_ctx = {
        "request": request,
        "user": get_current_user(request),
        "download_url": CLIENT_DOWNLOAD_URL,
        "products": product_list(),
    }
    base_ctx.update(ctx)
    return templates.TemplateResponse(template_name, base_ctx)


@APP.on_event("startup")
def on_startup():
    ensure_db()
    ensure_default_admin()
    templates.env.filters["format_ts"] = format_ts
    templates.env.filters["mask_key"] = mask_key


@APP.get("/health")
def health():
    return {"ok": True, "time": now_ts(), "web": True}


@APP.get("/", response_class=HTMLResponse)
def index(request: Request, msg: str | None = None):
    return render_template(request, "index.html", msg=msg)


@APP.get("/buy", response_class=HTMLResponse)
def buy_page(request: Request, msg: str | None = None):
    return render_template(request, "buy.html", msg=msg)


@APP.get("/register", response_class=HTMLResponse)
def register_page(request: Request):
    if get_current_user(request) is not None:
        return RedirectResponse("/dashboard", status_code=303)
    return render_template(request, "register.html", error=None)


@APP.post("/register", response_class=HTMLResponse)
def register_submit(
    request: Request,
    username: str = Form(...),
    email: str = Form(...),
    password: str = Form(...),
    confirm_password: str = Form(...),
):
    username = username.strip()
    email = email.strip().lower()

    if len(username) < 3 or len(username) > 24:
        return render_template(request, "register.html", error="Ник должен быть от 3 до 24 символов")
    if "@" not in email or len(email) > 120:
        return render_template(request, "register.html", error="Некорректная почта")
    if len(password) < 6:
        return render_template(request, "register.html", error="Пароль минимум 6 символов")
    if password != confirm_password:
        return render_template(request, "register.html", error="Пароли не совпадают")

    created = now_ts()
    salt = secrets.token_hex(16)
    pwd_hash = hash_password(password, salt)

    with db() as connection:
        exists = connection.execute(
            "SELECT id FROM users WHERE username = ? OR email = ?",
            (username, email),
        ).fetchone()
        if exists is not None:
            return render_template(request, "register.html", error="Пользователь с таким ником или почтой уже есть")

        connection.execute(
            """
            INSERT INTO users (username, email, password_hash, password_salt, is_admin, created_at)
            VALUES (?, ?, ?, ?, 0, ?)
            """,
            (username, email, pwd_hash, salt, created),
        )
        user_id = connection.execute("SELECT id FROM users WHERE username = ?", (username,)).fetchone()["id"]
        token = create_session(connection, int(user_id), request)

    response = RedirectResponse("/dashboard?msg=Регистрация завершена", status_code=303)
    set_session_cookie(response, token)
    return response


@APP.get("/login", response_class=HTMLResponse)
def login_page(request: Request):
    if get_current_user(request) is not None:
        return RedirectResponse("/dashboard", status_code=303)
    return render_template(request, "login.html", error=None)


@APP.post("/login", response_class=HTMLResponse)
def login_submit(
    request: Request,
    username_or_email: str = Form(...),
    password: str = Form(...),
):
    lookup = username_or_email.strip()
    with db() as connection:
        user = connection.execute(
            "SELECT * FROM users WHERE username = ? OR email = ?",
            (lookup, lookup.lower()),
        ).fetchone()
        if user is None:
            return render_template(request, "login.html", error="Пользователь не найден")
        if not verify_password(password, user["password_salt"], user["password_hash"]):
            return render_template(request, "login.html", error="Неверный пароль")

        connection.execute("UPDATE users SET last_login_at = ? WHERE id = ?", (now_ts(), user["id"]))
        token = create_session(connection, int(user["id"]), request)

    response = RedirectResponse("/dashboard?msg=Вход выполнен", status_code=303)
    set_session_cookie(response, token)
    return response


@APP.post("/logout")
def logout(request: Request):
    token = request.cookies.get(SESSION_COOKIE)
    if token:
        with db() as connection:
            connection.execute("DELETE FROM sessions WHERE token = ?", (token,))
    response = RedirectResponse("/", status_code=303)
    clear_session_cookie(response)
    return response


@APP.get("/checkout/{product_code}", response_class=HTMLResponse)
def checkout_page(request: Request, product_code: str):
    product = get_product(product_code)
    if product is None:
        raise HTTPException(status_code=404, detail="product_not_found")
    return render_template(request, "checkout.html", product=product, error=None)


@APP.post("/checkout/{product_code}/pay")
def checkout_pay(
    request: Request,
    product_code: str,
    payment_method: str = Form(default="card"),
    contact: str = Form(default=""),
    comment: str = Form(default=""),
):
    product = get_product(product_code)
    if product is None:
        raise HTTPException(status_code=404, detail="product_not_found")

    user = get_current_user(request)
    if user is None:
        return RedirectResponse("/login", status_code=303)

    payment_method = (payment_method or "card").strip().lower()
    if payment_method not in {"card", "sbp", "crypto"}:
        payment_method = "card"

    created = now_ts()
    contact = contact.strip()[:120]
    comment = comment.strip()[:300]

    with db() as connection:
        connection.execute(
            """
            INSERT INTO orders (
                user_id, product_code, product_name, plan_days, max_hwid, price_rub,
                payment_method, payment_status, paid_at, contact, comment,
                status, admin_note, license_key, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, 'paid', ?, ?, ?, 'approved', 'auto_paid', NULL, ?, ?)
            """,
            (
                user["id"],
                product["code"],
                product["title"],
                product["days"],
                1,
                product["price_rub"],
                payment_method,
                created,
                contact,
                comment,
                created,
                created,
            ),
        )
        order_id = connection.execute("SELECT last_insert_rowid() AS id").fetchone()["id"]

        note = f"store_auto user={user['username']} product={product['code']}"
        license_data = create_unique_license_record(
            connection,
            preferred_key=None,
            days=int(product["days"]),
            max_hwid=1,
            note=note,
            owner_user_id=int(user["id"]),
            source_order_id=int(order_id),
        )

        connection.execute(
            "UPDATE orders SET license_key = ?, updated_at = ? WHERE id = ?",
            (license_data["key"], now_ts(), order_id),
        )

    return RedirectResponse("/dashboard?msg=Оплата успешна, ключ выдан автоматически", status_code=303)


@APP.get("/dashboard", response_class=HTMLResponse)
def dashboard(request: Request, msg: str | None = None):
    user = get_current_user(request)
    if user is None:
        return RedirectResponse("/login", status_code=303)

    with db() as connection:
        orders = connection.execute(
            """
            SELECT
                id, product_code, product_name, plan_days, max_hwid, price_rub,
                payment_method, payment_status, paid_at, status, admin_note, license_key,
                created_at, updated_at
            FROM orders
            WHERE user_id = ?
            ORDER BY id DESC
            """,
            (user["id"],),
        ).fetchall()

        licenses = connection.execute(
            """
            SELECT display_key, note, enabled, max_hwid, hwids_json, created_at, expires_at, last_seen_at
            FROM licenses
            WHERE owner_user_id = ?
            ORDER BY id DESC
            """,
            (user["id"],),
        ).fetchall()

    return render_template(request, "dashboard.html", msg=msg, orders=orders, licenses=licenses)


@APP.get("/profile")
def profile_alias():
    return RedirectResponse("/dashboard", status_code=303)


@APP.get("/dashboard/admin-panel", response_class=HTMLResponse)
def admin_unlock_page(request: Request, msg: str | None = None):
    user = get_current_user(request)
    if user is None:
        return RedirectResponse("/login", status_code=303)
    if int(user["is_admin"]) == 1:
        return RedirectResponse("/admin", status_code=303)
    return render_template(request, "admin_unlock.html", msg=msg)


@APP.post("/dashboard/unlock-admin")
def unlock_admin_panel(
    request: Request,
    admin_code: str = Form(default=""),
):
    user = get_current_user(request)
    if user is None:
        return RedirectResponse("/login", status_code=303)

    provided_code = (admin_code or "").strip().upper()
    if not provided_code:
        return RedirectResponse("/dashboard/admin-panel?msg=Введите+код+доступа", status_code=303)
    if provided_code != ADMIN_UNLOCK_CODE:
        return RedirectResponse("/dashboard/admin-panel?msg=Неверный+код", status_code=303)
    if not provided_code:
        return RedirectResponse("/dashboard?msg=Введите+код+доступа", status_code=303)
    if provided_code != ADMIN_UNLOCK_CODE:
        return RedirectResponse("/dashboard?msg=Неверный+код", status_code=303)

    with db() as connection:
        connection.execute(
            "UPDATE users SET is_admin = 1 WHERE id = ?",
            (user["id"],),
        )

    return RedirectResponse("/admin?msg=Admin+panel+enabled", status_code=303)


@APP.post("/dashboard/activate-license")
def activate_license(
    request: Request,
    license_key: str = Form(default=""),
):
    user = get_current_user(request)
    if user is None:
        return RedirectResponse("/login", status_code=303)

    key = (license_key or "").strip()
    if not key:
        return RedirectResponse("/dashboard?msg=Введите+ключ", status_code=303)

    hashed = key_hash(key)
    with db() as connection:
        row = connection.execute(
            "SELECT id, owner_user_id FROM licenses WHERE key_hash = ?",
            (hashed,),
        ).fetchone()
        if row is None:
            return RedirectResponse("/dashboard?msg=Ключ+не+найден", status_code=303)

        owner_user_id = row["owner_user_id"]
        if owner_user_id is not None and int(owner_user_id) != int(user["id"]):
            return RedirectResponse("/dashboard?msg=Ключ+уже+привязан+к+другому+аккаунту", status_code=303)

        if owner_user_id is None:
            connection.execute(
                "UPDATE licenses SET owner_user_id = ? WHERE id = ?",
                (int(user["id"]), int(row["id"])),
            )

    return RedirectResponse("/dashboard?msg=Ключ+успешно+активирован", status_code=303)


@APP.get("/install")
def install_client(request: Request):
    user = get_current_user(request)
    if user is None:
        return RedirectResponse("/login", status_code=303)

    with db() as connection:
        if not user_has_active_subscription(connection, int(user["id"]), now_ts()):
            return RedirectResponse("/dashboard?msg=No+active+subscription+for+download", status_code=303)

    if CLIENT_DOWNLOAD_URL and CLIENT_DOWNLOAD_URL.strip():
        return RedirectResponse(CLIENT_DOWNLOAD_URL.strip(), status_code=302)
    return RedirectResponse("/dashboard?msg=Ссылка+на+установщик+пока+не+задана", status_code=303)


@APP.post("/orders/create")
def create_order_legacy(
    request: Request,
    plan_days: int = Form(...),
    max_hwid: int = Form(default=1),
    contact: str = Form(default=""),
    comment: str = Form(default=""),
):
    user = get_current_user(request)
    if user is None:
        return RedirectResponse("/login", status_code=303)

    product = product_by_days(plan_days)
    if product is None:
        return RedirectResponse("/dashboard?msg=Некорректный тариф", status_code=303)

    max_hwid = 1 if max_hwid < 1 else min(max_hwid, 3)
    contact = contact.strip()[:120]
    comment = comment.strip()[:300]
    created = now_ts()

    with db() as connection:
        connection.execute(
            """
            INSERT INTO orders (
                user_id, product_code, product_name, plan_days, max_hwid, price_rub,
                payment_method, payment_status, paid_at, contact, comment,
                status, admin_note, license_key, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, 'manual', 'pending', NULL, ?, ?, 'new', NULL, NULL, ?, ?)
            """,
            (
                user["id"],
                product["code"],
                product["title"],
                product["days"],
                max_hwid,
                product["price_rub"],
                contact,
                comment,
                created,
                created,
            ),
        )

    return RedirectResponse("/dashboard?msg=Заявка создана", status_code=303)


@APP.get("/admin", response_class=HTMLResponse)
def admin_page(request: Request, msg: str | None = None):
    user = get_current_user(request)
    if user is None:
        return RedirectResponse("/login", status_code=303)
    if int(user["is_admin"]) != 1:
        return RedirectResponse("/dashboard?msg=Нет доступа", status_code=303)

    with db() as connection:
        orders = connection.execute(
            """
            SELECT o.*, u.username, u.email
            FROM orders o
            JOIN users u ON u.id = o.user_id
            ORDER BY CASE o.status WHEN 'new' THEN 0 WHEN 'approved' THEN 1 ELSE 2 END, o.id DESC
            """
        ).fetchall()

        recent_licenses = connection.execute(
            """
            SELECT display_key, note, enabled, max_hwid, created_at, expires_at, last_seen_at
            FROM licenses
            ORDER BY id DESC
            LIMIT 20
            """
        ).fetchall()

    return render_template(request, "admin.html", msg=msg, orders=orders, recent_licenses=recent_licenses)


@APP.post("/admin/orders/{order_id}/approve")
def admin_approve_order(
    request: Request,
    order_id: int,
    days_override: int = Form(default=0),
    max_hwid_override: int = Form(default=0),
    admin_note: str = Form(default=""),
):
    user = get_current_user(request)
    if user is None:
        return RedirectResponse("/login", status_code=303)
    if int(user["is_admin"]) != 1:
        return RedirectResponse("/dashboard?msg=Нет доступа", status_code=303)

    with db() as connection:
        order = connection.execute(
            """
            SELECT o.*, u.username, u.email
            FROM orders o
            JOIN users u ON u.id = o.user_id
            WHERE o.id = ?
            """,
            (order_id,),
        ).fetchone()
        if order is None:
            return RedirectResponse("/admin?msg=Заказ не найден", status_code=303)
        if order["status"] != "new":
            return RedirectResponse("/admin?msg=Заказ уже обработан", status_code=303)

        plan_days = days_override if days_override > 0 else int(order["plan_days"])
        max_hwid = max_hwid_override if max_hwid_override > 0 else int(order["max_hwid"])
        if plan_days < 1 or plan_days > 365000 or max_hwid < 1 or max_hwid > 10:
            return RedirectResponse("/admin?msg=Неверные параметры", status_code=303)

        extra = admin_note.strip()[:160]
        note = f"admin_issue user={order['username']} email={order['email']}"
        if extra:
            note += f" | {extra}"

        license_data = create_unique_license_record(
            connection,
            preferred_key=None,
            days=plan_days,
            max_hwid=max_hwid,
            note=note,
            owner_user_id=int(order["user_id"]),
            source_order_id=order_id,
        )

        connection.execute(
            """
            UPDATE orders
            SET status = 'approved', payment_status = 'paid', paid_at = ?, admin_note = ?, license_key = ?, updated_at = ?
            WHERE id = ?
            """,
            (now_ts(), extra, license_data["key"], now_ts(), order_id),
        )

    return RedirectResponse("/admin?msg=Заказ подтвержден, ключ выдан", status_code=303)


@APP.post("/admin/orders/{order_id}/reject")
def admin_reject_order(
    request: Request,
    order_id: int,
    admin_note: str = Form(default=""),
):
    user = get_current_user(request)
    if user is None:
        return RedirectResponse("/login", status_code=303)
    if int(user["is_admin"]) != 1:
        return RedirectResponse("/dashboard?msg=Нет доступа", status_code=303)

    with db() as connection:
        updated = connection.execute(
            """
            UPDATE orders
            SET status = 'rejected', admin_note = ?, updated_at = ?
            WHERE id = ? AND status = 'new'
            """,
            (admin_note.strip()[:160], now_ts(), order_id),
        ).rowcount
    if updated == 0:
        return RedirectResponse("/admin?msg=Заказ не найден или уже обработан", status_code=303)

    return RedirectResponse("/admin?msg=Заказ отклонен", status_code=303)


@APP.post("/api/v1/client/bootstrap")
def client_bootstrap(payload: BootstrapRequest):
    current = now_ts()
    normalized_hwid = decode_and_verify_hwid(
        plain_hwid=None,
        enc_hwid=payload.hwid_enc,
        nonce=payload.hwid_nonce,
        sig=payload.hwid_sig,
        context="bootstrap",
    )
    if not normalized_hwid:
        raise HTTPException(status_code=400, detail="invalid_hwid_payload")

    hwid_hash = hashlib.sha256(f"bootstrap:{normalized_hwid}".encode("utf-8")).hexdigest()
    with db() as connection:
        row = connection.execute(
            "SELECT launch_count FROM client_bootstrap_hwid WHERE hwid_hash = ?",
            (hwid_hash,),
        ).fetchone()
        if row is None:
            connection.execute(
                """
                INSERT INTO client_bootstrap_hwid (hwid_hash, created_at, last_seen_at, launch_count, last_client_version)
                VALUES (?, ?, ?, 1, ?)
                """,
                (hwid_hash, current, current, payload.client_version[:128]),
            )
        else:
            connection.execute(
                """
                UPDATE client_bootstrap_hwid
                SET last_seen_at = ?, launch_count = ?, last_client_version = ?
                WHERE hwid_hash = ?
                """,
                (current, int(row["launch_count"]) + 1, payload.client_version[:128], hwid_hash),
            )

    return {"ok": True}


@APP.post("/api/v1/license/verify", response_model=VerifyResponse)
def verify(payload: VerifyRequest):
    current = now_ts()
    normalized_key = payload.key.strip()
    normalized_hwid = decode_and_verify_hwid(
        plain_hwid=payload.hwid,
        enc_hwid=payload.hwid_enc,
        nonce=payload.hwid_nonce,
        sig=payload.hwid_sig,
        context=f"verify:{normalized_key}",
    )
    if not normalized_hwid:
        return VerifyResponse(licensed=False, reason="invalid_hwid", message="Неверный HWID payload")

    with db() as connection:
        row = connection.execute(
            "SELECT * FROM licenses WHERE key_hash = ?",
            (key_hash(normalized_key),),
        ).fetchone()

        if row is None:
            return VerifyResponse(licensed=False, reason="not_found", message="Ключ не найден")
        if int(row["enabled"]) != 1:
            return VerifyResponse(licensed=False, reason="disabled", message="Лицензия отключена")

        expires_at = int(row["expires_at"])
        if current > expires_at:
            return VerifyResponse(licensed=False, reason="expired", message="Лицензия истекла", expires_at=expires_at)

        owner_user_id = row["owner_user_id"]
        if owner_user_id is None:
            return VerifyResponse(
                licensed=False,
                reason="owner_required",
                message="Активируйте ключ в личном кабинете аккаунта",
                expires_at=expires_at,
            )

        lock_ok, lock_message = enforce_hwid_account_lock(
            connection,
            owner_user_id=int(owner_user_id),
            license_id=int(row["id"]),
            normalized_hwid=normalized_hwid,
            current_ts=current,
        )
        if not lock_ok:
            return VerifyResponse(
                licensed=False,
                reason="hwid_account_lock",
                message=lock_message,
                expires_at=expires_at,
            )

        hwids = json.loads(row["hwids_json"] or "[]")
        if normalized_hwid not in hwids:
            max_hwid = max(1, int(row["max_hwid"]))
            if len(hwids) >= max_hwid:
                return VerifyResponse(
                    licensed=False,
                    reason="hwid_limit",
                    message="Превышен лимит устройств",
                    expires_at=expires_at,
                    hwid_count=len(hwids),
                )
            hwids.append(normalized_hwid)
            connection.execute(
                "UPDATE licenses SET hwids_json = ? WHERE id = ?",
                (json.dumps(hwids), row["id"]),
            )

        connection.execute(
            "UPDATE licenses SET last_seen_at = ?, last_client_version = ? WHERE id = ?",
            (current, payload.client_version, row["id"]),
        )

        return VerifyResponse(
            licensed=True,
            reason="ok",
            message="Лицензия активна",
            expires_at=expires_at,
            hwid_count=len(hwids),
        )


@APP.post("/api/v1/admin/licenses/create")
def admin_create_license(
    payload: AdminCreateRequest,
    x_admin_token: str | None = Header(default=None),
):
    require_admin_api(x_admin_token)

    raw_key = payload.key.strip() if payload.key else None
    with db() as connection:
        try:
            result = create_unique_license_record(
                connection,
                preferred_key=raw_key,
                days=payload.days,
                max_hwid=payload.max_hwid,
                note=(payload.note or "")[:256],
            )
        except sqlite3.IntegrityError:
            raise HTTPException(status_code=409, detail="key_already_exists")

    return {"ok": True, **result}


@APP.post("/api/v1/admin/licenses/reset-hwid")
def admin_reset_hwid(
    payload: AdminResetRequest,
    x_admin_token: str | None = Header(default=None),
):
    require_admin_api(x_admin_token)
    with db() as connection:
        row = connection.execute(
            "SELECT id, owner_user_id FROM licenses WHERE key_hash = ?",
            (key_hash(payload.key.strip()),),
        ).fetchone()
        if row is None:
            updated = 0
        else:
            connection.execute(
                "UPDATE licenses SET hwids_json = '[]' WHERE id = ?",
                (int(row["id"]),),
            )
            owner_user_id = row["owner_user_id"]
            if owner_user_id is not None:
                connection.execute(
                    "DELETE FROM hwid_locks WHERE user_id = ?",
                    (int(owner_user_id),),
                )
            else:
                connection.execute(
                    "DELETE FROM hwid_locks WHERE license_id = ?",
                    (int(row["id"]),),
                )
            updated = 1
    if updated == 0:
        raise HTTPException(status_code=404, detail="license_not_found")
    return {"ok": True}


@APP.post("/api/v1/admin/licenses/toggle")
def admin_toggle(
    payload: AdminToggleRequest,
    x_admin_token: str | None = Header(default=None),
):
    require_admin_api(x_admin_token)
    with db() as connection:
        updated = connection.execute(
            "UPDATE licenses SET enabled = ? WHERE key_hash = ?",
            (1 if payload.enabled else 0, key_hash(payload.key.strip())),
        ).rowcount
    if updated == 0:
        raise HTTPException(status_code=404, detail="license_not_found")
    return {"ok": True, "enabled": payload.enabled}


@APP.get("/api/v1/admin/licenses/list")
def admin_list_licenses(
    x_admin_token: str | None = Header(default=None),
):
    require_admin_api(x_admin_token)
    with db() as connection:
        rows = connection.execute(
            """
            SELECT display_key, note, enabled, max_hwid, hwids_json, created_at, expires_at, last_seen_at, last_client_version
            FROM licenses
            ORDER BY id DESC
            """
        ).fetchall()

    items = []
    for row in rows:
        hwids = json.loads(row["hwids_json"] or "[]")
        items.append(
            {
                "key": row["display_key"],
                "note": row["note"],
                "enabled": bool(row["enabled"]),
                "max_hwid": row["max_hwid"],
                "hwid_count": len(hwids),
                "created_at": row["created_at"],
                "expires_at": row["expires_at"],
                "last_seen_at": row["last_seen_at"],
                "last_client_version": row["last_client_version"],
            }
        )
    return {"ok": True, "items": items}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "app:APP",
        host=os.getenv("LICENSE_HOST", "0.0.0.0"),
        port=int(os.getenv("LICENSE_PORT", "8080")),
        reload=False,
    )
