# -*- coding: utf-8 -*-
"""
====================================================================================================
TRUNG TÂM QUẢN LÝ BẢN QUYỀN SELL PVP & SELL KHO (LOCAL SERVER 127.0.0.1:5555)
====================================================================================================
- Quản lý tạo Key, khóa Key, mở khóa, chỉnh thời hạn (Vĩnh viễn hoặc ngày cụ thể).
- 1 Key chỉ dùng cho 1 máy (HWID binding) & 1 tài khoản duy nhất (Username binding).
- Hỗ trợ 1-click Reset HWID (khi khách đổi máy) và Reset Nick (khi khách đổi tài khoản).
- Đồng bộ 2 chiều với Google Sheets Cloud Web App.
- API Endpoint /api/verify phục vụ mod Minecraft Forge 1.12.2 trong game (phản hồi <5ms).
====================================================================================================
"""

import os
import sys
import json
import time
import uuid
import hmac
import hashlib
import urllib.parse
from datetime import datetime, timedelta
from http.server import HTTPServer, BaseHTTPRequestHandler
from socketserver import ThreadingMixIn
import threading

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')

CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
DB_FILE = os.path.join(CURRENT_DIR, "keys_database.json")
CONFIG_FILE = os.path.join(CURRENT_DIR, "sheet_config.json")

DEFAULT_WEB_APP_URL = "https://script.google.com/macros/s/AKfycbyKy7LVdOWKqVEfZ6fTV5DHFSQt-sK998WMYDeI4efGiOXY4JcwPTZIsHIHAKPSUco4/exec"
DEFAULT_SECRET_SALT = "SELL_PVP_SECURE_SALT_2026_@#K88"
BACKWARD_COMPAT_SALT = "MINEZACS_SECURE_SALT_2026_@#X99"

def get_web_app_url():
    if os.path.exists(CONFIG_FILE):
        try:
            with open(CONFIG_FILE, "r", encoding="utf-8") as f:
                data = json.load(f)
                u = data.get("web_app_url", "").strip()
                if u:
                    return u
        except Exception:
            pass
    return DEFAULT_WEB_APP_URL

def compute_hmac_sha256(message, secret):
    return hmac.new(secret.encode('utf-8'), message.encode('utf-8'), hashlib.sha256).hexdigest()

class Database:
    def __init__(self, db_path=DB_FILE):
        self.db_path = db_path
        self.lock = threading.Lock()
        self.keys = []
        self.load()

    def load(self):
        with self.lock:
            if os.path.exists(self.db_path):
                try:
                    with open(self.db_path, "r", encoding="utf-8") as f:
                        data = json.load(f)
                        if isinstance(data, list):
                            self.keys = data
                            return
                except Exception as e:
                    print(f"[DB] Loi doc file: {e}")
            
            # Default initial demo key if database is empty
            self.keys = [
                {
                    "key": "SELL-VIP-TEST-2026",
                    "client_name": "Khach Hang VIP",
                    "hwid": "",
                    "username": "",
                    "expires_at": "2099-12-31 23:59:59",
                    "status": "ACTIVE",
                    "features": "sell",
                    "created_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
                    "last_active": ""
                }
            ]
            self.save_unlocked()

    def save(self):
        with self.lock:
            self.save_unlocked()

    def save_unlocked(self):
        try:
            with open(self.db_path, "w", encoding="utf-8") as f:
                json.dump(self.keys, f, indent=2, ensure_ascii=False)
        except Exception as e:
            print(f"[DB] Loi ghi file: {e}")

    def find_key(self, key_str):
        clean = key_str.strip().upper()
        with self.lock:
            for k in self.keys:
                if k.get("key", "").strip().upper() == clean:
                    return k
        return None

db = Database()

def sync_from_google_sheet():
    url = get_web_app_url()
    try:
        import urllib.request
        req = urllib.request.Request(f"{url}?action=list", headers={"User-Agent": "SellAdmin/1.0"})
        with urllib.request.urlopen(req, timeout=8) as res:
            if res.status == 200:
                data = json.loads(res.read().decode('utf-8'))
                if data.get("success") and "keys" in data:
                    sheet_keys = data["keys"]
                    with db.lock:
                        existing_map = {k["key"].upper(): k for k in db.keys}
                        new_keys = []
                        for sk in sheet_keys:
                            k_code = sk.get("key", "").strip().upper()
                            if not k_code:
                                continue
                            if k_code in existing_map:
                                item = existing_map[k_code]
                                item["status"] = sk.get("status", item.get("status", "ACTIVE"))
                                item["expires_at"] = sk.get("expires_at", item.get("expires_at", "2099-12-31 23:59:59"))
                                item["hwid"] = sk.get("hwid", item.get("hwid", ""))
                                item["username"] = sk.get("username", item.get("username", ""))
                                item["client_name"] = sk.get("client_name", item.get("client_name", "Khách VIP"))
                                item["features"] = sk.get("features", item.get("features", "sell"))
                                if "created_at" in sk and sk["created_at"]:
                                    item["created_at"] = sk["created_at"]
                                if "last_active" in sk and sk["last_active"]:
                                    item["last_active"] = sk["last_active"]
                                new_keys.append(item)
                            else:
                                new_keys.append({
                                    "key": k_code,
                                    "client_name": sk.get("client_name", "Khách VIP"),
                                    "hwid": sk.get("hwid", ""),
                                    "username": sk.get("username", ""),
                                    "expires_at": sk.get("expires_at", "2099-12-31 23:59:59"),
                                    "status": sk.get("status", "ACTIVE"),
                                    "features": sk.get("features", "sell"),
                                    "created_at": sk.get("created_at", time.strftime("%Y-%m-%d %H:%M:%S")),
                                    "last_active": sk.get("last_active", "")
                                })
                        db.keys = new_keys
                        db.save_unlocked()
                    print(f"[CloudSync] Dong bo thanh cong {len(new_keys)} keys tu Google Sheet (Xoa cac key da bi go)!")
                    return True, f"Dong bo thanh cong {len(new_keys)} keys tu Google Sheet!"
    except Exception as e:
        print(f"[CloudSync] Loi ket noi Google Sheet: {e}")
        return False, str(e)
    return False, "Khong the ket noi Google Sheet"

def background_sync_worker():
    while True:
        try:
            time.sleep(4)
            sync_from_google_sheet()
        except Exception:
            pass

def notify_cloud_async(action, payload):
    def worker():
        try:
            import urllib.request
            url = get_web_app_url()
            params = {"action": action}
            params.update(payload)
            query = urllib.parse.urlencode(params)
            full_url = f"{url}?{query}"
            req = urllib.request.Request(full_url, headers={"User-Agent": "SellAdmin/1.0"})
            with urllib.request.urlopen(req, timeout=10) as res:
                _ = res.read()
                print(f"[CloudNotify] Action {action} synced to Google Sheet successfully.")
        except Exception as e:
            print(f"[CloudNotify] Action {action} background sync error: {e}")
    threading.Thread(target=worker, daemon=True).start()

HTML_PAGE = """<!DOCTYPE html>
<html lang="vi">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Quản Lý Bản Quyền Sell PVP & Sell Kho</title>
  <style>
    :root {
      --bg: #090d16;
      --card: #131b2e;
      --card-border: #1e293b;
      --primary: #38bdf8;
      --primary-hover: #0ea5e9;
      --success: #22c55e;
      --danger: #ef4444;
      --warning: #f59e0b;
      --text: #f8fafc;
      --text-muted: #94a3b8;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
    body { background-color: var(--bg); color: var(--text); padding: 24px; min-height: 100vh; }
    .container { max-width: 1250px; margin: 0 auto; }
    .header { display: flex; justify-content: space-between; align-items: center; padding-bottom: 20px; border-bottom: 1px solid var(--card-border); margin-bottom: 24px; }
    .brand { display: flex; align-items: center; gap: 12px; }
    .brand h1 { font-size: 22px; font-weight: 800; color: #fff; }
    .badge-online { background: #064e3b; color: #34d399; font-size: 12px; padding: 4px 10px; border-radius: 999px; font-weight: 600; display: inline-flex; align-items: center; gap: 6px; }
    .badge-online::before { content: ""; width: 8px; height: 8px; background: #34d399; border-radius: 50%; display: inline-block; }
    .btn { background: var(--primary); color: #000; border: none; padding: 9px 16px; border-radius: 8px; font-weight: 600; font-size: 13px; cursor: pointer; transition: 0.15s ease; display: inline-flex; align-items: center; gap: 6px; }
    .btn:hover { background: var(--primary-hover); }
    .btn-sync { background: #1e293b; color: #38bdf8; border: 1px solid #334155; }
    .btn-sync:hover { background: #334155; color: #fff; }
    .btn-danger { background: #7f1d1d; color: #fca5a5; }
    .btn-danger:hover { background: var(--danger); color: #fff; }
    .btn-success { background: #064e3b; color: #6ee7b7; }
    .btn-success:hover { background: var(--success); color: #000; }
    .btn-warning { background: #78350f; color: #fde68a; }
    .btn-warning:hover { background: var(--warning); color: #000; }
    .card { background: var(--card); border: 1px solid var(--card-border); border-radius: 12px; padding: 20px; margin-bottom: 24px; }
    .card-title { font-size: 16px; font-weight: 700; margin-bottom: 16px; display: flex; align-items: center; gap: 8px; color: #e2e8f0; }
    .form-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 14px; align-items: end; }
    .form-group { display: flex; flex-direction: column; gap: 6px; }
    label { font-size: 12px; font-weight: 600; color: var(--text-muted); }
    input, select { background: #090d16; border: 1px solid #334155; color: #fff; padding: 10px 12px; border-radius: 8px; font-size: 14px; outline: none; }
    input:focus, select:focus { border-color: var(--primary); }
    .quick-days { display: flex; gap: 6px; margin-top: 6px; flex-wrap: wrap; }
    .tag-day { background: #1e293b; color: #94a3b8; font-size: 11px; padding: 3px 8px; border-radius: 4px; cursor: pointer; border: 1px solid #334155; font-weight: 600; }
    .tag-day:hover, .tag-day.active { background: var(--primary); color: #000; border-color: var(--primary); }
    .tag-life { background: #701a75; color: #f5d0fe; border-color: #a21caf; }
    .tag-life:hover { background: #d946ef; color: #000; }
    table { width: 100%; border-collapse: collapse; text-align: left; font-size: 13px; }
    th { background: #0b1120; color: var(--text-muted); padding: 12px 14px; font-weight: 600; border-bottom: 1px solid var(--card-border); }
    td { padding: 14px; border-bottom: 1px solid #1a2234; vertical-align: middle; }
    tr:hover { background: #162035; }
    .key-code { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-weight: 700; color: #38bdf8; background: #0b1329; padding: 4px 8px; border-radius: 6px; border: 1px solid #1e3a8a; cursor: pointer; display: inline-flex; align-items: center; gap: 6px; }
    .hwid-badge { font-family: ui-monospace, monospace; font-size: 11px; color: #cbd5e1; background: #1e293b; padding: 3px 6px; border-radius: 4px; }
    .user-badge { font-family: ui-monospace, monospace; font-size: 12px; font-weight: 700; color: #fbbf24; background: #27210e; border: 1px solid #78350f; padding: 3px 8px; border-radius: 4px; }
    .status-active { background: #064e3b; color: #4ade80; padding: 4px 10px; border-radius: 999px; font-size: 11px; font-weight: 700; display: inline-block; }
    .status-locked { background: #7f1d1d; color: #f87171; padding: 4px 10px; border-radius: 999px; font-size: 11px; font-weight: 700; display: inline-block; }
    .actions { display: flex; gap: 6px; flex-wrap: wrap; }
    .btn-sm { padding: 5px 10px; font-size: 11px; border-radius: 6px; }
    .toast { position: fixed; bottom: 24px; right: 24px; padding: 12px 20px; border-radius: 8px; font-size: 13px; font-weight: 600; color: #fff; background: #1e293b; border-left: 4px solid var(--primary); box-shadow: 0 10px 15px -3px rgba(0,0,0,0.5); display: none; z-index: 9999; }
  </style>
</head>
<body>
<div class="container">
  <div class="header">
    <div class="brand">
      <span style="font-size:26px;">⚔️</span>
      <div>
        <h1>Quản Lý Bản Quyền Sell PVP & Sell Kho</h1>
        <div style="font-size:12px; color:var(--text-muted); margin-top:2px;">Local Admin Fast Sync Server (127.0.0.1:5555)</div>
      </div>
    </div>
    <div style="display:flex; align-items:center; gap:12px;">
      <span class="badge-online">Local API Online</span>
      <button class="btn btn-sync" onclick="syncSheet()">🔄 Đồng bộ Google Sheet</button>
    </div>
  </div>

  <!-- TAO KEY MOI -->
  <div class="card">
    <div class="card-title"><span>➕</span> Tạo License Key Mới</div>
    <div class="form-grid">
      <div class="form-group">
        <label>Mã Key:</label>
        <div style="display:flex; gap:6px;">
          <input type="text" id="new_key" placeholder="SELL-XXXX-XXXX-XXXX" style="flex:1;">
          <button class="btn btn-sync" onclick="genRandomKey()" title="Sinh ngẫu nhiên">🎲</button>
        </div>
      </div>
      <div class="form-group">
        <label>Tên Khách Hàng:</label>
        <input type="text" id="new_client" placeholder="Khách VIP" value="Khách VIP">
      </div>
      <div class="form-group">
        <label>Thời Hạn Hết Hạn:</label>
        <input type="text" id="new_exp" value="2099-12-31 23:59:59">
        <div class="quick-days">
          <span class="tag-day" onclick="setExpDays(1)">1 Ngày</span>
          <span class="tag-day" onclick="setExpDays(7)">7 Ngày</span>
          <span class="tag-day" onclick="setExpDays(30)">30 Ngày</span>
          <span class="tag-day" onclick="setExpDays(90)">90 Ngày</span>
          <span class="tag-day tag-life active" onclick="setLife()">⭐ VĨNH VIỄN</span>
        </div>
      </div>
      <div class="form-group">
        <label>Mã Máy HWID (Tùy chọn):</label>
        <input type="text" id="new_hwid" placeholder="Trống = Tự bind máy đầu tiên">
      </div>
      <div class="form-group">
        <label>Tài Khoản Nick (Tùy chọn):</label>
        <input type="text" id="new_user" placeholder="Trống = Tự bind nick đầu tiên">
      </div>
      <div class="form-group">
        <button class="btn" onclick="createKey()" style="height:42px; justify-content:center;">🚀 Tạo & Kích Hoạt Key</button>
      </div>
    </div>
  </div>

  <!-- DANH SACH KEYS -->
  <div class="card">
    <div class="card-title" style="justify-content:space-between;">
      <div style="display:flex; align-items:center; gap:8px;">
        <span>📋</span> Danh Sách License Keys (<span id="key_count">0</span>)
      </div>
      <input type="text" id="search_box" onkeyup="renderTable()" placeholder="🔍 Tìm kiếm Key / Khách / Nick..." style="width:240px; padding:6px 10px; font-size:12px;">
    </div>
    <div style="overflow-x:auto;">
      <table>
        <thead>
          <tr>
            <th>MÃ KEY</th>
            <th>KHÁCH HÀNG</th>
            <th>MÁY (HWID)</th>
            <th>TÀI KHOẢN (NICK)</th>
            <th>HẠN DÙNG</th>
            <th>TRẠNG THÁI</th>
            <th>THAO TÁC ADMIN</th>
          </tr>
        </thead>
        <tbody id="key_tbody">
          <tr><td colspan="7" style="text-align:center; padding:30px; color:#64748b;">Đang tải dữ liệu...</td></tr>
        </tbody>
      </table>
    </div>
  </div>
</div>

<div id="toast" class="toast"></div>

<script>
let allKeys = [];

function showToast(msg, isSuccess = true) {
  const t = document.getElementById("toast");
  t.innerText = msg;
  t.style.borderLeftColor = isSuccess ? "var(--success)" : "var(--danger)";
  t.style.display = "block";
  setTimeout(() => { t.style.display = "none"; }, 3500);
}

function genRandomKey() {
  const p1 = Math.random().toString(36).substring(2, 6).toUpperCase();
  const p2 = Math.random().toString(36).substring(2, 6).toUpperCase();
  const p3 = Math.random().toString(36).substring(2, 6).toUpperCase();
  document.getElementById("new_key").value = `SELL-${p1}-${p2}-${p3}`;
}

function setExpDays(d) {
  const dt = new Date();
  dt.setDate(dt.getDate() + d);
  const pad = n => n.toString().padStart(2, '0');
  const str = `${dt.getFullYear()}-${pad(dt.getMonth()+1)}-${pad(dt.getDate())} 23:59:59`;
  document.getElementById("new_exp").value = str;
  document.querySelectorAll('.tag-day').forEach(el => el.classList.remove('active'));
  event.target.classList.add('active');
}

function setLife() {
  document.getElementById("new_exp").value = "2099-12-31 23:59:59";
  document.querySelectorAll('.tag-day').forEach(el => el.classList.remove('active'));
  event.target.classList.add('active');
}

function loadKeys() {
  fetch('/api/keys')
    .then(r => r.json())
    .then(data => {
      allKeys = data;
      renderTable();
    })
    .catch(e => console.error(e));
}

function renderTable() {
  const q = document.getElementById("search_box").value.trim().toUpperCase();
  const filtered = allKeys.filter(k => {
    return (k.key || "").toUpperCase().includes(q) ||
           (k.client_name || "").toUpperCase().includes(q) ||
           (k.username || "").toUpperCase().includes(q) ||
           (k.hwid || "").toUpperCase().includes(q);
  });
  document.getElementById("key_count").innerText = filtered.length;

  const tbody = document.getElementById("key_tbody");
  if (filtered.length === 0) {
    tbody.innerHTML = '<tr><td colspan="7" style="text-align:center; padding:30px; color:#64748b;">Chưa có Key nào phù hợp</td></tr>';
    return;
  }

  tbody.innerHTML = filtered.map(k => {
    const isLocked = (k.status || "").toUpperCase() === "BLOCKED" || (k.status || "").toUpperCase() === "LOCKED";
    const statusHtml = isLocked 
      ? '<span class="status-locked">🔒 ĐÃ KHÓA</span>'
      : '<span class="status-active">✔ HOẠT ĐỘNG</span>';
    
    const isLife = (k.expires_at || "").includes("2099") || (k.key || "").includes("LIFE");
    const expDisplay = isLife ? '<span style="color:#f5d0fe; font-weight:700;">⭐ Vĩnh viễn</span>' : k.expires_at;

    const hwidDisplay = k.hwid ? `<span class="hwid-badge">${k.hwid}</span>` : '<span style="color:#64748b; font-size:11px;">(Tự nhận khi nhập)</span>';
    const userDisplay = k.username ? `<span class="user-badge">${k.username}</span>` : '<span style="color:#64748b; font-size:11px;">(Tự nhận khi đăng nhập)</span>';

    const toggleBtn = isLocked
      ? `<button class="btn btn-success btn-sm" onclick="toggleStatus('${k.key}', 'ACTIVE')">🔓 Mở Khóa</button>`
      : `<button class="btn btn-danger btn-sm" onclick="toggleStatus('${k.key}', 'BLOCKED')">🔒 Khóa Key</button>`;

    return `
      <tr>
        <td>
          <span class="key-code" onclick="copyText('${k.key}')" title="Click để sao chép">${k.key} 📋</span>
        </td>
        <td><strong>${k.client_name || 'Khách VIP'}</strong></td>
        <td>${hwidDisplay}</td>
        <td>${userDisplay}</td>
        <td>${expDisplay}</td>
        <td>${statusHtml}</td>
        <td>
          <div class="actions">
            ${toggleBtn}
            <button class="btn btn-warning btn-sm" onclick="editExpiry('${k.key}')">📅 Đổi Hạn</button>
            <button class="btn btn-sync btn-sm" onclick="resetHwid('${k.key}')" title="Reset HWID">🔄 Đổi Máy</button>
            <button class="btn btn-sync btn-sm" onclick="resetUser('${k.key}')" title="Reset Nick" style="color:#fbbf24;">👤 Đổi Nick</button>
            <button class="btn btn-danger btn-sm" onclick="deleteKey('${k.key}')" title="Xóa">🗑️</button>
          </div>
        </td>
      </tr>
    `;
  }).join("");
}

function copyText(t) {
  navigator.clipboard.writeText(t);
  showToast("Đã sao chép: " + t);
}

function createKey() {
  const key = document.getElementById("new_key").value.trim().toUpperCase();
  const client = document.getElementById("new_client").value.trim() || "Khách VIP";
  const exp = document.getElementById("new_exp").value.trim() || "2099-12-31 23:59:59";
  const hwid = document.getElementById("new_hwid").value.trim();
  const username = document.getElementById("new_user").value.trim();

  if (!key) {
    showToast("Vui lòng nhập hoặc tạo mã Key!", false);
    return;
  }

  fetch('/api/keys/create', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({ key, client_name: client, expires_at: exp, hwid, username })
  })
  .then(r => r.json())
  .then(data => {
    if (data.success) {
      showToast("Tạo Key thành công: " + key);
      genRandomKey();
      loadKeys();
    } else {
      showToast(data.msg || "Tạo Key thất bại!", false);
    }
  });
}

function toggleStatus(key, newStatus) {
  fetch('/api/keys/update_status', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({ key, status: newStatus })
  })
  .then(r => r.json())
  .then(data => {
    if (data.success) {
      showToast((newStatus === 'BLOCKED' ? "ĐÃ KHÓA Key: " : "ĐÃ MỞ KHÓA Key: ") + key);
      loadKeys();
    } else {
      showToast(data.msg || "Cập nhật thất bại!", false);
    }
  });
}

function editExpiry(key) {
  const current = prompt("Nhập thời hạn mới cho Key " + key + "\\n(Gõ 2099-12-31 23:59:59 để đặt VĨNH VIỄN):", "2099-12-31 23:59:59");
  if (!current) return;

  fetch('/api/keys/extend', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({ key, expires_at: current.trim() })
  })
  .then(r => r.json())
  .then(data => {
    if (data.success) {
      showToast("Đã cập nhật hạn dùng Key: " + key);
      loadKeys();
    } else {
      showToast(data.msg || "Cập nhật thất bại!", false);
    }
  });
}

function resetHwid(key) {
  if (!confirm("Bạn có chắc muốn Reset HWID cho Key " + key + "?\\nSau khi reset, bất kỳ máy nào cũng có thể kích hoạt Key này.")) return;

  fetch('/api/keys/reset_hwid', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({ key })
  })
  .then(r => r.json())
  .then(data => {
    if (data.success) {
      showToast("Đã Reset HWID thành công cho Key: " + key);
      loadKeys();
    } else {
      showToast(data.msg || "Reset thất bại!", false);
    }
  });
}

function resetUser(key) {
  if (!confirm("Bạn có chắc muốn Reset Nick cho Key " + key + "?\\nSau khi reset, Key có thể bind lại với tài khoản Minecraft khác.")) return;

  fetch('/api/keys/reset_user', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({ key })
  })
  .then(r => r.json())
  .then(data => {
    if (data.success) {
      showToast("Đã Reset Nick thành công cho Key: " + key);
      loadKeys();
    } else {
      showToast(data.msg || "Reset thất bại!", false);
    }
  });
}

function deleteKey(key) {
  if (!confirm("Bạn có chắc muốn XÓA vĩnh viễn Key " + key + "?")) return;

  fetch('/api/keys/delete', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({ key })
  })
  .then(r => r.json())
  .then(data => {
    if (data.success) {
      showToast("Đã xóa Key: " + key);
      loadKeys();
    } else {
      showToast(data.msg || "Xóa thất bại!", false);
    }
  });
}

function syncSheet() {
  showToast("Đang đồng bộ với Google Sheet...");
  fetch('/api/keys/sync', { method: 'POST' })
    .then(r => r.json())
    .then(data => {
      showToast(data.msg, data.success);
      loadKeys();
    });
}

genRandomKey();
loadKeys();
setInterval(loadKeys, 3000);
</script>
</body>
</html>
"""

class ThreadedHTTPServer(ThreadingMixIn, HTTPServer):
    daemon_threads = True

class AdminRequestHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        if "/api/verify" in self.path:
            sys.stdout.write(f"[GAME-MOD] {self.address_string()} - {format%args}\n")
        else:
            sys.stdout.write(f"[ADMIN-WEB] {self.address_string()} - {format%args}\n")

    def send_json_response(self, data, status=200):
        body = json.dumps(data, ensure_ascii=False).encode('utf-8')
        self.send_response(status)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.end_headers()

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        params = dict(urllib.parse.parse_qsl(parsed.query))

        if path == "/" or path == "/index.html":
            body = HTML_PAGE.encode('utf-8')
            self.send_response(200)
            self.send_header('Content-Type', 'text/html; charset=utf-8')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return

        if path == "/api/keys":
            with db.lock:
                keys_copy = list(db.keys)
            self.send_json_response(keys_copy)
            return

        # =========================================================================
        # API VERIFY (Cho Mod Minecraft trong game gửi tới)
        # =========================================================================
        if path == "/api/verify":
            action = params.get("action", "verify")
            key_in = params.get("key", "").strip().upper()
            hwid_in = params.get("hwid", "").strip()
            user_in = params.get("username", "").strip()

            if not key_in:
                self.send_json_response({"success": False, "code": "MISSING_KEY", "msg": "Vui long nhap License Key!"})
                return

            found = db.find_key(key_in)
            if not found:
                self.send_json_response({"success": False, "code": "KEY_NOT_FOUND", "msg": "Key khong ton tai tren he thong!"})
                return

            # Check status
            st = str(found.get("status", "ACTIVE")).strip().upper()
            if st == "BLOCKED" or st == "LOCKED":
                self.send_json_response({"success": False, "code": "KEY_LOCKED", "msg": "Key da bi KHOA boi Admin!"})
                return

            # Check HWID (1 Key 1 May)
            saved_hwid = str(found.get("hwid", "")).strip()
            if not saved_hwid and hwid_in:
                # Auto-bind first machine
                with db.lock:
                    found["hwid"] = hwid_in
                    db.save_unlocked()
                notify_cloud_async("create", found)
                print(f"[HWID-BIND] Key {key_in} da tu dong gan voi may {hwid_in}")
            elif saved_hwid and hwid_in and (saved_hwid.upper() != hwid_in.upper()):
                self.send_json_response({"success": False, "code": "HWID_MISMATCH", "msg": "Key khong hop le hoac bi khoa!"})
                return

            # Cap nhat Nick Minecraft dang hoat dong tren may hop le (1 Key 1 May Duy Nhat)
            saved_user = str(found.get("username", "")).strip()
            if user_in and user_in.lower() != "player":
                if saved_user.lower() != user_in.lower():
                    with db.lock:
                        found["username"] = user_in
                        db.save_unlocked()
                    notify_cloud_async("create", found)
                    print(f"[USER-UPDATE] Key {key_in} tren may {saved_hwid or hwid_in} chuyen sang nick [{user_in}]")

            # Check Expiry
            exp_str = str(found.get("expires_at", "")).strip()
            if exp_str and not "2099" in exp_str and not "LIFE" in key_in:
                try:
                    exp_dt = None
                    try:
                        exp_dt = datetime.strptime(exp_str, "%Y-%m-%d %H:%M:%S")
                    except Exception:
                        exp_dt = datetime.strptime(exp_str, "%Y-%m-%d")
                    if exp_dt and datetime.now() > exp_dt:
                        self.send_json_response({"success": False, "code": "KEY_EXPIRED", "msg": f"Key da het han ({exp_str})!"})
                        return
                except Exception:
                    pass

            # Update last active
            now_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            with db.lock:
                found["last_active"] = now_str
                db.save_unlocked()

            # Sign with salt
            msg_to_sign = f"{key_in}|{saved_hwid or hwid_in}|{exp_str}|{now_str}"
            sig = compute_hmac_sha256(msg_to_sign, DEFAULT_SECRET_SALT)

            self.send_json_response({
                "success": True,
                "key": key_in,
                "client_name": found.get("client_name", "Khach VIP"),
                "username": saved_user or user_in,
                "expires_at": exp_str or "Vinh vien",
                "hwid": saved_hwid or hwid_in,
                "server_time": now_str,
                "signature": sig,
                "features": ["sell"],
                "msg": "Xac thuc ban quyen thanh cong!"
            })
            return

        self.send_response(404)
        self.end_headers()

    def do_POST(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path

        content_length = int(self.headers.get('Content-Length', 0))
        post_data = {}
        if content_length > 0:
            raw = self.rfile.read(content_length).decode('utf-8')
            try:
                post_data = json.loads(raw)
            except Exception:
                post_data = dict(urllib.parse.parse_qsl(raw))

        if path == "/api/keys/create":
            key = str(post_data.get("key", "")).strip().upper()
            client = str(post_data.get("client_name", "Khach VIP")).strip()
            exp = str(post_data.get("expires_at", "2099-12-31 23:59:59")).strip()
            hwid = str(post_data.get("hwid", "")).strip()
            username = str(post_data.get("username", "")).strip()

            if not key:
                self.send_json_response({"success": False, "msg": "Vui long nhap ma Key!"})
                return

            if db.find_key(key):
                self.send_json_response({"success": False, "msg": "Key nay da ton tai trong he thong!"})
                return

            new_record = {
                "key": key,
                "client_name": client,
                "hwid": hwid,
                "username": username,
                "expires_at": exp,
                "status": "ACTIVE",
                "features": "sell",
                "created_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
                "last_active": ""
            }

            with db.lock:
                db.keys.insert(0, new_record)
                db.save_unlocked()

            notify_cloud_async("create", new_record)
            self.send_json_response({"success": True, "msg": f"Da tao Key {key} thanh cong!"})
            return

        if path == "/api/keys/update_status":
            key = str(post_data.get("key", "")).strip().upper()
            st = str(post_data.get("status", "ACTIVE")).strip().upper()
            found = db.find_key(key)
            if not found:
                self.send_json_response({"success": False, "msg": "Khong tim thay Key!"})
                return

            with db.lock:
                found["status"] = st
                db.save_unlocked()

            notify_cloud_async("update_status", {"key": key, "status": st})
            print(f"[STATUS] Key {key} updated to {st}")
            self.send_json_response({"success": True, "msg": f"Da cap nhat trang thai sang {st}"})
            return

        if path == "/api/keys/extend":
            key = str(post_data.get("key", "")).strip().upper()
            exp = str(post_data.get("expires_at", "2099-12-31 23:59:59")).strip()
            found = db.find_key(key)
            if not found:
                self.send_json_response({"success": False, "msg": "Khong tim thay Key!"})
                return

            with db.lock:
                found["expires_at"] = exp
                if found.get("status") == "EXPIRED":
                    found["status"] = "ACTIVE"
                db.save_unlocked()

            notify_cloud_async("extend", {"key": key, "expires_at": exp})
            self.send_json_response({"success": True, "msg": f"Da cap nhat han dung sang {exp}"})
            return

        if path == "/api/keys/reset_hwid":
            key = str(post_data.get("key", "")).strip().upper()
            found = db.find_key(key)
            if not found:
                self.send_json_response({"success": False, "msg": "Khong tim thay Key!"})
                return

            with db.lock:
                found["hwid"] = ""
                db.save_unlocked()

            notify_cloud_async("reset_hwid", {"key": key})
            self.send_json_response({"success": True, "msg": f"Da Reset HWID cho Key {key}"})
            return

        if path == "/api/keys/reset_user":
            key = str(post_data.get("key", "")).strip().upper()
            found = db.find_key(key)
            if not found:
                self.send_json_response({"success": False, "msg": "Khong tim thay Key!"})
                return

            with db.lock:
                found["username"] = ""
                db.save_unlocked()

            notify_cloud_async("create", found)
            self.send_json_response({"success": True, "msg": f"Da Reset Nick cho Key {key}"})
            return

        if path == "/api/keys/delete":
            key = str(post_data.get("key", "")).strip().upper()
            found = db.find_key(key)
            if not found:
                self.send_json_response({"success": False, "msg": "Khong tim thay Key!"})
                return

            with db.lock:
                db.keys = [k for k in db.keys if k.get("key", "").upper() != key]
                db.save_unlocked()

            notify_cloud_async("delete", {"key": key})
            self.send_json_response({"success": True, "msg": f"Da xoa Key {key}"})
            return

        if path == "/api/keys/sync":
            ok, msg = sync_from_google_sheet()
            self.send_json_response({"success": ok, "msg": msg})
            return

        self.send_response(404)
        self.end_headers()

def run_server(port=5555):
    server_address = ('127.0.0.1', port)
    httpd = ThreadedHTTPServer(server_address, AdminRequestHandler)
    print("=" * 65)
    print(f"  TRUNG TAM QUAN LY BAN QUYEN SELL PVP & SELL KHO (PORT {port})")
    print(f"  Trinh duyet: http://127.0.0.1:{port}")
    print(f"  API Verify:  http://127.0.0.1:{port}/api/verify")
    print(f"  Du lieu CSDL: {DB_FILE}")
    print("=" * 65)
    print("  [SAN SANG] Cho mod Minecraft trong game ket noi...")
    print("=" * 65)

    threading.Thread(target=sync_from_google_sheet, daemon=True).start()
    threading.Thread(target=background_sync_worker, daemon=True).start()

    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\n[AdminServer] Dang dung server...")
        httpd.server_close()

if __name__ == "__main__":
    run_server()
