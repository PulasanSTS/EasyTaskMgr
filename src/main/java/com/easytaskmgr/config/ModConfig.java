package com.easytaskmgr.config;

import com.easytaskmgr.EasyTaskMgr;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 客户端配置，保存在 {@code config/easytaskmgr.json}。
 */
public final class ModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final ModConfig INSTANCE = new ModConfig();

	/** HUD 面板右边缘位置，百分比（0-100）。 */
	public int hudXPercent = 99;
	/** HUD 面板上边缘位置，百分比（0-100）。 */
	public int hudYPercent = 2;
	/** HUD 面板最大宽度（像素），长文本按这个宽度换行。 */
	public int hudMaxWidth = 200;
	/** 是否绘制半透明背景。 */
	public boolean hudBackground = true;
	/** HUD 最多显示几条待办。 */
	public int hudCompactLimit = 8;
	/** HUD 当前是否隐藏（H 键切换）。 */
	public boolean hudHidden = false;
	/** 是否在 HUD 上显示队友的待办。 */
	public boolean showTeammates = true;
	/** 是否显示坐标任务的距离与方向。 */
	public boolean showDistance = true;
	/** 是否显示移动速度。 */
	public boolean showSpeed = true;
	/** 坐标任务是否在 HUD 上显示指向箭头。 */
	public boolean showDirectionArrow = true;
	/** 成就前置条件 UI（暂时关闭，代码保留）。 */
	public boolean advancementRequirements = false;
	/** HUD 交互模式是否开启（开启后鼠标释放，点击普通任务可完成）。 */
	public boolean hudInteractive = false;
	/** 一个游戏日多少 tick（整合包改过昼夜周期时调整，默认 24000）。 */
	public int ticksPerDay = 24000;
	/** 语言：auto（跟随 Minecraft）/ zh_cn / en_us。 */
	public String language = "auto";
	/** 完成 / 提醒时是否播放音效。 */
	public boolean soundEnabled = true;

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("easytaskmgr.json");
	}

	public static ModConfig get() {
		return INSTANCE;
	}

	public void load() {
		Path path = file();
		if (!Files.exists(path)) {
			save();
			return;
		}
		try {
			ModConfig loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), ModConfig.class);
			if (loaded != null) {
				copyFrom(loaded);
			}
			EasyTaskMgr.LOGGER.info("已载入配置: {}", path);
		} catch (Exception e) {
			EasyTaskMgr.LOGGER.error("读取配置失败: {}", path, e);
		}
	}

	public void save() {
		Path path = file();
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (Exception e) {
			EasyTaskMgr.LOGGER.error("写入配置失败: {}", path, e);
		}
	}

	private void copyFrom(ModConfig o) {
		hudXPercent = clamp(o.hudXPercent, 0, 100);
		hudYPercent = clamp(o.hudYPercent, 0, 100);
		hudMaxWidth = clamp(o.hudMaxWidth, 120, 400);
		hudBackground = o.hudBackground;
		hudCompactLimit = clamp(o.hudCompactLimit, 1, 20);
		hudHidden = o.hudHidden;
		showTeammates = o.showTeammates;
		showDistance = o.showDistance;
		showSpeed = o.showSpeed;
		showDirectionArrow = o.showDirectionArrow;
		advancementRequirements = o.advancementRequirements;
		hudInteractive = o.hudInteractive;
		ticksPerDay = o.ticksPerDay >= 2000 ? o.ticksPerDay : 24000;
		language = (o.language != null && (o.language.equals("zh_cn") || o.language.equals("en_us"))) ? o.language : "auto";
		soundEnabled = o.soundEnabled;
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}
}
