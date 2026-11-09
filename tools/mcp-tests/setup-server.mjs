// 准备 run-server/：EULA、离线模式、以及给测试玩家 op（/give 需要）
//
// 运行：node tools/mcp-tests/setup-server.mjs [玩家名]
// 默认玩家名 Tester（与 runClient 的 --username 一致）。

import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { existsSync } from 'node:fs';
import { join } from 'node:path';

const dir = process.env.MCP_SERVER_DIR ?? 'run';
const player = process.argv[2] ?? process.env.MCP_TEST_PLAYER ?? 'Tester';

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

const properties = join(dir, 'server.properties');
if (existsSync(properties)) {
  const text = await readFile(properties, 'utf8');
  const patched = text
    .replace(/^online-mode=.*$/m, 'online-mode=false')
    .replace(/^enable-command-block=.*$/m, 'enable-command-block=true');
  await writeFile(properties, patched, 'utf8');
} else {
  await writeFile(
    properties,
    ['online-mode=false', 'enable-command-block=true', 'motd=EasyTaskMgr MCP tests', ''].join('\n'),
    'utf8',
  );
}

const ops = join(dir, 'ops.json');
const entry = {
  uuid: offlineUuid(player),
  name: player,
  level: 4,
  bypassesPlayerLimit: true,
};
await writeFile(ops, `${JSON.stringify([entry], null, 2)}\n`, 'utf8');

console.log(`已准备 ${dir}: eula=true, online-mode=false, op=${player} (${entry.uuid})`);
