#!/usr/bin/env python3
"""
Magis/FLUJO /api/v7 compatibility bridge → SEÑAL REST API.

The forked APK points host adfereredadasww.ai → senalapi-origen.tv (same length).
Point DNS / hosts for senalapi-origen.tv to this service (or proxy 80→PORT).

Upstream: SENAL_BASE_URL (default http://185.192.20.245:3000)
"""

from __future__ import annotations

import os
import re
import time
import uuid
from typing import Any

import httpx
from fastapi import FastAPI, Header, Request, Response
from fastapi.responses import JSONResponse

SENAL = os.environ.get("SENAL_BASE_URL", "http://185.192.20.245:3000").rstrip("/")
PORT = int(os.environ.get("PORT", "8080"))
ADULT_RE = re.compile(
    r"(?i)(\+| )?18\+?|adult|adulto|adultos|xxx|porn|porno|erotic|erotica|nsfw"
)
PREFERRED_ORDER = [
    "deportes",
    "sports",
    "noticias",
    "news",
    "cine",
    "peliculas",
    "películas",
    "series",
    "infantil",
    "kids",
    "latino",
    "latinos",
    "españa",
    "espana",
    "mexico",
    "méxico",
    "usa",
    "documentales",
    "musica",
    "música",
    "4k",
    "general",
]

app = FastAPI(title="SEÑAL Magis-compat bridge", version="1.0.0")


def is_adult_label(label: str | None) -> bool:
    return bool(label and ADULT_RE.search(label))


def preferred_index(label: str) -> int:
    key = label.strip().lower()
    for i, pref in enumerate(PREFERRED_ORDER):
        if key == pref or pref in key:
            return i
    return len(PREFERRED_ORDER) + 1


def sort_category_names(names: list[str]) -> list[str]:
    normal = [n for n in names if not is_adult_label(n)]
    adults = [n for n in names if is_adult_label(n)]
    normal.sort(key=lambda n: (preferred_index(n), n.lower()))
    return normal + adults


def ok(data: Any = None, msg: str = "success") -> dict[str, Any]:
    return {
        "code": 200,
        "ret": 0,
        "errorCode": 0,
        "errorMsg": "",
        "msg": msg,
        "message": msg,
        "isSuccess": True,
        "success": True,
        "data": data if data is not None else {},
        "result": data if data is not None else {},
    }


def fail(msg: str, code: int = 400) -> JSONResponse:
    body = {
        "code": code,
        "ret": code,
        "errorCode": code,
        "errorMsg": msg,
        "msg": msg,
        "message": msg,
        "isSuccess": False,
        "success": False,
        "data": None,
        "result": None,
    }
    return JSONResponse(body, status_code=200)  # Magis clients often expect HTTP 200


def bearer(authorization: str | None) -> dict[str, str]:
    headers = {"Accept": "application/json"}
    if authorization:
        headers["Authorization"] = authorization
    elif os.environ.get("SENAL_TOKEN"):
        headers["Authorization"] = f"Bearer {os.environ['SENAL_TOKEN']}"
    return headers


async def senal(method: str, path: str, authorization: str | None = None, **kwargs):
    async with httpx.AsyncClient(timeout=30.0) as client:
        r = await client.request(
            method,
            f"{SENAL}{path}",
            headers=bearer(authorization),
            **kwargs,
        )
        return r


def catalog_items(payload: dict[str, Any]) -> list[dict[str, Any]]:
    for key in ("items", "channels", "movies", "series", "data", "results"):
        val = payload.get(key)
        if isinstance(val, list):
            return val
    return []


def has_more(payload: dict[str, Any], page_size: int, got: int) -> bool:
    if isinstance(payload.get("hasMore"), bool):
        return bool(payload["hasMore"])
    if payload.get("nextPage") is not None:
        return True
    total = payload.get("total")
    page = payload.get("page") or 1
    limit = payload.get("limit") or page_size
    if isinstance(total, int) and isinstance(page, int) and isinstance(limit, int):
        return page * limit < total
    return got >= page_size


async def fetch_all_live(authorization: str | None) -> list[dict[str, Any]]:
    """Page through SEÑAL live catalog so categories are complete."""
    items: list[dict[str, Any]] = []
    page = 1
    page_size = 200
    while page <= 40:
        r = await senal(
            "GET",
            "/api/catalog",
            authorization,
            params={"type": "live", "page": page, "limit": page_size},
        )
        if r.status_code >= 400:
            if page == 1:
                raise RuntimeError(f"catalog live HTTP {r.status_code}")
            break
        payload = r.json()
        batch = catalog_items(payload)
        if not batch:
            break
        items.extend(batch)
        if not has_more(payload, page_size, len(batch)):
            break
        page += 1
    # de-dupe by id
    seen: set[str] = set()
    unique: list[dict[str, Any]] = []
    for it in items:
        cid = str(it.get("id") or it.get("_id") or it.get("streamId") or it.get("name") or "")
        if not cid or cid in seen:
            continue
        seen.add(cid)
        unique.append(it)
    return unique


def map_channel(item: dict[str, Any], idx: int) -> dict[str, Any]:
    cid = str(item.get("id") or item.get("_id") or item.get("streamId") or idx)
    name = item.get("title") or item.get("name") or f"Canal {idx}"
    logo = item.get("logo") or item.get("poster") or item.get("cover") or item.get("image") or ""
    category = item.get("category") or item.get("group") or "General"
    return {
        "id": cid,
        "channelId": cid,
        "streamId": cid,
        "name": name,
        "title": name,
        "num": item.get("number") or item.get("channelNumber") or idx,
        "number": item.get("number") or item.get("channelNumber") or idx,
        "logo": logo,
        "icon": logo,
        "image": logo,
        "poster": logo,
        "cover": logo,
        "group": category,
        "category": category,
        "categoryId": item.get("categoryId") or category,
        "epgNow": item.get("epgNow") or (item.get("epg") or {}).get("now") if isinstance(item.get("epg"), dict) else "",
        "epgNext": item.get("epgNext") or (item.get("epg") or {}).get("next") if isinstance(item.get("epg"), dict) else "",
        "url": item.get("url") or "",
        "playUrl": item.get("url") or "",
        "source": item.get("url") or "",
        "type": item.get("type") or "live",
    }


def map_vod(item: dict[str, Any], idx: int) -> dict[str, Any]:
    base = map_channel(item, idx)
    base.update(
        {
            "year": item.get("year"),
            "score": item.get("rating"),
            "rating": item.get("rating"),
            "description": item.get("description") or item.get("plot") or item.get("synopsis") or "",
            "desc": item.get("description") or item.get("plot") or item.get("synopsis") or "",
            "genre": item.get("genre") or ",".join(item.get("genres") or []),
            "seasons": item.get("seasons") or [],
        }
    )
    return base


@app.get("/")
async def root():
    return {
        "service": "SEÑAL Magis-compat bridge",
        "upstream": SENAL,
        "hint": "Point senalapi-origen.tv → this host; APK host was patched to that name.",
    }


@app.api_route("/api/v7/info", methods=["GET", "POST"])
@app.api_route("/api/v7/", methods=["GET", "POST"])
async def info():
    return ok(
        {
            "appName": "SEÑAL",
            "name": "SEÑAL",
            "brand": "SEÑAL",
            "portal": SENAL,
            "version": "8.6.2",
            "serverTime": int(time.time()),
        }
    )


@app.api_route("/api/v7/login", methods=["GET", "POST"])
async def login(request: Request):
    body: dict[str, Any] = {}
    try:
        body = await request.json()
    except Exception:
        form = await request.form()
        body = dict(form)

    username = body.get("username") or body.get("account") or body.get("user") or body.get("email")
    password = body.get("password") or body.get("pass")
    device_id = body.get("deviceId") or body.get("device_id") or body.get("mac") or str(uuid.uuid4())
    device_name = body.get("deviceName") or body.get("device_name") or "SEÑAL TV"

    if not username or not password:
        return fail("username/password required")

    r = await senal(
        "POST",
        "/api/auth/login",
        json={
            "username": username,
            "password": password,
            "deviceId": device_id,
            "deviceName": device_name,
        },
    )
    try:
        payload = r.json()
    except Exception:
        return fail(f"upstream login failed: HTTP {r.status_code}")

    token = payload.get("token") or payload.get("accessToken") or payload.get("jwt")
    if not token:
        return fail(payload.get("error") or payload.get("message") or "login failed")

    user = payload.get("user") or {}
    data = {
        "token": token,
        "accessToken": token,
        "Authorization": f"Bearer {token}",
        "userId": user.get("id") or username,
        "uid": user.get("id") or username,
        "username": user.get("username") or username,
        "account": user.get("username") or username,
        "expireTime": user.get("expiresAt") or "",
        "expiration": user.get("expiresAt") or "",
        "deviceId": device_id,
        "settings": {},
        "meal": {"name": "SEÑAL", "expireTime": user.get("expiresAt") or ""},
        "userInfo": {
            "userId": user.get("id") or username,
            "username": user.get("username") or username,
            "role": user.get("role") or "user",
            "expireTime": user.get("expiresAt") or "",
        },
    }
    return ok(data)


@app.api_route("/api/v7/logout", methods=["GET", "POST"])
async def logout():
    return ok({})


@app.api_route("/api/v7/site/live", methods=["GET", "POST"])
@app.api_route("/api/v7/site/liveTag", methods=["GET", "POST"])
async def site_live(authorization: str | None = Header(default=None)):
    try:
        items = await fetch_all_live(authorization)
    except RuntimeError as err:
        return fail(str(err))
    channels = [map_channel(it, i + 1) for i, it in enumerate(items)]

    # Magis often expects categories → channels
    buckets: dict[str, list] = {}
    for ch in channels:
        buckets.setdefault(ch["category"], []).append(ch)
    # Adult last; prefer Deportes/Noticias/… first (never open on Adultos)
    ordered_names = sort_category_names(list(buckets.keys()))
    categories = [
        {
            "id": name,
            "categoryId": name,
            "name": name,
            "title": name,
            "list": buckets[name],
            "channels": buckets[name],
            "data": buckets[name],
        }
        for name in ordered_names
    ]
    ordered_channels = [ch for name in ordered_names for ch in buckets[name]]
    data = {
        "list": categories,
        "categories": categories,
        "channels": ordered_channels,
        "items": ordered_channels,
        "total": len(ordered_channels),
    }
    return ok(data)


@app.api_route("/api/v7/site/recommend", methods=["GET", "POST"])
@app.api_route("/api/v7/site/recommend/live", methods=["GET", "POST"])
@app.api_route("/api/v7/site/hot", methods=["GET", "POST"])
@app.api_route("/api/v7/site/carousel", methods=["GET", "POST"])
@app.api_route("/api/v7/site/column", methods=["GET", "POST"])
@app.api_route("/api/v7/site/theme", methods=["GET", "POST"])
@app.api_route("/api/v7/site/special", methods=["GET", "POST"])
@app.api_route("/api/v7/site/sub", methods=["GET", "POST"])
@app.api_route("/api/v7/site/app", methods=["GET", "POST"])
async def site_recommend(authorization: str | None = Header(default=None)):
    # Mix live + movies for home rails (skip adult in live preview)
    live = await senal(
        "GET",
        "/api/catalog",
        authorization,
        params={"type": "live", "page": 1, "limit": 80},
    )
    movies = await senal("GET", "/api/catalog?type=movie&limit=40", authorization)
    series = await senal("GET", "/api/catalog?type=series&limit=40", authorization)

    def safe_vod(resp: httpx.Response):
        if resp.status_code >= 400:
            return []
        items = catalog_items(resp.json())
        return [map_vod(it, i + 1) for i, it in enumerate(items)]

    live_raw = []
    if live.status_code < 400:
        live_raw = [
            it for it in catalog_items(live.json())
            if not is_adult_label(str(it.get("category") or it.get("group") or ""))
        ][:40]
    live_items = [map_channel(it, i + 1) for i, it in enumerate(live_raw)]
    movie_items = safe_vod(movies)
    series_items = safe_vod(series)

    blocks = [
        {"id": "live", "name": "En vivo", "title": "En vivo", "type": "live", "list": live_items, "data": live_items},
        {"id": "movies", "name": "Películas", "title": "Películas", "type": "vod", "list": movie_items, "data": movie_items},
        {"id": "series", "name": "Series", "title": "Series", "type": "vod", "list": series_items, "data": series_items},
    ]
    return ok({"list": blocks, "blocks": blocks, "banner": movie_items[:8], "data": blocks})


@app.api_route("/api/v7/site/query", methods=["GET", "POST"])
@app.api_route("/api/v7/site/filter", methods=["GET", "POST"])
@app.api_route("/api/v7/site/filters", methods=["GET", "POST"])
@app.api_route("/api/v7/site/search", methods=["GET", "POST"])
async def site_query(request: Request, authorization: str | None = Header(default=None)):
    q = request.query_params.get("q") or request.query_params.get("keyword") or request.query_params.get("query") or ""
    body = {}
    try:
        body = await request.json()
    except Exception:
        pass
    q = q or body.get("q") or body.get("keyword") or body.get("query") or ""
    type_ = request.query_params.get("type") or body.get("type") or "movie"

    if q:
        r = await senal("GET", "/api/search", authorization, params={"q": q, "limit": 80})
    else:
        r = await senal("GET", "/api/catalog", authorization, params={"type": type_, "limit": 80})

    if r.status_code >= 400:
        return fail(f"query HTTP {r.status_code}")
    items = catalog_items(r.json())
    mapped = [map_vod(it, i + 1) for i, it in enumerate(items)]
    return ok({"list": mapped, "items": mapped, "data": mapped, "total": len(mapped)})


@app.api_route("/api/v7/site/program", methods=["GET", "POST"])
@app.api_route("/api/v7/site/info", methods=["GET", "POST"])
async def site_program(request: Request, authorization: str | None = Header(default=None)):
    params = dict(request.query_params)
    body = {}
    try:
        body = await request.json()
    except Exception:
        pass
    cid = params.get("id") or params.get("channelId") or body.get("id") or body.get("channelId")
    if not cid:
        return ok({"list": [], "programs": []})
    # SEÑAL playback resolves stream URL
    r = await senal("POST", f"/api/playback/{cid}", authorization, json={})
    if r.status_code >= 400:
        return fail(f"playback HTTP {r.status_code}")
    payload = r.json()
    url = payload.get("url") or payload.get("streamUrl") or payload.get("playbackUrl") or payload.get("src")
    data = {
        "id": cid,
        "playUrl": url,
        "url": url,
        "source": url,
        "link": url,
        "headers": payload.get("headers") or {},
        "title": payload.get("title") or "",
        "logo": payload.get("logo") or "",
        "list": [],
        "programs": [],
    }
    return ok(data)


@app.api_route("/api/v7/epg", methods=["GET", "POST"])
async def epg():
    return ok({"list": [], "epg": [], "data": []})


@app.api_route("/api/v7/site/history", methods=["GET", "POST"])
@app.api_route("/api/v7/site/favourite", methods=["GET", "POST"])
@app.api_route("/api/v7/site/live/favourite", methods=["GET", "POST"])
async def local_lists():
    return ok({"list": [], "items": [], "data": []})


@app.api_route("/api/v7/static/{path:path}", methods=["GET"])
async def static_page(path: str):
    return Response(
        content=f"<html><body style='background:#0B0B0F;color:#fff;font-family:sans-serif;padding:2rem'><h1>SEÑAL</h1><p>{path}</p></body></html>",
        media_type="text/html",
    )


# Catch-all for other Magis relative paths under /api/v7
@app.api_route("/api/v7/{path:path}", methods=["GET", "POST", "PUT", "DELETE"])
async def catch_all(path: str):
    return ok({"list": [], "data": {}, "path": path})


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("main:app", host="0.0.0.0", port=PORT, reload=False)
