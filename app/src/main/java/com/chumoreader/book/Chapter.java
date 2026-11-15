package com.chumoreader.book;

import java.util.ArrayList;
import java.util.List;

/** 一章：标题 + 若干排版单元。 */
public class Chapter {

    public String title;
    public String subtitle;
    public final List<Block> blocks = new ArrayList<>();

    public Chapter() {
    }

    public Chapter(String title) {
        this.title = title;
    }

    public void add(Block b) {
        if (b != null) {
            blocks.add(b);
        }
    }

    public void add(String text) {
        if (text != null && text.trim().length() > 0) {
            blocks.add(Block.p(text.trim()));
        }
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    /** 纯文本行数，用于字数统计与目录摘要。 */
    public int charCount() {
        int n = 0;
        for (Block b : blocks) {
            if (b.type != Block.IMG) {
                n += b.text.length();
            }
        }
        return n;
    }
}
