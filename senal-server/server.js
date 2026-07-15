#!/usr/bin/env node
/**
 * SEÑAL Server 2.0 — auth, users, device limits, expiry, M3U import, news banners.
 * Deploy: copy this folder to the VPS, npm install && npm start (PORT=3000).
 */
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
const PORT = Number(process.env.PORT || 3000);
const JWT_SECRET = process.env.SENAL_JWT_SECRET || "senal-change-me-in-production";
const MASTER_USER = process.env.SENAL_MASTER_USER || "admin";
const MASTER_PASS = process.env.SENAL_MASTER_PASS || "admin123";

fs.mkdirSync(DATA, { recursive: true });

function loadDb() {
  if (!fs.existsSync(DB_FILE)) {
    const hash = bcrypt.hashSync(MASTER_PASS, 10);
    const db = {
      users: [
        {
          id: "master",
          username: MASTER_USER,
          passwordHash: hash,
          role: "MASTER",
          active: true,
          connectionLimit: 99,
          expiresAt: null,
          devices: [],
          createdAt: new Date().toISOString()
        }
      ],
      content: { live: 0, movies: 0, series: 0, total: 0 },
      banners: [
        {
          id: uuidv4(),
          title: "Bienvenido a SEÑAL",
          body: "Edita este aviso desde el panel admin.",
          imageUrl: "",
          active: true,
          updatedAt: new Date().toISOString()
        }
      ]
    };
    saveDb(db);
    return db;
  }
  return JSON.parse(fs.readFileSync(DB_FILE, "utf8"));
}

function saveDb(db) {
  fs.writeFileSync(DB_FILE, JSON.stringify(db, null, 2));
}

let db = loadDb();

const app = express();
app.use(cors());
app.use(express.json({ limit: "2mb" }));
app.use(express.static(PUBLIC));

const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 80 * 1024 * 1024 } });

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

function publicUser(u) {
  return {
    id: u.id,
    username: u.username,
    role: u.role,
    active: !!u.active,
    connectionLimit: u.connectionLimit ?? 1,
    expiresAt: u.expiresAt || null,
    devices: (u.devices || []).map((d) => ({
      id: d.id,
      deviceId: d.deviceId,
      deviceName: d.deviceName,
      lastSeenAt: d.lastSeenAt
    })),
    createdAt: u.createdAt
  };
}

function isExpired(u) {
  if (!u.expiresAt) return false;
  return Date.now() > new Date(u.expiresAt).getTime();
}

app.get("/api/health", (_req, res) => {
  res.json({
    ok: true,
    app: "Señal Server",
    version: "2.0.0",
    content: db.content?.total || 0,
    users: db.users.length,
    banners: (db.banners || []).filter((b) => b.active).length
  });
});

/** Public banner feed for Android / iOS clients. */
app.get("/api/banner", (_req, res) => {
  const items = (db.banners || [])
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
  if (!username || !password) {
    return res.status(400).json({ error: "Usuario y contraseña requeridos" });
  }
  const user = db.users.find((u) => u.username === username);
  if (!user || !(await bcrypt.compare(password, user.passwordHash))) {
    return res.status(401).json({ error: "Usuario o contraseña incorrectos" });
  }
  if (!user.active) return res.status(403).json({ error: "Usuario bloqueado" });
  if (isExpired(user)) return res.status(403).json({ error: "Suscripción vencida" });

  user.devices = user.devices || [];
  if (deviceId && user.role !== "MASTER") {
    const existing = user.devices.find((d) => d.deviceId === deviceId);
    if (existing) {
      existing.lastSeenAt = new Date().toISOString();
      existing.deviceName = deviceName || existing.deviceName;
    } else {
      if (user.devices.length >= (user.connectionLimit || 1)) {
        return res.status(403).json({
          error: `Límite de dispositivos alcanzado (${user.connectionLimit})`
        });
      }
      user.devices.push({
        id: uuidv4(),
        deviceId,
        deviceName: deviceName || "Dispositivo",
        lastSeenAt: new Date().toISOString()
      });
    }
    saveDb(db);
  }

  const token = sign(user);
  res.json({
    token,
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
  saveDb(db);
  res.json({ ok: true });
});

app.get("/api/admin/stats", auth, requireMaster, (_req, res) => {
  const devices = db.users.reduce((n, u) => n + (u.devices?.length || 0), 0);
  res.json({
    users: db.users.length,
    devices,
    content: db.content?.total || 0,
    live: db.content?.live || 0,
    movies: db.content?.movies || 0,
    series: db.content?.series || 0,
    banners: (db.banners || []).length
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
    id: uuidv4(),
    username: String(username).trim(),
    passwordHash: await bcrypt.hash(String(password), 10),
    role: role === "ADMIN" ? "ADMIN" : "USER",
    active: true,
    connectionLimit: Math.max(1, Number(connectionLimit) || 1),
    expiresAt: expiresAt || null,
    devices: [],
    createdAt: new Date().toISOString()
  };
  db.users.push(user);
  saveDb(db);
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
  saveDb(db);
  res.json({ ok: true, user: publicUser(user) });
});

app.delete("/api/admin/users/:id", auth, requireMaster, (req, res) => {
  const user = db.users.find((u) => u.id === req.params.id);
  if (!user) return res.status(404).json({ error: "No encontrado" });
  if (user.role === "MASTER") return res.status(400).json({ error: "No se puede eliminar MASTER" });
  db.users = db.users.filter((u) => u.id !== req.params.id);
  saveDb(db);
  res.json({ ok: true });
});

app.delete("/api/admin/users/:id/devices", auth, requireMaster, (req, res) => {
  const user = db.users.find((u) => u.id === req.params.id);
  if (!user) return res.status(404).json({ error: "No encontrado" });
  user.devices = [];
  saveDb(db);
  res.json({ ok: true });
});

app.get("/api/admin/banners", auth, requireMaster, (_req, res) => {
  res.json(db.banners || []);
});

app.put("/api/admin/banners", auth, requireMaster, (req, res) => {
  const items = Array.isArray(req.body?.items) ? req.body.items : [];
  db.banners = items.slice(0, 8).map((b) => ({
    id: b.id || uuidv4(),
    title: String(b.title || "").slice(0, 120),
    body: String(b.body || b.message || "").slice(0, 500),
    imageUrl: String(b.imageUrl || "").slice(0, 500),
    active: b.active !== false,
    updatedAt: new Date().toISOString()
  }));
  saveDb(db);
  res.json({ ok: true, items: db.banners });
});

app.post("/api/admin/banners", auth, requireMaster, (req, res) => {
  const b = {
    id: uuidv4(),
    title: String(req.body?.title || "Aviso").slice(0, 120),
    body: String(req.body?.body || req.body?.message || "").slice(0, 500),
    imageUrl: String(req.body?.imageUrl || "").slice(0, 500),
    active: req.body?.active !== false,
    updatedAt: new Date().toISOString()
  };
  db.banners = db.banners || [];
  db.banners.unshift(b);
  db.banners = db.banners.slice(0, 8);
  saveDb(db);
  res.json(b);
});

app.delete("/api/admin/banners/:id", auth, requireMaster, (req, res) => {
  db.banners = (db.banners || []).filter((b) => b.id !== req.params.id);
  saveDb(db);
  res.json({ ok: true });
});

app.post("/api/admin/import", auth, requireMaster, upload.single("playlist"), (req, res) => {
  if (!req.file) return res.status(400).json({ error: "Falta archivo playlist" });
  const text = req.file.buffer.toString("utf8");
  const lines = text.split(/\r?\n/);
  let live = 0;
  let movies = 0;
  let series = 0;
  let pendingGroup = "";
  for (const line of lines) {
    if (line.startsWith("#EXTINF")) {
      const m = /group-title="([^"]*)"/i.exec(line);
      pendingGroup = (m?.[1] || "").toLowerCase();
      const name = line.includes(",") ? line.slice(line.lastIndexOf(",") + 1).toLowerCase() : "";
      const hay = `${pendingGroup} ${name}`;
      if (/serie|series/.test(hay)) series += 1;
      else if (/pel[ií]cula|movie|cine|vod/.test(hay)) movies += 1;
      else live += 1;
    }
  }
  const total = live + movies + series;
  db.content = { live, movies, series, total };
  const out = path.join(DATA, "lista_importada.m3u");
  fs.writeFileSync(out, text);
  saveDb(db);
  res.json({ ok: true, imported: total, live, movies, series });
});

app.get("*", (_req, res) => {
  res.sendFile(path.join(PUBLIC, "index.html"));
});

app.listen(PORT, () => {
  console.log(`SEÑAL Server 2.0 on :${PORT}`);
  console.log(`Master: ${MASTER_USER} / (SENAL_MASTER_PASS or admin123)`);
});
