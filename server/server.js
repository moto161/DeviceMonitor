/**
 * 多设备状态监控中心服务器
 * 科幻风格设备监控系统
 */

const express = require('express');
const cors = require('cors');
const bodyParser = require('body-parser');
const Database = require('better-sqlite3');
const axios = require('axios');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 8443;

// ========== 数据库初始化 ==========
const db = new Database(path.join(__dirname, 'devices.db'));

db.exec(`
  CREATE TABLE IF NOT EXISTS devices (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    type TEXT DEFAULT 'unknown',
    last_seen INTEGER,
    status TEXT DEFAULT 'offline',
    config TEXT DEFAULT '{}'
  );

  CREATE TABLE IF NOT EXISTS device_data (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    data TEXT NOT NULL,
    FOREIGN KEY (device_id) REFERENCES devices(id)
  );

  CREATE TABLE IF NOT EXISTS alerts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id TEXT NOT NULL,
    type TEXT NOT NULL,
    message TEXT NOT NULL,
    level TEXT DEFAULT 'warning',
    timestamp INTEGER NOT NULL,
    acknowledged INTEGER DEFAULT 0
  );

  CREATE INDEX IF NOT EXISTS idx_data_device ON device_data(device_id, timestamp DESC);
  CREATE INDEX IF NOT EXISTS idx_alerts_device ON alerts(device_id, timestamp DESC);
`);

// ========== 配置 ==========
const CONFIG = {
  // 微信推送 Server酱 SendKey（用户后续填入）
  wechatSendKey: process.env.WECHAT_SENDKEY || '',
  // 设备离线判定时间（毫秒）
  offlineTimeout: 120000,
  // 数据保留天数
  dataRetentionDays: 30,
  // 告警阈值
  thresholds: {
    cpuUsage: 90,
    cpuTemp: 80,
    memoryUsage: 90,
    battery: 20,
    diskUsage: 90
  }
};

// ========== 微信推送 ==========
async function sendWechatAlert(title, content) {
  if (!CONFIG.wechatSendKey) {
    console.log('[微信推送] 未配置 SendKey，跳过:', title);
    return;
  }
  try {
    await axios.post(`https://sctapi.ftqq.com/${CONFIG.wechatSendKey}.send`, {
      title: title,
      desp: content
    }, { timeout: 10000 });
    console.log('[微信推送] 成功:', title);
  } catch (e) {
    console.error('[微信推送] 失败:', e.message);
  }
}


// Ponytail: 提取重复的告警插入代码，DRY原则
function insertAlert(deviceId, type, message, level, timestamp) {
  db.prepare('INSERT INTO alerts (device_id, type, message, level, timestamp) VALUES (?, ?, ?, ?, ?)')
    .run(deviceId, type, message, level, timestamp);
}

// ========== 告警检测 ==========
function checkAlerts(deviceId, deviceName, data) {
  const alerts = [];
  const t = CONFIG.thresholds;
  if (!data || typeof data !== 'object') return alerts;

  // Ponytail: 配置数组驱动，消除3个重复if结构
  const rules = [
    { field: 'cpu_usage', threshold: t.cpuUsage, type: 'cpu_high', level: 'warning', label: 'CPU 使用率', unit: '%' },
    { field: 'cpu_temp', threshold: t.cpuTemp, type: 'cpu_temp_high', level: 'critical', label: 'CPU 温度', unit: '°C' },
    { field: 'memory_usage', threshold: t.memoryUsage, type: 'memory_high', level: 'warning', label: '内存使用率', unit: '%' },
  ];
  rules.forEach(rule => {
    const val = data[rule.field];
    if (val !== undefined && val !== null && val >= rule.threshold) {
      alerts.push({
        type: rule.type,
        message: `${deviceName} ${rule.label} ${val.toFixed(1)}${rule.unit} 超过阈值 ${rule.threshold}${rule.unit}`,
        level: rule.level
      });
    }
  });

  // 电量告警（逻辑特殊：低于阈值且未充电）
  if (data.battery !== undefined && data.battery !== null && data.battery <= t.battery && data.charging !== true) {
    alerts.push({
      type: 'battery_low',
      message: `${deviceName} 电量 ${data.battery}% 过低，请及时充电`,
      level: 'warning'
    });
  }

  // 磁盘告警（多磁盘遍历）
  if (data.disks) {
    data.disks.forEach(disk => {
      if (disk.usage >= t.diskUsage) {
        alerts.push({
          type: 'disk_high',
          message: `${deviceName} 磁盘 ${disk.mount} 使用率 ${disk.usage.toFixed(1)}% 超过阈值 ${t.diskUsage}%`,
          level: 'warning'
        });
      }
    });
  }

  return alerts;
}

// 告警去重（同一设备同一类型5分钟内只推一次，内存+数据库双重校验防重启后重复推送）
const alertCooldown = {};
function shouldAlert(deviceId, type) {
  const key = `${deviceId}_${type}`;
  const now = Date.now();
  // 内存缓存快速判断
  if (alertCooldown[key] && now - alertCooldown[key] < 300000) {
    return false;
  }
  // 数据库持久化判断（服务器重启后内存丢失，靠数据库防重复）
  const recent = db.prepare('SELECT id FROM alerts WHERE device_id = ? AND type = ? AND timestamp > ?').get(deviceId, type, now - 300000);
  if (recent) {
    alertCooldown[key] = now;
    return false;
  }
  alertCooldown[key] = now;
  return true;
}

// ========== 中间件 ==========
// CORS配置：允许来源但限制方法和头部（Android APP不受CORS限制）
app.use(cors({
  methods: ['GET', 'POST', 'DELETE', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'x-api-key'],
  maxAge: 86400
}));

// 安全响应头
app.use((req, res, next) => {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('X-Frame-Options', 'DENY');
  res.setHeader('X-XSS-Protection', '1; mode=block');
  res.setHeader('Referrer-Policy', 'no-referrer');
  next();
});

app.use(bodyParser.json({ limit: '1mb' }));

// 安全 JSON 解析（数据损坏时返回 null，不抛异常）
function safeJsonParse(str) {
  try {
    return JSON.parse(str);
  } catch (e) {
    return null;
  }
}

// 简单的 API Key 认证（2026-10-12 轮换：默认只认新 Key；
// 过渡期可设环境变量 API_KEY_LEGACY 暂时兼容旧 Key，全部 Agent 更新后去掉该变量即彻底失效）
const API_KEY = process.env.API_KEY || '';
const API_KEY_LEGACY = process.env.API_KEY_LEGACY || '';
function authMiddleware(req, res, next) {
  const key = req.headers['x-api-key'];  // 只允许 header 传递，防止 API Key 泄露到 URL/日志
  if (key && key === API_KEY) {
    return next();
  }
  if (key && API_KEY_LEGACY && key === API_KEY_LEGACY) {
    console.log('[认证] 旧版 API Key（过渡期兼容）:', req.method, req.path, req.ip);
    return next();
  }
  // 仅健康检查端点免认证，上报接口需 API Key（所有 Agent 均已携带 x-api-key）
  const p = req.path;
  if (p === '/api/test' || p === '/test') {
    return next();
  }
  return res.status(401).json({ error: 'Unauthorized' });
}


// ========== 频率限制（防止恶意刷接口导致数据库膨胀） ==========
const rateLimitMap = new Map();
const RATE_WINDOW_MS = 60000;  // 1分钟窗口
const RATE_MAX = 120;           // 每IP每分钟最多120次请求
function rateLimitMiddleware(req, res, next) {
  const ip = req.ip || req.connection.remoteAddress || 'unknown';
  const now = Date.now();
  let record = rateLimitMap.get(ip);
  if (!record || now > record.resetTime) {
    record = { count: 0, resetTime: now + RATE_WINDOW_MS };
    rateLimitMap.set(ip, record);
  }
  record.count++;
  if (record.count > RATE_MAX) {
    return res.status(429).json({ error: 'Too Many Requests', retryAfter: Math.ceil((record.resetTime - now) / 1000) });
  }
  next();
}
// 过期数据清理（启动时执行一次，之后每24小时一次，避免频繁全表DELETE锁表）
function cleanupExpiredData() {
  const expireTime = Date.now() - CONFIG.dataRetentionDays * 24 * 3600 * 1000;
  const result = db.prepare('DELETE FROM device_data WHERE timestamp < ?').run(expireTime);
  if (result.changes > 0) {
    console.log(`[数据清理] 已清理 ${result.changes} 条过期数据`);
  }
}
cleanupExpiredData();
setInterval(cleanupExpiredData, 24 * 3600 * 1000);

// 定期清理过期IP记录（每5分钟）
setInterval(() => {
  const now = Date.now();
  for (const [ip, record] of rateLimitMap) {
    if (now > record.resetTime) rateLimitMap.delete(ip);
  }
}, 300000);

app.use('/api', rateLimitMiddleware);
app.use('/api', authMiddleware);

// 统一错误处理中间件（Ponytail: 错误处理不能省，防止数据库错误导致进程崩溃）
function asyncHandler(fn) {
  return (req, res, next) => {
    try {
      fn(req, res, next);
    } catch (e) {
      console.error('[API错误]', req.method, req.path, e.message);
      res.status(500).json({ error: '服务器内部错误', detail: e.message });
    }
  };
}


// ========== API 路由 ==========

// 免认证健康检查端点（仅返回服务器状态，不泄露设备数据）
app.get('/api/test', asyncHandler((req, res) => {
  res.json({
    status: 'ok',
    server_time: Date.now(),
    version: '1.0.0'
  });
}));

// 输入验证（防止恶意输入导致数据库膨胀）
function validateReportInput(device_id, device_name, device_type) {
  if (typeof device_id !== 'string' || device_id.length < 1 || device_id.length > 64) {
    return { valid: false, error: 'device_id must be 1-64 chars' };
  }
  if (!/^[a-zA-Z0-9_-]+$/.test(device_id)) {
    return { valid: false, error: 'device_id contains invalid chars' };
  }
  if (typeof device_name !== 'string' || device_name.length > 100) {
    return { valid: false, error: 'device_name too long (max 100)' };
  }
  if (typeof device_type !== 'string' || device_type.length > 32) {
    return { valid: false, error: 'device_type too long (max 32)' };
  }
  return { valid: true };
}

// 设备数据上报
app.post('/api/report', asyncHandler((req, res) => {
  const { device_id, device_name, device_type, data } = req.body;

  if (!device_id || !data) {
    return res.status(400).json({ error: 'device_id and data required' });
  }

  const validation = validateReportInput(device_id, device_name || '', device_type || '');
  if (!validation.valid) {
    return res.status(400).json({ error: validation.error });
  }

  const now = Date.now();
  const name = device_name || device_id;
  const type = device_type || 'unknown';

  // 更新或插入设备
  const stmt = db.prepare(`
    INSERT INTO devices (id, name, type, last_seen, status)
    VALUES (?, ?, ?, ?, 'online')
    ON CONFLICT(id) DO UPDATE SET
      name = excluded.name,
      type = excluded.type,
      last_seen = excluded.last_seen,
      status = 'online'
  `);
  stmt.run(device_id, name, type, now);

  // 保存数据
  const dataStmt = db.prepare('INSERT INTO device_data (device_id, timestamp, data) VALUES (?, ?, ?)');
  dataStmt.run(device_id, now, JSON.stringify(data));

  // 告警检测
  const alerts = checkAlerts(device_id, name, data);
  alerts.forEach(alert => {
    if (shouldAlert(device_id, alert.type)) {
      insertAlert(device_id, alert.type, alert.message, alert.level, now);
      sendWechatAlert(`【设备告警】${alert.level === 'critical' ? '严重' : '警告'}`, alert.message);
    }
  });

  res.json({ success: true, timestamp: now });
}));

// 设备列表（优化：用子查询一次性获取最新数据，避免N+1查询）
app.get('/api/devices', asyncHandler((req, res) => {
  const now = Date.now();
  const rows = db.prepare(`
    SELECT d.id, d.name, d.type, d.last_seen,
           (SELECT data FROM device_data WHERE device_id = d.id ORDER BY timestamp DESC LIMIT 1) as latest_data
    FROM devices d
    ORDER BY d.last_seen DESC
  `).all();

  const result = rows.map(d => {
    const status = (now - d.last_seen) > CONFIG.offlineTimeout ? 'offline' : 'online';
    return {
      id: d.id,
      name: d.name,
      type: d.type,
      status: status,
      last_seen: d.last_seen,
      latest_data: d.latest_data ? safeJsonParse(d.latest_data) : null
    };
  });

  res.json({ devices: result });
}));

// 设备详情
app.get('/api/devices/:id', asyncHandler((req, res) => {
  const { id } = req.params;
  const device = db.prepare('SELECT * FROM devices WHERE id = ?').get(id);

  if (!device) {
    return res.status(404).json({ error: 'Device not found' });
  }

  const now = Date.now();
  const latest = db.prepare('SELECT data, timestamp FROM device_data WHERE device_id = ? ORDER BY timestamp DESC LIMIT 1').get(id);
  const status = (now - device.last_seen) > CONFIG.offlineTimeout ? 'offline' : 'online';

  res.json({
    device: {
      ...device,
      status,
      latest_data: latest ? safeJsonParse(latest.data) : null,
      latest_timestamp: latest ? latest.timestamp : null
    }
  });
}));

// 删除设备（同时删除历史数据和告警）
app.delete('/api/devices/:id', asyncHandler((req, res) => {
  const { id } = req.params;

  // 使用事务确保数据一致性
  const deleteDevice = db.transaction(() => {
    // 删除设备历史数据
    db.prepare('DELETE FROM device_data WHERE device_id = ?').run(id);
    // 删除设备相关告警
    db.prepare('DELETE FROM alerts WHERE device_id = ?').run(id);
    // 删除设备本身
    const result = db.prepare('DELETE FROM devices WHERE id = ?').run(id);
    return result.changes;
  });

  try {
    const changes = deleteDevice();
    if (changes > 0) {
      res.json({ success: true, message: '设备已删除', deleted: changes });
    } else {
      res.status(404).json({ error: '设备不存在' });
    }
  } catch (e) {
    console.error('删除设备失败:', e);
    res.status(500).json({ error: '删除失败: ' + e.message });
  }
}));

// 设备历史数据
app.get('/api/devices/:id/history', asyncHandler((req, res) => {
  const { id } = req.params;
  const hours = parseInt(req.query.hours) || 24;
  const limit = Math.min(parseInt(req.query.limit) || 288, 1000);

  const since = Date.now() - hours * 3600 * 1000;
  const rows = db.prepare(`
    SELECT timestamp, data FROM device_data
    WHERE device_id = ? AND timestamp >= ?
    ORDER BY timestamp DESC
    LIMIT ?
  `).all(id, since, limit);

  const history = rows.map(r => ({
    timestamp: r.timestamp,
    data: safeJsonParse(r.data)
  })).reverse();

  res.json({ history });
}));

// 告警列表
app.get('/api/alerts', asyncHandler((req, res) => {
  const limit = Math.min(parseInt(req.query.limit) || 500, 2000);
  const alerts = db.prepare(`
    SELECT a.*, d.name as device_name
    FROM alerts a
    LEFT JOIN devices d ON a.device_id = d.id
    ORDER BY a.timestamp DESC
    LIMIT ?
  `).all(limit);

  res.json({ alerts });
}));

// 删除单条告警
app.delete('/api/alerts/:id', asyncHandler((req, res) => {
  const { id } = req.params;
  const result = db.prepare('DELETE FROM alerts WHERE id = ?').run(id);
  if (result.changes > 0) {
    res.json({ success: true, message: '告警已删除' });
  } else {
    res.status(404).json({ error: '告警不存在' });
  }
}));

// 删除全部告警
app.delete('/api/alerts', asyncHandler((req, res) => {
  const result = db.prepare('DELETE FROM alerts').run();
  res.json({ success: true, deleted: result.changes, message: '全部告警已清除' });
}));

// 系统状态
app.get('/api/status', asyncHandler((req, res) => {
  // Ponytail: 4个COUNT合并为1次查询，减少数据库往返
  const stats = db.prepare(`
    SELECT
      (SELECT COUNT(*) FROM devices) as total,
      (SELECT COUNT(*) FROM devices WHERE last_seen > ?) as online,
      (SELECT COUNT(*) FROM alerts WHERE acknowledged = 0) as alerts,
      (SELECT COUNT(*) FROM device_data) as data_points
  `).get(Date.now() - CONFIG.offlineTimeout);
  const deviceCount = stats.total;
  const onlineCount = stats.online;
  const alertCount = stats.alerts;
  const dataCount = stats.data_points;

  res.json({
    server_time: Date.now(),
    total_devices: deviceCount,
    online_devices: onlineCount,
    offline_devices: deviceCount - onlineCount,
    unacknowledged_alerts: alertCount,
    total_data_points: dataCount,
    version: '1.0.0'
  });
}));

// ========== 离线检测定时任务 ==========
setInterval(() => {
  const now = Date.now();
  const offlineDevices = db.prepare(`
    SELECT id, name FROM devices
    WHERE status = 'online' AND last_seen < ?
  `).all(now - CONFIG.offlineTimeout);

  offlineDevices.forEach(d => {
    db.prepare("UPDATE devices SET status = 'offline' WHERE id = ?").run(d.id);
    if (shouldAlert(d.id, 'offline')) {
      const msg = `${d.name} 已离线，最后在线时间: ${new Date(now - CONFIG.offlineTimeout).toLocaleString()}`;
      insertAlert(d.id, 'offline', msg, 'critical', now);
      sendWechatAlert('【设备告警】设备离线', msg);
    }
  });

}, 30000);

// ========== 启动服务器 ==========
app.listen(PORT, '0.0.0.0', () => {
  console.log('========================================');
  console.log('  设备监控中心服务器已启动');
  console.log('  端口:', PORT);
  const maskedKey = API_KEY.length > 8 ? API_KEY.substring(0, 4) + '****' + API_KEY.slice(-4) : '****';
  console.log('  API Key:', maskedKey, API_KEY_LEGACY ? '（过渡期同时接受旧 Key）' : '');
  console.log('  微信推送:', CONFIG.wechatSendKey ? '已配置' : '未配置');
  console.log('========================================');
});
