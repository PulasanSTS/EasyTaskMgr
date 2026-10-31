package com.easytaskmgr.client;

/**
 * 兼容层：旧代码里的共享调用点统一指向 {@link ClientShared}。
 */
public final class ClientNet {
	private ClientNet() {
	}

	public static void register() {
		ClientShared.register();
	}

	public static boolean serverHasMod() {
		return ClientShared.available();
	}

	public static void reset() {
	}

	public static void markDirty() {
	}

	public static void syncIfNeeded() {
	}
}