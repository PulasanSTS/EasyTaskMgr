// 单人回归：收集任务自动完成 + HUD 截图
//
// 前置：客户端装了 Minecraft MCP + JsMacros 并进入一个世界；测试玩家需要 op（/give），
// 本地服务器可用 `node tools/mcp-tests/setup-server.mjs --with-op <玩家名>` 准备。
//
// 运行：node tools/mcp-tests/single.mjs

import { mkdir, writeFile } from 'node:fs/promises';
import { Bridge, Report, loadConfig } from './bridge.mjs';

const report = new Report('single');
const ITEM = 'minecraft:diamond';
const GIVE = 5;
const DEADLINE_MS = 5000;

/** 共享的 JS 片段：按 id 找任务、数背包物品。 */
const HELPERS = `
function etmStore() {
  const Store = Java.type("com.easytaskmgr.task.TaskStore");
  return Store.class.getDeclaredMethod("get").invoke(null);
}
function etmTaskById(id) {
  const tasks = etmStore().all();
  for (let i = 0; i < tasks.size(); i++) if (tasks.get(i).id === id) return tasks.get(i);
  return null;
}
function etmCount(itemId) {
  const id = Java.type("net.minecraft.resources.Identifier").tryParse(itemId);
  const item = Java.type("net.minecraft.core.registries.BuiltInRegistries").ITEM.getValue(id);
  const inv = Player.getPlayer().getInventory();
  let total = 0;
  for (let i = 0; i < inv.size(); i++) {
    const stack = inv.getStack(i);
    if (!stack.isEmpty() && stack.is(item)) total += stack.getCount();
  }
  return total;
}
`;

async function countItem(bridge) {
  const out = await bridge.execute(`${HELPERS}\nconsole.log(etmCount(${JSON.stringify(ITEM)}));`);
  return Number(out.trim().split('\n').pop());
}

async function createTask(bridge, targetCount) {
  const code = `${HELPERS}
const Task = Java.type("com.easytaskmgr.task.Task");
const t = new Task();
t.text = "MCP collect ${targetCount} diamonds";
t.type = Java.type("com.easytaskmgr.task.TaskType").COLLECT;
t.target = ${JSON.stringify(ITEM)};
t.targetCount = ${targetCount};
t.priority = 2;
etmStore().add(t);
etmStore().save();
console.log(t.id);
`;
  return (await bridge.execute(code)).trim().split('\n').pop();
}

async function state(bridge, id) {
  const out = await bridge.execute(`${HELPERS}
const t = etmTaskById(${JSON.stringify(id)});
console.log(JSON.stringify({ found: t !== null, done: t ? t.done : null, completedAt: t ? t.completedAt : 0 }));
`);
  return JSON.parse(out.trim().split('\n').pop());
}

async function removeTask(bridge, id) {
  await bridge.execute(`${HELPERS}
const t = etmTaskById(${JSON.stringify(id)});
if (t !== null) { etmStore().remove(t); etmStore().save(); }
console.log("REMOVED");
`);
}

await report.run('collect task', async () => {
  const bridge = new Bridge(await loadConfig());
  report.check('bridge status', (await bridge.status()).enabled === true);

  const before = await countItem(bridge);
  const target = before + GIVE;
  const id = await createTask(bridge, target);
  report.check('task created', typeof id === 'string' && id.length > 0, id);

  let current = await state(bridge, id);
  report.check('not done before items', current.found === true && current.done === false, JSON.stringify(current));

  const giveStarted = Date.now();
  await bridge.execute(`Chat.say("/give @s ${ITEM} ${GIVE}"); console.log("GIVEN");`);

  let after = before;
  for (let i = 0; i < 10 && after < target; i++) {
    await new Promise((resolve) => setTimeout(resolve, 200));
    after = await countItem(bridge);
  }
  report.check(
    'items were given',
    after >= target,
    `${before} -> ${after}（需要 op：setup-server.mjs --with-op <玩家名>）`,
  );

  let elapsed = 0;
  while (Date.now() - giveStarted < DEADLINE_MS) {
    current = await state(bridge, id);
    if (current.done === true) {
      elapsed = Date.now() - giveStarted;
      break;
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  report.check(`completed within ${DEADLINE_MS}ms`, current.done === true, JSON.stringify(current));
  report.check('completedAt is set', current.completedAt > 0, String(current.completedAt));

  await mkdir('tools/mcp-tests/out', { recursive: true });
  await writeFile('tools/mcp-tests/out/single.png', await bridge.screenshot());
  console.log(`  截图: tools/mcp-tests/out/single.png（完成用时 ${elapsed || 'n/a'}ms）`);

  await removeTask(bridge, id);
  await bridge.chat('single regression done');
});

report.finish();
