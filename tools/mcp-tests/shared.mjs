// 双人回归：共享任务 认领 → 完成 → 快照同步
//
// 需要两个都装了模组（并且装了 Minecraft MCP + JsMacros）的客户端，登录同一个服务器：
//   A 客户端：run/            （默认）
//   B 客户端：run-b/          （第二份游戏目录，端口/token 自动读取）
//
// 运行：node tools/mcp-tests/shared.mjs
// 自定义：MCP_A_RUN_DIR / MCP_B_RUN_DIR，或 MCP_A_PORT+MCP_A_TOKEN / MCP_B_PORT+MCP_B_TOKEN

import { mkdir, writeFile } from 'node:fs/promises';
import { Bridge, Report, loadConfig, JS_HELPERS } from './bridge.mjs';

const report = new Report('shared');

/** 通过环境变量或 run 目录拿到两个客户端的桥接配置。 */
async function loadPair() {
  const a = await loadConfig({
    dir: process.env.MCP_A_RUN_DIR ?? 'run',
    port: process.env.MCP_A_PORT,
    token: process.env.MCP_A_TOKEN,
  });
  const b = await loadConfig({
    dir: process.env.MCP_B_RUN_DIR ?? 'run-b',
    port: process.env.MCP_B_PORT,
    token: process.env.MCP_B_TOKEN,
  });
  return [new Bridge({ ...a, label: 'A' }), new Bridge({ ...b, label: 'B' })];
}

async function publishTask(a, text) {
  const code = `
${JS_HELPERS}
const Task = Java.type("com.easytaskmgr.task.Task");
const t = new Task();
t.text = ${JSON.stringify(text)};
t.type = Java.type("com.easytaskmgr.task.TaskType").NORMAL;
t.visibility = 1;
t.shareMode = 2; // 任一（含发布者）
Java.type("com.easytaskmgr.client.ClientShared").publish(t);
console.log(t.id);
`;
  return (await a.execute(code)).trim().split('\n').pop();
}

/** 读取某个客户端看到的共享条目快照。 */
async function snapshot(bridge, id) {
  const code = `
${JS_HELPERS}
const CS = Java.type("com.easytaskmgr.client.ClientShared");
const e = CS.find(${JSON.stringify(id)});
console.log(JSON.stringify(e === null ? { found: false } : {
  found: true,
  done: e.done(),
  claimed: e.claimed(),
  claims: e.claims(),
  mine: e.mine(),
  canClaim: e.canClaim(),
  doneByMe: e.doneByMe(),
}));
`;
  const out = (await bridge.execute(code)).trim().split('\n').pop();
  return JSON.parse(out);
}

async function claim(bridge, id) {
  return bridge.execute(`
Java.type("com.easytaskmgr.client.ClientShared").claim(${JSON.stringify(id)});
console.log("CLAIMED");
`);
}

async function complete(bridge, id) {
  return bridge.execute(`
Java.type("com.easytaskmgr.client.ClientShared").complete(${JSON.stringify(id)});
console.log("COMPLETED");
`);
}

await report.run('shared task', async () => {
  const [a, b] = await loadPair();
  report.check('bridge A', (await a.status()).enabled === true);
  report.check('bridge B', (await b.status()).enabled === true);

  const id = await publishTask(a, `MCP shared task ${Date.now()}`);
  report.check('published', typeof id === 'string' && id.length > 0, id);

  await new Promise((resolve) => setTimeout(resolve, 2000));
  let onB = await snapshot(b, id);
  report.check('B sees the shared task', onB.found === true, JSON.stringify(onB));
  report.check('B may claim it', onB.found === true && onB.canClaim === true, JSON.stringify(onB));

  // A 是发布者，server 只允许「任一（含发布者）」模式下的发布者直接上报完成，先验证 B 认领
  await claim(b, id);
  await new Promise((resolve) => setTimeout(resolve, 2000));
  const afterClaim = await snapshot(a, id);
  report.check('A sees B as claimant', afterClaim.claims >= 1, JSON.stringify(afterClaim));

  await complete(b, id);
  await new Promise((resolve) => setTimeout(resolve, 2000));
  const afterComplete = await snapshot(a, id);
  report.check('task completes for A', afterComplete.done === true, JSON.stringify(afterComplete));

  const doneForB = await snapshot(b, id);
  report.check('task completes for B', doneForB.done === true, JSON.stringify(doneForB));

  // 清理：发布者撤回测试任务，避免共享表越积越多
  await a.execute(`
Java.type("com.easytaskmgr.client.ClientShared").revoke(${JSON.stringify(id)});
console.log("REVOKED");
`);

  await mkdir('tools/mcp-tests/out', { recursive: true });
  await writeFile('tools/mcp-tests/out/shared-a.png', await a.screenshot());
  await writeFile('tools/mcp-tests/out/shared-b.png', await b.screenshot());
  console.log('  截图: tools/mcp-tests/out/shared-{a,b}.png');
});

report.finish();
