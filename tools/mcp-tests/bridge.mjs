// Minimal client for the Minecraft MCP bridge that the client mod exposes on localhost.
//
// It speaks the same HTTP protocol the official MCP server uses:
//   GET  /status
//   POST /command  {op: 'execute'|'screenshot'|'chat'|'stop', ...}
//   GET  /output?task=<id>&wait_ms=<ms>
//
// We talk to it directly so the regression scripts need no MCP SDK, no stdio
// handshake and no agent runtime - just Node 22+.

import { readFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { join } from 'node:path';

/** Load the bridge settings the mod writes into run/config/minecraft_mcp.json. */
export async function loadConfig({ dir = process.env.MCP_RUN_DIR ?? 'run', port, token } = {}) {
  if (port && token) return { port: Number(port), token };
  const file = join(dir, 'config', 'minecraft_mcp.json');
  if (!existsSync(file)) {
    throw new Error(
      `找不到 ${file}。先启动带 Minecraft MCP 模组的客户端（见 tools/mcp-tests/README.md）。`,
    );
  }
  const cfg = JSON.parse(await readFile(file, 'utf8'));
  return { port: Number(cfg.port), token: cfg.token };
}

export class Bridge {
  constructor({ port, token, label = 'client' }) {
    this.port = port;
    this.token = token;
    this.label = label;
  }

  async request(path, body) {
    const response = await fetch(`http://127.0.0.1:${this.port}${path}`, {
      method: body ? 'POST' : 'GET',
      headers: {
        Authorization: `Bearer ${this.token}`,
        'X-Minecraft-Mcp-Protocol': '1',
        ...(body ? { 'Content-Type': 'application/json' } : {}),
      },
      body: body ? JSON.stringify(body) : undefined,
      signal: AbortSignal.timeout(15000),
    });
    if (!response.ok) {
      throw new Error(`[${this.label}] HTTP ${response.status}: ${await response.text()}`);
    }
    return response.json();
  }

  async status() {
    return this.request('/status');
  }

  /** Run JsMacros JavaScript and return its buffered console output. */
  async execute(code, { wait = 1500 } = {}) {
    const { task } = await this.request('/command', { op: 'execute', code });
    return this.output(task, wait);
  }

  async output(task, wait = 1500) {
    const result = await this.request(`/output?task=${encodeURIComponent(task)}&wait_ms=${wait}`);
    if (result.error) throw new Error(`[${this.label}] 脚本错误: ${result.error}`);
    return result.output ?? '';
  }

  /** Show a message in the local chat as [AGENT]. */
  async chat(message) {
    return this.request('/command', { op: 'chat', message });
  }

  /** Return the screenshot as a Buffer. */
  async screenshot() {
    const result = await this.request('/command', { op: 'screenshot' });
    if (result.mimeType !== 'image/png' || typeof result.data !== 'string') {
      throw new Error(`[${this.label}] 截图响应无效`);
    }
    return Buffer.from(result.data, 'base64');
  }
}

/** 断言工具：失败时收集错误而不是立刻退出，方便一次跑完所有用例。 */
export class Report {
  constructor(name) {
    this.name = name;
    this.failures = [];
    this.passes = 0;
  }

  check(label, condition, detail = '') {
    if (condition) {
      this.passes++;
      console.log(`  ok   ${label}`);
    } else {
      this.failures.push(`${label}${detail ? ` (${detail})` : ''}`);
      console.log(`  FAIL ${label}${detail ? ` (${detail})` : ''}`);
    }
  }

  async run(label, fn) {
    try {
      await fn();
    } catch (error) {
      this.failures.push(`${label}: ${error.message}`);
      console.log(`  FAIL ${label}: ${error.message}`);
    }
  }

  finish() {
    console.log('');
    if (this.failures.length > 0) {
      console.log(`${this.name}: ${this.passes} passed, ${this.failures.length} failed`);
      for (const failure of this.failures) console.log(`  - ${failure}`);
      process.exitCode = 1;
    } else {
      console.log(`${this.name}: ${this.passes} passed`);
    }
  }
}

/** JsMacros snippet: read a Java field / call a static method through reflection. */
export const JS_HELPERS = `
function etmStore() {
  const Store = Java.type("com.easytaskmgr.task.TaskStore");
  return Store.class.getDeclaredMethod("get").invoke(null);
}
function etmTaskById(id) {
  const tasks = etmStore().all();
  for (let i = 0; i < tasks.size(); i++) {
    if (tasks.get(i).id === id) return tasks.get(i);
  }
  return null;
}
`;
