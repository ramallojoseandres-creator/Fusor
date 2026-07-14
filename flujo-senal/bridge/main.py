#!/usr/bin/env python3
"""
Magis/FLUJO /api/v7 compatibility bridge → SEÑAL REST API.

The forked APK points host adfereredadasww.ai → senalapi-origen.tv (same length).
Point DNS / hosts for senalapi-origen.tv to this service (or proxy 80→PORT).

Upstream: SENAL_BASE_URL (default http://185.192.20.245:3000)
"""

from __future__ import annotations

import os
import time
import uuid
from typing import Any

import httpx
from fastapi import FastAPI, Header, Request, Response
from fastapi.responses import JSONResponse

SENAL = os.environ.get("SENAL_BASE_URL", "http://185.192.20.245:3000").rstrip("/")
PORT = int(os.environ.get("PORT", "8080"))

app = FastAPI(title="SEÑAL Magis-compat bridge", version="1.0.0")


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
    r = await senal("GET", "/api/catalog?type=live&limit=500", authorization)
    if r.status_code >= 400:
        return fail(f"catalog live HTTP {r.status_code}")
    payload = r.json()
    items = catalog_items(payload)
    channels = [map_channel(it, i + 1) for i, it in enumerate(items)]

    # Magis often expects categories → channels
    buckets: dict[str, list] = {}
    for ch in channels:
        buckets.setdefault(ch["category"], []).append(ch)
    categories = [
        {
            "id": name,
            "categoryId": name,
            "name": name,
            "title": name,
            "list": chans,
            "channels": chans,
            "data": chans,
        }
        for name, chans in buckets.items()
    ]
    data = {
        "list": categories,
        "categories": categories,
        "channels": channels,
        "items": channels,
        "total": len(channels),
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
    # Mix live + movies for home rails
    live = await senal("GET", "/api/catalog?type=live&limit=40", authorization)
    movies = await senal("GET", "/api/catalog?type=movie&limit=40", authorization)
    series = await senal("GET", "/api/catalog?type=series&limit=40", authorization)

    def safe_items(resp: httpx.Response, kind: str):
        if resp.status_code >= 400:
            return []
        items = catalog_items(resp.json())
        if kind == "live":
            return [map_channel(it, i + 1) for i, it in enumerate(items)]
        return [map_vod(it, i + 1) for i, it in enumerate(items)]

    live_items = safe_items(live, "live")
    movie_items = safe_items(movies, "vod")
    series_items = safe_items(series, "vod")

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
