package com.chumoreader.book;

/** 排版单元：标题 / 正文段 / 引用 / 分隔线 / 图片占位。 */
public class Block {

    public static final int PARA = 0;
    public static final int H1 = 1;
    public static final int H2 = 2;
    public static final int H3 = 3;
    public static final int QUOTE = 4;
    public static final int SEP = 5;
    public static final int IMG = 6;

    public int type;
    public String text;

    public Block(int type, String text) {
        this.type = type;
        this.text = text == null ? "" : text;
    }

    public static Block p(String text) {
        return new Block(PARA, text);
    }

    public static Block h(int level, String text) {
        return new Block(level <= 1 ? H1 : (level == 2 ? H2 : H3), text);
    }

    public static Block sep() {
        return new Block(SEP, "· · ·");
    }
}
