// 单人回归：收集任务自动完成 + HUD 截图
//
// 前置：客户端装了 Minecraft MCP + JsMacros，并已经进入一个世界（单人/服务器都行，
// 收集检测在客户端做，所以往客户端背包塞物品就够了）。
//
// 运行：node tools/mcp-tests/single.mjs

import { mkdir, writeFile } from 'node:fs/promises';
import { Bridge, Report, loadConfig, JS_HELPERS } from './bridge.mjs';

const report = new Report('single');

async function ensureTask(bridge, { text, target, count }) {
  const code = `
${JS_HELPERS}
const Task = Java.type("com.easytaskmgr.task.Task");
const t = new Task();
t.text = ${JSON.stringify(text)};
t.type = Java.type("com.easytaskmgr.task.TaskType").COLLECT;
t.target = ${JSON.stringify(target)};
t.targetCount = ${count};
t.priority = 2;
etmStore().add(t);
etmStore().save();
console.log(t.id);
`;
  return (await bridge.execute(code)).trim().split('\n').pop();
}

/** 通过 /give 把物品塞进背包；需要测试玩家有 op（见 setup-server.mjs）。 */
async function giveItem(bridge, itemId, amount) {
  const code = `
Chat.say("/give @s ${itemId} ${amount}");
console.log("GIVEN");
`;
  return bridge.execute(code);
}

async function taskState(bridge, id) {
  const code = `
${JS_HELPERS}
const t = etmTaskById(${JSON.stringify(id)});
console.log(JSON.stringify({ found: t !== null, done: t ? t.done : null }));
`;
  const out = (await bridge.execute(code)).trim().split('\n').pop();
  return JSON.parse(out);
}

await report.run('collect task', async () => {
  const config = await loadConfig();
  const bridge = new Bridge(config);
  report.check('bridge status', (await bridge.status()).enabled === true);

  const id = await ensureTask(bridge, { text: 'MCP collect 5 diamonds', target: 'minecraft:diamond', count: 5 });
  report.check('task created', typeof id === 'string' && id.length > 0, id);

  let state = await taskState(bridge, id);
  report.check('not done before items', state.found === true && state.done === false);

  await giveItem(bridge, 'minecraft:diamond', 5);
  await new Promise((resolve) => setTimeout(resolve, 3500));

  state = await taskState(bridge, id);
  report.check('done after 5 diamonds', state.found === true && state.done === true, JSON.stringify(state));

  await mkdir('tools/mcp-tests/out', { recursive: true });
  await writeFile('tools/mcp-tests/out/single.png', await bridge.screenshot());
  await bridge.chat('single regression done');
  console.log('  截图: tools/mcp-tests/out/single.png');
});

report.finish();
