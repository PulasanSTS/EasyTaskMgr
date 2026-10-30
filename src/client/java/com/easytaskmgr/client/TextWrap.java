package com.easytaskmgr.client;

import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本排版小工具：按像素宽度换行，替代「省略号截断」。
 *
 * <p>中文没有空格，所以按字符逐个累加宽度；遇到空格时优先在空格处断行，
 * 避免把英文单词从中间劈开。
 */
public final class TextWrap {
	private TextWrap() {
	}

	/** 把 text 折成若干行，每行宽度不超过 maxWidth。 */
	public static List<String> wrap(Font font, String text, int maxWidth) {
		List<String> lines = new ArrayList<>();
		if (text == null || text.isEmpty()) {
			lines.add("");
			return lines;
		}
		for (String paragraph : text.split("\n", -1)) {
			if (paragraph.isEmpty()) {
				lines.add("");
				continue;
			}
			StringBuilder line = new StringBuilder();
			int lastSpace = -1;
			for (int i = 0; i < paragraph.length(); i++) {
				char c = paragraph.charAt(i);
				String candidate = line.toString() + c;
				if (font.width(candidate) > maxWidth && line.length() > 0) {
					if (lastSpace > 0 && lastSpace < line.length() - 1 && font.width(line.substring(0, lastSpace)) > maxWidth / 2) {
						// 回到最近的空格处断行，剩下的部分接到下一行
						String head = line.substring(0, lastSpace);
						String tail = line.substring(lastSpace + 1) + c;
						lines.add(head);
						line = new StringBuilder(tail);
					} else {
						lines.add(line.toString());
						line = new StringBuilder().append(c);
					}
					lastSpace = line.indexOf(" ");
				} else {
					line.append(c);
					if (c == ' ') {
						lastSpace = line.length() - 1;
					}
				}
			}
			if (line.length() > 0) {
				lines.add(line.toString());
			}
		}
		return lines;
	}

	/** 单行收窄到指定宽度，超出时截断并补省略号（仅用于明确只要一行的场景）。 */
	public static String clip(Font font, String text, int maxWidth) {
		if (text == null) {
			return "";
		}
		if (font.width(text) <= maxWidth) {
			return text;
		}
		return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
	}
}
