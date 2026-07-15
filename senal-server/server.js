#!/usr/bin/env node
/**
 * SEÑAL Server PRO 2.1 — compatible con el paquete Windows original
 * (INSTALAR-WINDOWS / INICIAR-SENAL) + panel: usuarios, logs, banner.
 *
 * Esquema DB compatible:
 *   { users[], devices[], content[], settings{}, banners[], logs[] }
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
const { v4: uuidv4 } = require("uuid");

const ROOT = __dirname;
const DATA = path.join(ROOT, "data");
const DB_FILE = path.join(DATA, "db.json");
const PUBLIC = path.join(ROOT, "public");
const LOG_MAX = 2000;

const PORT = Number(process.env.PORT || 3000);
const PUBLIC_BASE_URL = (process.env.PUBLIC_BASE_URL || `http://localhost:${PORT}`).replace(/\/$/, "");
const JWT_SECRET = process.env.JWT_SECRET || "senal-change-me";
const MASTER_USER = process.env.MASTER_USERNAME || "admin";
const MASTER_PASS = process.env.MASTER_PASSWORD || "admin123";
const PLAYBACK_MODE = process.env.PLAYBACK_MODE || "redirect";

fs.mkdirSync(DATA, { recursive: true });

function uid(prefix = "id") {
  return `${prefix}_${crypto.randomBytes(12).toString("hex")}`;
}

function loadDb() {
  if (!fs.existsSync(DB_FILE)) {
    const db = emptyDb();
    ensureMaster(db);
    saveDb(db);
    return db;
  }
  const raw = JSON.parse(fs.readFileSync(DB_FILE, "utf8"));
  return normalizeDb(raw);
}

function emptyDb() {
  return {
    users: [],
    devices: [],
    content: [],
    settings: { importedAt: null, importedFile: null },
    banners: [
      {
        id: uid("bnr"),
        title: "Bienvenido a SEÑAL",
        body: "Edita este aviso desde el panel → Banner.",
        imageUrl: "",
        active: true,
        updatedAt: new Date().toISOString()
      }
    ],
    logs: []
  };
}

function normalizeDb(raw) {
  const db = emptyDb();
  db.users = Array.isArray(raw.users) ? raw.users : [];
  db.devices = Array.isArray(raw.devices) ? raw.devices : [];
  // migrate nested devices → flat list
  for (const u of db.users) {
    if (Array.isArray(u.devices) && u.devices.length) {
      for (const d of u.devices) {
        db.devices.push({
          id: d.id || uid("dev"),
          userId: u.id,
          deviceId: d.deviceId || d.id,
          name: d.deviceName || d.name || "Dispositivo",
          createdAt: d.createdAt || new Date().toISOString(),
          lastSeenAt: d.lastSeenAt || null
        });
      }
      delete u.devices;
    }
  }
  db.content = Array.isArray(raw.content) ? raw.content : [];
  db.settings = { ...db.settings, ...(raw.settings || {}) };
  db.banners = Array.isArray(raw.banners) ? raw.banners : db.banners;
  db.logs = Array.isArray(raw.logs) ? raw.logs : [];
  ensureMaster(db);
  return db;
}

function ensureMaster(db) {
  const master = db.users.find((u) => u.role === "MASTER" || u.username === MASTER_USER);
  if (!master) {
    db.users.unshift({
      id: uid("usr"),
      username: MASTER_USER,
      passwordHash: bcrypt.hashSync(MASTER_PASS, 10),
      role: "MASTER",
      active: true,
      expiresAt: null,
      connectionLimit: 20,
      createdAt: new Date().toISOString()
    });
  }
}

let saveTimer = null;
function saveDb(db) {
  fs.writeFileSync(DB_FILE, JSON.stringify(db, null, 2));
}
function scheduleSave() {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => saveDb(db), 250);
}

let db = loadDb();
saveDb(db); // persist migrations

function logEvent(type, message, meta = {}) {
  db.logs.unshift({
    id: uid("log"),
    at: new Date().toISOString(),
    type,
    message,
    meta
  });
  if (db.logs.length > LOG_MAX) db.logs.length = LOG_MAX;
  scheduleSave();
}

const app = express();
app.use(cors());
app.use(express.json({ limit: "4mb" }));
app.use(express.urlencoded({ extended: true }));

const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 120 * 1024 * 1024 } });

function sign(user) {
  return jwt.sign(
    { sub: user.id, username: user.username, role: user.role },
    JWT_SECRET,
    { expiresIn: "7d" }
  );
}

function auth(req, res, next) {
  const h = req.headers.authorization || "";
  const token = h.startsWith("Bearer ") ? h.slice(7) : "";
  if (!token) return res.status(401).json({ error: "Sesión inválida" });
  try {
    req.user = jwt.verify(token, JWT_SECRET);
    next();
  } catch {
    return res.status(401).json({ error: "Sesión inválida" });
  }
}

function requireMaster(req, res, next) {
  if (req.user?.role !== "MASTER" && req.user?.role !== "ADMIN") {
    return res.status(403).json({ error: "Solo admin" });
  }
  next();
}

function isExpired(u) {
  if (!u.expiresAt) return false;
  return Date.now() > new Date(u.expiresAt).getTime();
}

function devicesOf(userId) {
  return db.devices.filter((d) => d.userId === userId);
}

function publicUser(u) {
  const devices = devicesOf(u.id);
  return {
    id: u.id,
    username: u.username,
    role: u.role,
    active: !!u.active,
    expiresAt: u.expiresAt || null,
    connectionLimit: u.connectionLimit ?? 1,
    devices,
    deviceCount: devices.length,
    createdAt: u.createdAt,
    expired: isExpired(u)
  };
}

function contentStats() {
  let live = 0;
  let movies = 0;
  let series = 0;
  for (const c of db.content) {
    const t = String(c.type || "").toUpperCase();
    if (t === "SERIES") series += 1;
    else if (t === "MOVIE" || t === "VOD") movies += 1;
    else live += 1;
  }
  return { live, movies, series, total: db.content.length };
}

// ---------- Public ----------
app.get("/api/health", (_req, res) => {
  const s = contentStats();
  res.json({
    ok: true,
    app: "Señal Server",
    version: "2.1.0",
    content: s.total,
    users: db.users.length,
    devices: db.devices.length,
    banners: db.banners.filter((b) => b.active !== false).length,
    publicBaseUrl: PUBLIC_BASE_URL
  });
});

app.get("/api/banner", (_req, res) => {
  const items = db.banners
    .filter((b) => b.active !== false)
    .map((b) => ({
      id: b.id,
      title: b.title,
      body: b.body,
      message: b.body,
      imageUrl: b.imageUrl || "",
      active: true
    }));
  res.json({ items, enabled: items.length > 0 });
});

app.post("/api/auth/login", async (req, res) => {
  const { username, password, deviceId, deviceName } = req.body || {};
  const ip = req.headers["x-forwarded-for"] || req.socket.remoteAddress || "";
  if (!username || !password) {
    return res.status(400).json({ error: "Usuario y contraseña requeridos" });
  }
  const user = db.users.find((u) => u.username === username);
  if (!user || !(await bcrypt.compare(password, user.passwordHash))) {
    logEvent("login_fail", `Login fallido: ${username}`, { username, ip });
    return res.status(401).json({ error: "Usuario o contraseña incorrectos" });
  }
  if (!user.active) {
    logEvent("login_blocked", `Usuario bloqueado: ${username}`, { userId: user.id, ip });
    return res.status(403).json({ error: "Usuario bloqueado" });
  }
  if (isExpired(user)) {
    logEvent("login_expired", `Suscripción vencida: ${username}`, { userId: user.id, ip });
    return res.status(403).json({ error: "Suscripción vencida" });
  }

  if (deviceId && user.role !== "MASTER") {
    const existing = db.devices.find((d) => d.userId === user.id && d.deviceId === deviceId);
    if (existing) {
      existing.lastSeenAt = new Date().toISOString();
      existing.name = deviceName || existing.name;
    } else {
      const limit = user.connectionLimit || 1;
      const count = devicesOf(user.id).length;
      if (count >= limit) {
        logEvent("device_limit", `Límite dispositivos: ${username}`, {
          userId: user.id,
          limit,
          ip
        });
        return res.status(403).json({ error: `Límite de dispositivos alcanzado (${limit})` });
      }
      db.devices.push({
        id: uid("dev"),
        userId: user.id,
        deviceId,
        name: deviceName || "Dispositivo",
        createdAt: new Date().toISOString(),
        lastSeenAt: new Date().toISOString()
      });
    }
    scheduleSave();
  }

  logEvent("login_ok", `Login: ${username}`, {
    userId: user.id,
    deviceId: deviceId || null,
    deviceName: deviceName || null,
    ip
  });

  res.json({
    token: sign(user),
    user: {
      id: user.id,
      username: user.username,
      role: user.role,
      expiresAt: user.expiresAt
    }
  });
});

app.post("/api/auth/change-password", auth, async (req, res) => {
  const user = db.users.find((u) => u.id === req.user.sub);
  if (!user) return res.status(404).json({ error: "Usuario no encontrado" });
  const current = req.body?.currentPassword || req.body?.oldPassword || "";
  const next = req.body?.newPassword || req.body?.password || "";
  if (!next || next.length < 4) {
    return res.status(400).json({ error: "Nueva contraseña inválida" });
  }
  if (!(await bcrypt.compare(current, user.passwordHash))) {
    return res.status(400).json({ error: "Contraseña actual incorrecta" });
  }
  user.passwordHash = await bcrypt.hash(next, 10);
  scheduleSave();
  logEvent("password_change", `Cambio de contraseña: ${user.username}`, { userId: user.id });
  res.json({ ok: true });
});

// ---------- Admin ----------
app.get("/api/admin/stats", auth, requireMaster, (_req, res) => {
  const s = contentStats();
  res.json({
    users: db.users.length,
    devices: db.devices.length,
    content: s.total,
    live: s.live,
    movies: s.movies,
    series: s.series,
    banners: db.banners.length,
    logs: db.logs.length
  });
});

app.get("/api/admin/users", auth, requireMaster, (_req, res) => {
  res.json(db.users.map(publicUser));
});

app.post("/api/admin/users", auth, requireMaster, async (req, res) => {
  const { username, password, connectionLimit, expiresAt, role } = req.body || {};
  if (!username || !password) {
    return res.status(400).json({ error: "Usuario y contraseña requeridos" });
  }
  if (db.users.some((u) => u.username === username)) {
    return res.status(400).json({ error: "El usuario ya existe" });
  }
  const user = {
    id: uid("usr"),
    username: String(username).trim(),
    passwordHash: await bcrypt.hash(String(password), 10),
    role: role === "ADMIN" ? "ADMIN" : "USER",
    active: true,
    connectionLimit: Math.max(1, Number(connectionLimit) || 1),
    expiresAt: expiresAt || null,
    createdAt: new Date().toISOString()
  };
  db.users.push(user);
  scheduleSave();
  logEvent("user_create", `Usuario creado: ${user.username}`, {
    userId: user.id,
    by: req.user.username
  });
  res.json(publicUser(user));
});

app.patch("/api/admin/users/:id", auth, requireMaster, async (req, res) => {
  const user = db.users.find((u) => u.id === req.params.id);
  if (!user) return res.status(404).json({ error: "No encontrado" });
  const { active, password, connectionLimit, expiresAt, username } = req.body || {};
  if (typeof active === "boolean") user.active = active;
  if (connectionLimit != null) user.connectionLimit = Math.max(1, Number(connectionLimit) || 1);
  if (expiresAt !== undefined) user.expiresAt = expiresAt || null;
  if (username) user.username = String(username).trim();
  if (password) user.passwordHash = await bcrypt.hash(String(password), 10);
  scheduleSave();
  logEvent("user_update", `Usuario actualizado: ${user.username}`, {
    userId: user.id,
    by: req.user.username,
    patch: { active, connectionLimit, expiresAt: expiresAt !== undefined }
  });
  res.json({ ok: true, user: publicUser(user) });
});

app.delete("/api/admin/users/:id", auth, requireMaster, (req, res) => {
  const user = db.users.find((u) => u.id === req.params.id);
  if (!user) return res.status(404).json({ error: "No encontrado" });
  if (user.role === "MASTER") return res.status(400).json({ error: "No se puede eliminar MASTER" });
  db.users = db.users.filter((u) => u.id !== req.params.id);
  db.devices = db.devices.filter((d) => d.userId !== user.id);
  scheduleSave();
  logEvent("user_delete", `Usuario eliminado: ${user.username}`, {
    userId: user.id,
    by: req.user.username
  });
  res.json({ ok: true });
});

app.delete("/api/admin/users/:id/devices", auth, requireMaster, (req, res) => {
  const user = db.users.find((u) => u.id === req.params.id);
  if (!user) return res.status(404).json({ error: "No encontrado" });
  const before = devicesOf(user.id).length;
  db.devices = db.devices.filter((d) => d.userId !== user.id);
  scheduleSave();
  logEvent("devices_clear", `Dispositivos liberados: ${user.username} (${before})`, {
    userId: user.id,
    by: req.user.username
  });
  res.json({ ok: true });
});

app.get("/api/admin/logs", auth, requireMaster, (req, res) => {
  const limit = Math.min(500, Math.max(1, Number(req.query.limit) || 150));
  const type = String(req.query.type || "").trim();
  const q = String(req.query.q || "").trim().toLowerCase();
  let rows = db.logs;
  if (type) rows = rows.filter((l) => l.type === type);
  if (q) {
    rows = rows.filter(
      (l) =>
        String(l.message || "").toLowerCase().includes(q) ||
        JSON.stringify(l.meta || {}).toLowerCase().includes(q)
    );
  }
  res.json({ total: rows.length, items: rows.slice(0, limit) });
});

app.delete("/api/admin/logs", auth, requireMaster, (req, res) => {
  db.logs = [];
  scheduleSave();
  logEvent("logs_clear", `Logs borrados por ${req.user.username}`, { by: req.user.username });
  res.json({ ok: true });
});

app.get("/api/admin/banners", auth, requireMaster, (_req, res) => {
  res.json(db.banners);
});

app.post("/api/admin/banners", auth, requireMaster, (req, res) => {
  const b = {
    id: uid("bnr"),
    title: String(req.body?.title || "Aviso").slice(0, 120),
    body: String(req.body?.body || req.body?.message || "").slice(0, 800),
    imageUrl: String(req.body?.imageUrl || "").slice(0, 500),
    active: req.body?.active !== false,
    updatedAt: new Date().toISOString()
  };
  db.banners.unshift(b);
  db.banners = db.banners.slice(0, 12);
  scheduleSave();
  logEvent("banner_create", `Banner: ${b.title}`, { bannerId: b.id, by: req.user.username });
  res.json(b);
});

app.put("/api/admin/banners", auth, requireMaster, (req, res) => {
  const items = Array.isArray(req.body?.items) ? req.body.items : [];
  db.banners = items.slice(0, 12).map((b) => ({
    id: b.id || uid("bnr"),
    title: String(b.title || "").slice(0, 120),
    body: String(b.body || b.message || "").slice(0, 800),
    imageUrl: String(b.imageUrl || "").slice(0, 500),
    active: b.active !== false,
    updatedAt: new Date().toISOString()
  }));
  scheduleSave();
  logEvent("banner_save", `Banners guardados (${db.banners.length})`, { by: req.user.username });
  res.json({ ok: true, items: db.banners });
});

app.delete("/api/admin/banners/:id", auth, requireMaster, (req, res) => {
  const before = db.banners.find((b) => b.id === req.params.id);
  db.banners = db.banners.filter((b) => b.id !== req.params.id);
  scheduleSave();
  logEvent("banner_delete", `Banner borrado: ${before?.title || req.params.id}`, {
    by: req.user.username
  });
  res.json({ ok: true });
});

app.post("/api/admin/import", auth, requireMaster, upload.single("playlist"), (req, res) => {
  if (!req.file) return res.status(400).json({ error: "Falta archivo playlist" });
  const text = req.file.buffer.toString("utf8");
  const lines = text.split(/\r?\n/);
  const items = [];
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
      n += 1;
      items.push({
        id: crypto.createHash("md5").update(`${pending.title}|${line}`).digest("hex").slice(0, 24),
        title: pending.title,
        type: pending.type,
        group: pending.group,
        poster: pending.poster,
        tvgId: pending.tvgId,
        url: line.trim(),
        createdAt: new Date().toISOString()
      });
      pending = null;
    }
  }
  db.content = items;
  db.settings = {
    importedAt: new Date().toISOString(),
    importedFile: req.file.originalname || "playlist.m3u"
  };
  fs.writeFileSync(path.join(DATA, "lista_importada.m3u"), text);
  scheduleSave();
  const s = contentStats();
  logEvent("m3u_import", `Import M3U: ${s.total} ítems`, {
    by: req.user.username,
    file: db.settings.importedFile
  });
  res.json({ ok: true, imported: s.total, live: s.live, movies: s.movies, series: s.series });
});

app.use(express.static(PUBLIC));
app.get("*", (_req, res) => {
  res.sendFile(path.join(PUBLIC, "index.html"));
});

app.listen(PORT, () => {
  console.log(`SEÑAL Server PRO 2.1 → http://localhost:${PORT}`);
  console.log(`Público: ${PUBLIC_BASE_URL}`);
  console.log(`Playback: ${PLAYBACK_MODE}`);
  console.log(`Master: ${MASTER_USER}`);
  logEvent("server_start", `Servidor iniciado en puerto ${PORT}`, { port: PORT });
});
