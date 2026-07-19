#!/usr/bin/env node
/**
 * SEÑAL Server 3.0 — Panel IPTV reseller
 * Usuarios · dispositivos · bouquets · catálogo con ORDEN editable · M3U · banner · logs
 */
require("dotenv").config();

const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const express = require("express");
const cors = require("cors");
const bcrypt = require("bcryptjs");
const jwt = require("jsonwebtoken");
const multer = require("multer");

const ROOT = __dirname;
const DATA = path.join(ROOT, "data");
const DB_FILE = path.join(DATA, "db.json");
const PUBLIC = path.join(ROOT, "public");
const LOG_MAX = 3000;

const PORT = Number(process.env.PORT || 3000);
const PUBLIC_BASE_URL = (process.env.PUBLIC_BASE_URL || `http://localhost:${PORT}`).replace(/\/$/, "");
const JWT_SECRET = process.env.JWT_SECRET || "senal-change-me";
const MASTER_USER = process.env.MASTER_USERNAME || "admin";
const MASTER_PASS = process.env.MASTER_PASSWORD || "admin123";

fs.mkdirSync(DATA, { recursive: true });

const uid = (p = "id") => `${p}_${crypto.randomBytes(10).toString("hex")}`;

function emptyDb() {
  return {
    users: [],
    devices: [],
    content: [],
    categoryOrder: [],
    bouquets: [{ id: "all", name: "Completo", categories: [], isDefault: true }],
    banners: [{
      id: uid("bnr"), title: "Bienvenido a SEÑAL", body: "Panel IPTV SEÑAL 3.0",
      imageUrl: "", active: true, updatedAt: new Date().toISOString()
    }],
    logs: [],
    settings: { importedAt: null, importedFile: null, playlistName: "SEÑAL" }
  };
}

function rebuildCatalogIndex(db) {
  const groups = new Map();
  for (const ch of db.content) {
    const g = String(ch.group || "Variados").trim() || "Variados";
    if (!groups.has(g)) groups.set(g, []);
    groups.get(g).push(ch);
  }
  for (const [, list] of groups) {
    list.sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0));
    list.forEach((ch, i) => { if (ch.sort == null) ch.sort = i; });
  }
  const seen = new Set();
  const order = [];
  for (const name of db.categoryOrder || []) {
    if (groups.has(name) && !seen.has(name)) { order.push(name); seen.add(name); }
  }
  for (const name of groups.keys()) {
    if (!seen.has(name)) { order.push(name); seen.add(name); }
  }
  db.categoryOrder = order;
}

function normalizeDb(raw) {
  const db = emptyDb();
  db.users = Array.isArray(raw.users) ? raw.users : [];
  db.devices = Array.isArray(raw.devices) ? raw.devices : [];
  for (const u of db.users) {
    if (Array.isArray(u.devices)) {
      for (const d of u.devices) {
        db.devices.push({
          id: d.id || uid("dev"), userId: u.id, deviceId: d.deviceId || d.id,
          name: d.deviceName || d.name || "Dispositivo",
          createdAt: d.createdAt || new Date().toISOString(), lastSeenAt: d.lastSeenAt || null
        });
      }
      delete u.devices;
    }
    if (!u.bouquetId) u.bouquetId = "all";
  }
  db.content = (Array.isArray(raw.content) ? raw.content : []).map((c, i) => ({
    id: c.id || uid("ch"),
    title: c.title || c.name || `Canal ${i + 1}`,
    group: String(c.group || "Variados").trim() || "Variados",
    type: c.type || "LIVE",
    url: c.url || "",
    poster: c.poster || c.logo || null,
    tvgId: c.tvgId || null,
    sort: typeof c.sort === "number" ? c.sort : i,
    hidden: !!c.hidden,
    createdAt: c.createdAt || new Date().toISOString()
  }));
  db.categoryOrder = Array.isArray(raw.categoryOrder) ? raw.categoryOrder : [];
  db.bouquets = Array.isArray(raw.bouquets) && raw.bouquets.length
    ? raw.bouquets
    : [{ id: "all", name: "Completo", categories: [], isDefault: true }];
  db.banners = Array.isArray(raw.banners) ? raw.banners : db.banners;
  db.logs = Array.isArray(raw.logs) ? raw.logs : [];
  db.settings = { ...db.settings, ...(raw.settings || {}) };
  rebuildCatalogIndex(db);
  ensureMaster(db);
  return db;
}

function loadDb() {
  if (!fs.existsSync(DB_FILE)) {
    const db = emptyDb();
    ensureMaster(db);
    saveDb(db);
    return db;
  }
  return normalizeDb(JSON.parse(fs.readFileSync(DB_FILE, "utf8")));
}

function ensureMaster(db) {
  if (!db.users.some((u) => u.role === "MASTER" || u.username === MASTER_USER)) {
    db.users.unshift({
      id: uid("usr"), username: MASTER_USER,
      passwordHash: bcrypt.hashSync(MASTER_PASS, 10),
      role: "MASTER", active: true, expiresAt: null,
      connectionLimit: 99, bouquetId: "all", createdAt: new Date().toISOString()
    });
  }
}

let saveTimer = null;
function saveDb(db) { fs.writeFileSync(DB_FILE, JSON.stringify(db, null, 2)); }
function scheduleSave() { clearTimeout(saveTimer); saveTimer = setTimeout(() => saveDb(db), 200); }

let db = loadDb();
saveDb(db);

function logEvent(type, message, meta = {}) {
  db.logs.unshift({ id: uid("log"), at: new Date().toISOString(), type, message, meta });
  if (db.logs.length > LOG_MAX) db.logs.length = LOG_MAX;
  scheduleSave();
}

function contentStats() {
  let live = 0, movies = 0, series = 0;
  for (const c of db.content) {
    if (c.hidden) continue;
    const t = String(c.type || "").toUpperCase();
    if (t === "SERIES") series++;
    else if (t === "MOVIE" || t === "VOD") movies++;
    else live++;
  }
  return { live, movies, series, total: db.content.filter((c) => !c.hidden).length };
}

function channelsInCategory(group) {
  return db.content
    .filter((c) => c.group === group)
    .sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0));
}

function bouquetForUser(user) {
  return db.bouquets.find((b) => b.id === user.bouquetId) || db.bouquets.find((b) => b.isDefault) || db.bouquets[0];
}

function filterForUser(user) {
  const b = bouquetForUser(user);
  if (!b || !b.categories?.length || b.id === "all") return db.content.filter((c) => !c.hidden);
  const allowed = new Set(b.categories);
  return db.content.filter((c) => !c.hidden && allowed.has(c.group));
}

function buildM3U(channels, header = true) {
  const lines = header ? ["#EXTM3U", `# Generated by SEÑAL Server 3.0 · ${new Date().toISOString()}`] : [];
  const byGroup = new Map();
  for (const ch of channels) {
    const g = ch.group || "Variados";
    if (!byGroup.has(g)) byGroup.set(g, []);
    byGroup.get(g).push(ch);
  }
  const order = db.categoryOrder.filter((g) => byGroup.has(g));
  for (const g of byGroup.keys()) if (!order.includes(g)) order.push(g);
  for (const group of order) {
    const list = byGroup.get(group).sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0));
    for (const ch of list) {
      const attrs = [
        'tvg-id="' + escAttr(ch.tvgId || "") + '"',
        'tvg-logo="' + escAttr(ch.poster || "") + '"',
        'group-title="' + escAttr(group) + '"'
      ].join(" ");
      lines.push(`#EXTINF:-1 ${attrs},${ch.title}`);
      lines.push(ch.url);
    }
  }
  return lines.join("\n") + "\n";
}

function escAttr(s) { return String(s || "").replace(/\\/g, "\\\\").replace(/"/g, '\\"'); }

function parseM3U(text) {
  const items = [];
  const lines = text.split(/\r?\n/);
  let pending = null;
  let n = 0;
  for (const line of lines) {
    if (line.startsWith("#EXTINF")) {
      const group = (/group-title="([^"]*)"/i.exec(line) || [])[1] || "Variados";
      const logo = (/tvg-logo="([^"]*)"/i.exec(line) || [])[1] || null;
      const tvgId = (/tvg-id="([^"]*)"/i.exec(line) || [])[1] || null;
      const title = line.includes(",") ? line.slice(line.lastIndexOf(",") + 1).trim() : `Canal ${n + 1}`;
      const hay = `${group} ${title}`.toLowerCase();
      let type = "LIVE";
      if (/serie|series/.test(hay)) type = "SERIES";
      else if (/pel[ií]cula|movie|cine|vod/.test(hay)) type = "MOVIE";
      pending = { title, group, poster: logo, tvgId, type };
    } else if (pending && line && !line.startsWith("#")) {
      items.push({
        id: uid("ch"), title: pending.title, group: pending.group.trim() || "Variados",
        type: pending.type, url: line.trim(), poster: pending.poster, tvgId: pending.tvgId,
        sort: n, hidden: false, createdAt: new Date().toISOString()
      });
      n++; pending = null;
    }
  }
  return items;
}

const app = express();
app.use(cors());
app.use(express.json({ limit: "8mb" }));
app.use(express.urlencoded({ extended: true }));
const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 150 * 1024 * 1024 } });

function sign(user) {
  return jwt.sign({ sub: user.id, username: user.username, role: user.role }, JWT_SECRET, { expiresIn: "7d" });
}

function auth(req, res, next) {
  const token = (req.headers.authorization || "").replace(/^Bearer\s+/i, "");
  if (!token) return res.status(401).json({ error: "Sesión inválida" });
  try { req.user = jwt.verify(token, JWT_SECRET); next(); }
  catch { return res.status(401).json({ error: "Sesión inválida" }); }
}

function requireMaster(req, res, next) {
  if (req.user?.role !== "MASTER" && req.user?.role !== "ADMIN") {
    return res.status(403).json({ error: "Solo admin" });
  }
  next();
}

function isExpired(u) { return u.expiresAt && Date.now() > new Date(u.expiresAt).getTime(); }
function devicesOf(userId) { return db.devices.filter((d) => d.userId === userId); }

function publicUser(u) {
  return {
    id: u.id, username: u.username, role: u.role, active: !!u.active,
    expiresAt: u.expiresAt || null, connectionLimit: u.connectionLimit ?? 1,
    bouquetId: u.bouquetId || "all",
    devices: devicesOf(u.id), deviceCount: devicesOf(u.id).length,
    createdAt: u.createdAt, expired: isExpired(u)
  };
}

// ---------- Public ----------
app.get("/api/health", (_req, res) => {
  const s = contentStats();
  res.json({
    ok: true, app: "Señal Server", version: "3.1.0",
    content: s.total, categories: db.categoryOrder.length,
    users: db.users.length, devices: db.devices.length,
    publicBaseUrl: PUBLIC_BASE_URL
  });
});

app.get("/api/banner", (_req, res) => {
  const items = db.banners.filter((b) => b.active !== false).map((b) => ({
    id: b.id, title: b.title, body: b.body, message: b.body,
    imageUrl: b.imageUrl || "", active: true
  }));
  res.json({ items, enabled: items.length > 0 });
});

/** Classic IPTV M3U URL: /get.php?username=X&password=Y&type=m3u_plus */
app.get("/get.php", async (req, res) => {
  const { username, password, type } = req.query;
  const user = db.users.find((u) => u.username === String(username || "").trim());
  if (!user || !(await bcrypt.compare(String(password || ""), user.passwordHash))) {
    return res.status(401).send("auth failed");
  }
  if (!user.active || isExpired(user)) return res.status(403).send("access denied");
  const channels = filterForUser(user);
  res.setHeader("Content-Type", "audio/x-mpegurl; charset=utf-8");
  res.setHeader("Content-Disposition", `attachment; filename="${user.username}.m3u"`);
  res.send(buildM3U(channels));
});

app.get("/playlist.m3u", auth, (req, res) => {
  const user = db.users.find((u) => u.id === req.user.sub);
  if (!user) return res.status(404).send("user not found");
  const channels = user.role === "MASTER" || user.role === "ADMIN"
    ? db.content.filter((c) => !c.hidden)
    : filterForUser(user);
  const body = buildM3U(channels);
  const etag = '"' + crypto.createHash("sha1").update(body).digest("hex") + '"';
  if (req.headers["if-none-match"] === etag) {
    res.status(304).end();
    return;
  }
  res.setHeader("ETag", etag);
  res.setHeader("Cache-Control", "private, max-age=60");
  res.setHeader("Content-Type", "audio/x-mpegurl; charset=utf-8");
  // Gzip manual para clientes que envían Accept-Encoding: gzip
  const accept = String(req.headers["accept-encoding"] || "");
  if (accept.includes("gzip")) {
    const zlib = require("zlib");
    const gz = zlib.gzipSync(Buffer.from(body, "utf8"));
    res.setHeader("Content-Encoding", "gzip");
    res.setHeader("Content-Length", gz.length);
    res.end(gz);
  } else {
    res.send(body);
  }
});

/** Categorías ligeras para la APK (instantáneo vs parsear M3U completo). */
app.get("/api/app/categories", auth, (req, res) => {
  const user = db.users.find((u) => u.id === req.user.sub);
  if (!user) return res.status(404).json({ error: "No encontrado" });
  const channels = user.role === "MASTER" || user.role === "ADMIN"
    ? db.content.filter((c) => !c.hidden)
    : filterForUser(user);
  const counts = new Map();
  for (const c of channels) counts.set(c.group, (counts.get(c.group) || 0) + 1);
  const order = db.categoryOrder.filter((g) => counts.has(g));
  for (const g of counts.keys()) if (!order.includes(g)) order.push(g);
  res.json({
    categories: order.map((name) => ({ id: name, name, title: name, count: counts.get(name) || 0 })),
    total: channels.length
  });
});

app.post("/api/auth/login", async (req, res) => {
  const { username, password, deviceId, deviceName } = req.body || {};
  const ip = req.headers["x-forwarded-for"] || req.socket.remoteAddress || "";
  if (!username || !password) return res.status(400).json({ error: "Usuario y contraseña requeridos" });
  const user = db.users.find((u) => u.username === username);
  if (!user || !(await bcrypt.compare(password, user.passwordHash))) {
    logEvent("login_fail", `Login fallido: ${username}`, { username, ip });
    return res.status(401).json({ error: "Usuario o contraseña incorrectos" });
  }
  if (!user.active) return res.status(403).json({ error: "Usuario bloqueado" });
  if (isExpired(user)) return res.status(403).json({ error: "Suscripción vencida" });

  if (deviceId && user.role !== "MASTER") {
    const ex = db.devices.find((d) => d.userId === user.id && d.deviceId === deviceId);
    if (ex) { ex.lastSeenAt = new Date().toISOString(); ex.name = deviceName || ex.name; }
    else {
      const limit = user.connectionLimit || 1;
      if (devicesOf(user.id).length >= limit) {
        logEvent("device_limit", `Límite: ${username}`, { userId: user.id, limit, ip });
        return res.status(403).json({ error: `Límite de dispositivos (${limit})` });
      }
      db.devices.push({
        id: uid("dev"), userId: user.id, deviceId, name: deviceName || "Dispositivo",
        createdAt: new Date().toISOString(), lastSeenAt: new Date().toISOString()
      });
    }
    scheduleSave();
  }
  logEvent("login_ok", `Login: ${username}`, { userId: user.id, deviceId, ip });
  res.json({
    token: sign(user),
    user: { id: user.id, username: user.username, role: user.role, expiresAt: user.expiresAt },
    playlistUrl: `${PUBLIC_BASE_URL}/get.php?username=${encodeURIComponent(user.username)}&password=***&type=m3u_plus`
  });
});

app.post("/api/auth/change-password", auth, async (req, res) => {
  const user = db.users.find((u) => u.id === req.user.sub);
  if (!user) return res.status(404).json({ error: "No encontrado" });
  const cur = req.body?.currentPassword || req.body?.oldPassword || "";
  const next = req.body?.newPassword || req.body?.password || "";
  if (!next || next.length < 4) return res.status(400).json({ error: "Contraseña inválida" });
  if (!(await bcrypt.compare(cur, user.passwordHash))) {
    return res.status(400).json({ error: "Contraseña actual incorrecta" });
  }
  user.passwordHash = await bcrypt.hash(next, 10);
  scheduleSave();
  res.json({ ok: true });
});

// ---------- Admin stats / users (unchanged core) ----------
app.get("/api/admin/stats", auth, requireMaster, (_req, res) => {
  const s = contentStats();
  res.json({
    users: db.users.length, devices: db.devices.length,
    content: s.total, live: s.live, movies: s.movies, series: s.series,
    categories: db.categoryOrder.length, bouquets: db.bouquets.length,
    banners: db.banners.length, logs: db.logs.length
  });
});

app.get("/api/admin/users", auth, requireMaster, (_req, res) => res.json(db.users.map(publicUser)));

app.post("/api/admin/users", auth, requireMaster, async (req, res) => {
  const { username, password, connectionLimit, expiresAt, role, bouquetId } = req.body || {};
  if (!username || !password) return res.status(400).json({ error: "Usuario y contraseña requeridos" });
  if (db.users.some((u) => u.username === username)) return res.status(400).json({ error: "Ya existe" });
  const user = {
    id: uid("usr"), username: String(username).trim(),
    passwordHash: await bcrypt.hash(String(password), 10),
    role: role === "ADMIN" ? "ADMIN" : "USER", active: true,
    connectionLimit: Math.max(1, Number(connectionLimit) || 1),
    expiresAt: expiresAt || null, bouquetId: bouquetId || "all",
    createdAt: new Date().toISOString()
  };
  db.users.push(user);
  scheduleSave();
  logEvent("user_create", `Usuario: ${user.username}`, { userId: user.id, by: req.user.username });
  res.json(publicUser(user));
});

app.patch("/api/admin/users/:id", auth, requireMaster, async (req, res) => {
  const user = db.users.find((u) => u.id === req.params.id);
  if (!user) return res.status(404).json({ error: "No encontrado" });
  const { active, password, connectionLimit, expiresAt, username, bouquetId } = req.body || {};
  if (typeof active === "boolean") user.active = active;
  if (connectionLimit != null) user.connectionLimit = Math.max(1, Number(connectionLimit) || 1);
  if (expiresAt !== undefined) user.expiresAt = expiresAt || null;
  if (username) user.username = String(username).trim();
  if (bouquetId) user.bouquetId = bouquetId;
  if (password) user.passwordHash = await bcrypt.hash(String(password), 10);
  scheduleSave();
  logEvent("user_update", `Usuario: ${user.username}`, { userId: user.id, by: req.user.username });
  res.json({ ok: true, user: publicUser(user) });
});

app.delete("/api/admin/users/:id", auth, requireMaster, (req, res) => {
  const user = db.users.find((u) => u.id === req.params.id);
  if (!user || user.role === "MASTER") return res.status(400).json({ error: "No permitido" });
  db.users = db.users.filter((u) => u.id !== req.params.id);
  db.devices = db.devices.filter((d) => d.userId !== user.id);
  scheduleSave();
  res.json({ ok: true });
});

app.delete("/api/admin/users/:id/devices", auth, requireMaster, (req, res) => {
  const user = db.users.find((u) => u.id === req.params.id);
  if (!user) return res.status(404).json({ error: "No encontrado" });
  db.devices = db.devices.filter((d) => d.userId !== user.id);
  scheduleSave();
  res.json({ ok: true });
});

// ---------- Bouquets (paquetes IPTV) ----------
app.get("/api/admin/bouquets", auth, requireMaster, (_req, res) => res.json(db.bouquets));

app.post("/api/admin/bouquets", auth, requireMaster, (req, res) => {
  const { name, categories } = req.body || {};
  if (!name) return res.status(400).json({ error: "Nombre requerido" });
  const b = { id: uid("bqt"), name: String(name).slice(0, 80), categories: categories || [], isDefault: false };
  db.bouquets.push(b);
  scheduleSave();
  logEvent("bouquet_create", `Paquete: ${b.name}`, { by: req.user.username });
  res.json(b);
});

app.patch("/api/admin/bouquets/:id", auth, requireMaster, (req, res) => {
  const b = db.bouquets.find((x) => x.id === req.params.id);
  if (!b) return res.status(404).json({ error: "No encontrado" });
  if (req.body.name) b.name = String(req.body.name).slice(0, 80);
  if (Array.isArray(req.body.categories)) b.categories = req.body.categories;
  scheduleSave();
  res.json(b);
});

app.delete("/api/admin/bouquets/:id", auth, requireMaster, (req, res) => {
  if (req.params.id === "all") return res.status(400).json({ error: "No se puede borrar Completo" });
  db.bouquets = db.bouquets.filter((b) => b.id !== req.params.id);
  for (const u of db.users) if (u.bouquetId === req.params.id) u.bouquetId = "all";
  scheduleSave();
  res.json({ ok: true });
});

// ---------- Catalog & ORDER ----------
app.get("/api/admin/catalog", auth, requireMaster, (_req, res) => {
  const categories = db.categoryOrder.map((name) => ({
    name,
    count: db.content.filter((c) => c.group === name && !c.hidden).length,
    total: db.content.filter((c) => c.group === name).length
  }));
  res.json({ categoryOrder: db.categoryOrder, categories, total: db.content.length });
});

app.get("/api/admin/catalog/:group/channels", auth, requireMaster, (req, res) => {
  const group = decodeURIComponent(req.params.group);
  const q = String(req.query.q || "").toLowerCase();
  let list = channelsInCategory(group);
  if (q) list = list.filter((c) => c.title.toLowerCase().includes(q));
  res.json(list.map((c) => ({
    id: c.id, title: c.title, group: c.group, url: c.url,
    poster: c.poster, tvgId: c.tvgId, type: c.type, sort: c.sort, hidden: !!c.hidden
  })));
});

/** Reorder categories (drag) */
app.put("/api/admin/catalog/categories/order", auth, requireMaster, (req, res) => {
  const order = req.body?.order;
  if (!Array.isArray(order)) return res.status(400).json({ error: "order[] requerido" });
  const valid = new Set(db.content.map((c) => c.group));
  db.categoryOrder = order.filter((g) => valid.has(g));
  for (const g of valid) if (!db.categoryOrder.includes(g)) db.categoryOrder.push(g);
  scheduleSave();
  logEvent("catalog_order", `Orden categorías actualizado`, { by: req.user.username });
  res.json({ ok: true, categoryOrder: db.categoryOrder });
});

/** Reorder channels inside a category (drag) */
app.put("/api/admin/catalog/channels/order", auth, requireMaster, (req, res) => {
  const { group, ids } = req.body || {};
  if (!group || !Array.isArray(ids)) return res.status(400).json({ error: "group + ids[]" });
  ids.forEach((id, i) => {
    const ch = db.content.find((c) => c.id === id && c.group === group);
    if (ch) ch.sort = i;
  });
  scheduleSave();
  logEvent("channel_order", `Orden canales: ${group}`, { group, count: ids.length, by: req.user.username });
  res.json({ ok: true });
});

/** Rename a category (all channels + categoryOrder + bouquets) */
app.patch("/api/admin/catalog/categories/rename", auth, requireMaster, (req, res) => {
  const { from, to } = req.body || {};
  if (!from || !to) return res.status(400).json({ error: "from y to requeridos" });
  const toName = String(to).trim();
  if (!toName) return res.status(400).json({ error: "Nombre inválido" });
  if (from === toName) return res.json({ ok: true, categoryOrder: db.categoryOrder });
  for (const ch of db.content) {
    if (ch.group === from) ch.group = toName;
  }
  db.categoryOrder = db.categoryOrder.map((g) => (g === from ? toName : g));
  for (const b of db.bouquets) {
    if (Array.isArray(b.categories)) {
      b.categories = b.categories.map((c) => (c === from ? toName : c));
    }
  }
  rebuildCatalogIndex(db);
  scheduleSave();
  logEvent("category_rename", `Categoría renombrada: ${from} → ${toName}`, { by: req.user.username });
  res.json({ ok: true, categoryOrder: db.categoryOrder });
});

/** Delete a category and all its channels */
app.delete("/api/admin/catalog/categories/:groupEncoded", auth, requireMaster, (req, res) => {
  const group = decodeURIComponent(req.params.groupEncoded);
  db.content = db.content.filter((c) => c.group !== group);
  db.categoryOrder = db.categoryOrder.filter((g) => g !== group);
  for (const b of db.bouquets) {
    if (Array.isArray(b.categories)) {
      b.categories = b.categories.filter((c) => c !== group);
    }
  }
  rebuildCatalogIndex(db);
  scheduleSave();
  logEvent("category_delete", `Categoría eliminada: ${group}`, { by: req.user.username });
  res.json({ ok: true });
});

app.patch("/api/admin/channels/:id", auth, requireMaster, (req, res) => {
  const ch = db.content.find((c) => c.id === req.params.id);
  if (!ch) return res.status(404).json({ error: "No encontrado" });
  const oldGroup = ch.group;
  if (req.body.title) ch.title = String(req.body.title).slice(0, 200);
  if (req.body.url) ch.url = String(req.body.url);
  if (req.body.group) ch.group = String(req.body.group).trim() || ch.group;
  if (typeof req.body.hidden === "boolean") ch.hidden = req.body.hidden;
  if (typeof req.body.sort === "number") ch.sort = req.body.sort;
  rebuildCatalogIndex(db);
  if (oldGroup !== ch.group) {
    const max = Math.max(-1, ...db.content.filter((c) => c.group === ch.group).map((c) => c.sort ?? 0));
    ch.sort = max + 1;
  }
  scheduleSave();
  res.json(ch);
});

app.delete("/api/admin/channels/:id", auth, requireMaster, (req, res) => {
  db.content = db.content.filter((c) => c.id !== req.params.id);
  rebuildCatalogIndex(db);
  scheduleSave();
  res.json({ ok: true });
});

app.post("/api/admin/import", auth, requireMaster, upload.single("playlist"), (req, res) => {
  if (!req.file) return res.status(400).json({ error: "Falta archivo" });
  const text = req.file.buffer.toString("utf8");
  db.content = parseM3U(text);
  rebuildCatalogIndex(db);
  db.settings.importedAt = new Date().toISOString();
  db.settings.importedFile = req.file.originalname || "playlist.m3u";
  const importedPath = path.join(DATA, "lista_importada.m3u");
  const m3uBody = buildM3U(db.content);
  fs.writeFileSync(importedPath, m3uBody);
  // Keep the public URL used by SEÑAL APKs in sync with the imported file.
  const downloadsDir = path.join(PUBLIC, "downloads");
  fs.mkdirSync(downloadsDir, { recursive: true });
  fs.writeFileSync(path.join(downloadsDir, "lista.m3u"), m3uBody);
  fs.writeFileSync(path.join(downloadsDir, "lista_importada.m3u"), m3uBody);
  scheduleSave();
  const s = contentStats();
  logEvent("m3u_import", `Import: ${db.content.length} canales`, { by: req.user.username, file: db.settings.importedFile });
  res.json({ ok: true, imported: db.content.length, live: s.live, movies: s.movies, series: s.series, categories: db.categoryOrder.length });
});

app.get("/api/admin/export.m3u", auth, requireMaster, (_req, res) => {
  const m3u = buildM3U(db.content.filter((c) => !c.hidden));
  res.setHeader("Content-Type", "audio/x-mpegurl; charset=utf-8");
  res.setHeader("Content-Disposition", 'attachment; filename="senal-ordenado.m3u"');
  res.send(m3u);
});

// ---------- Logs & Banners ----------
app.get("/api/admin/logs", auth, requireMaster, (req, res) => {
  const limit = Math.min(500, Number(req.query.limit) || 200);
  let rows = db.logs;
  if (req.query.type) rows = rows.filter((l) => l.type === req.query.type);
  if (req.query.q) {
    const q = String(req.query.q).toLowerCase();
    rows = rows.filter((l) => String(l.message).toLowerCase().includes(q));
  }
  res.json({ total: rows.length, items: rows.slice(0, limit) });
});

app.delete("/api/admin/logs", auth, requireMaster, (req, res) => {
  db.logs = []; scheduleSave(); res.json({ ok: true });
});

app.get("/api/admin/banners", auth, requireMaster, (_req, res) => res.json(db.banners));

app.post("/api/admin/banners", auth, requireMaster, (req, res) => {
  const b = {
    id: uid("bnr"), title: String(req.body?.title || "Aviso").slice(0, 120),
    body: String(req.body?.body || "").slice(0, 800),
    imageUrl: String(req.body?.imageUrl || "").slice(0, 500),
    active: req.body?.active !== false, updatedAt: new Date().toISOString()
  };
  db.banners.unshift(b);
  db.banners = db.banners.slice(0, 12);
  scheduleSave();
  res.json(b);
});

app.put("/api/admin/banners", auth, requireMaster, (req, res) => {
  const items = Array.isArray(req.body?.items) ? req.body.items : [];
  db.banners = items.slice(0, 12).map((b) => ({
    id: b.id || uid("bnr"), title: String(b.title || "").slice(0, 120),
    body: String(b.body || "").slice(0, 800), imageUrl: String(b.imageUrl || "").slice(0, 500),
    active: b.active !== false, updatedAt: new Date().toISOString()
  }));
  scheduleSave();
  res.json({ ok: true, items: db.banners });
});

app.delete("/api/admin/banners/:id", auth, requireMaster, (req, res) => {
  db.banners = db.banners.filter((b) => b.id !== req.params.id);
  scheduleSave();
  res.json({ ok: true });
});

// Prefer data/lista_importada.m3u (panel import) for the SEÑAL APK playlist URLs.
// Falls back to public/downloads copies if the import file is missing.
function sendCanonicalLista(req, res) {
  const imported = path.join(DATA, "lista_importada.m3u");
  const publicLista = path.join(PUBLIC, "downloads", "lista.m3u");
  const publicImported = path.join(PUBLIC, "downloads", "lista_importada.m3u");
  const file = [imported, publicImported, publicLista].find((p) => fs.existsSync(p) && fs.statSync(p).size > 32);
  if (!file) return res.status(404).type("text/plain").send("Playlist not found");
  res.setHeader("Content-Type", "audio/x-mpegurl; charset=utf-8");
  res.setHeader("Cache-Control", "no-cache");
  return res.sendFile(path.resolve(file));
}
app.get("/downloads/lista.m3u", sendCanonicalLista);
app.get("/downloads/lista_importada.m3u", sendCanonicalLista);

app.use(express.static(PUBLIC));
app.get("*", (_req, res) => res.sendFile(path.join(PUBLIC, "index.html")));

app.listen(PORT, () => {
  console.log(`SEÑAL Server IPTV 3.1 → http://localhost:${PORT}`);
  console.log(`M3U: ${PUBLIC_BASE_URL}/get.php?username=USER&password=PASS&type=m3u_plus`);
  console.log(`Master: ${MASTER_USER}`);
  logEvent("server_start", `Servidor 3.0 puerto ${PORT}`, { port: PORT });
});
