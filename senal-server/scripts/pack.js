#!/usr/bin/env node
const fs = require("fs");
const path = require("path");
const { execSync } = require("child_process");

const root = path.join(__dirname, "..");
const outDir = path.join(root, "..", "dist");
const zipPath = path.join(outDir, "Senal-Server-PRO.zip");

fs.mkdirSync(outDir, { recursive: true });
if (fs.existsSync(zipPath)) fs.unlinkSync(zipPath);

const excludes = [
  "node_modules/*",
  ".env",
  "dist/*",
  ".git/*",
  "data/db.json",
  "data/*.m3u"
];
const args = excludes.map((x) => `-x "${x}"`).join(" ");

execSync(`cd "${root}" && zip -r "${zipPath}" . ${args}`, { stdio: "inherit" });
console.log("Created", zipPath);
