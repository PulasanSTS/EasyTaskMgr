// 准备开发用服务器目录：EULA + 离线模式（可选：给测试玩家 op）。
//
// 运行：node tools/mcp-tests/setup-server.mjs [--with-op 玩家名]
//
// 注意：
// - 默认只写 EULA 与 online-mode=false，**不会**自动给任何人 op；
// - `--with-op` 是显式选择，且会合并现有 ops.json（不会清掉别的管理员）；
// - 只用于本地开发世界，不要对公网服务器运行本脚本。

import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { existsSync } from 'node:fs';
import { join } from 'node:path';

const dir = process.env.MCP_SERVER_DIR ?? 'run';
const args = process.argv.slice(2);
const opIndex = args.indexOf('--with-op');
const opPlayer = opIndex >= 0 ? args[opIndex + 1] : undefined;

if (opIndex >= 0 && (!opPlayer || opPlayer.startsWith('--'))) {
  console.error('用法: node tools/mcp-tests/setup-server.mjs [--with-op <玩家名>]');
  process.exit(2);
}

/** 离线模式 UUID：UUID v3(MD5) of "OfflinePlayer:<name>"，与 Minecraft 一致。 */
function offlineUuid(name) {
  const hash = createHash('md5').update(`OfflinePlayer:${name}`, 'utf8').digest();
  hash[6] = (hash[6] & 0x0f) | 0x30;
  hash[8] = (hash[8] & 0x3f) | 0x80;
  const hex = hash.toString('hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

await mkdir(dir, { recursive: true });
await writeFile(join(dir, 'eula.txt'), 'eula=true\n', 'utf8');

// 只改 online-mode，别的设置保持原样；不启用命令方块，避免不可信存档执行命令。
const properties = join(dir, 'server.properties');
const defaults = [
  'online-mode=false',
  'enable-command-block=false',
  'motd=EasyTaskMgr MCP tests (local only)',
  '',
];
const text = existsSync(properties) ? await readFile(properties, 'utf8') : '';
const lines = text.length > 0 ? text.split(/\r?\n/) : defaults;
let patched = lines.filter((line) => !line.startsWith('enable-command-block='));
patched = patched.some((line) => line.startsWith('online-mode='))
  ? patched.map((line) => (line.startsWith('online-mode=') ? 'online-mode=false' : line))
  : [...patched, 'online-mode=false'];
patched.push('enable-command-block=false');
await writeFile(
  properties,
  `${patched.join('\n').replace(/\n{3,}/g, '\n\n').trimEnd()}\n`,
  'utf8',
);

if (opPlayer) {
  const opsFile = join(dir, 'ops.json');
  const existing = existsSync(opsFile) ? JSON.parse(await readFile(opsFile, 'utf8')) : [];
  const entry = { uuid: offlineUuid(opPlayer), name: opPlayer, level: 4, bypassesPlayerLimit: true };
  const merged = [
    ...existing.filter((op) => op.uuid !== entry.uuid && op.name !== entry.name),
    entry,
  ];
  await writeFile(opsFile, `${JSON.stringify(merged, null, 2)}\n`, 'utf8');
  console.warn(
    `[warn] 已给 "${opPlayer}" 写入 op（仅限本地测试世界！）。` +
      `本脚本不会启用命令方块，也不会清掉已有管理员。`,
  );
}

console.log(`已准备 ${dir}: eula=true, online-mode=false${opPlayer ? `, op=${opPlayer}` : ''}`);
