import com.chumoreader.book.Block;
import com.chumoreader.book.Chapter;
import com.chumoreader.book.DocxParser;
import com.chumoreader.book.Fb2Parser;
import com.chumoreader.book.EpubParser;
import com.chumoreader.book.HtmlBookParser;
import com.chumoreader.book.MobiParser;
import com.chumoreader.book.MarkdownParser;
import com.chumoreader.book.ParseResult;
import com.chumoreader.book.RtfParser;
import com.chumoreader.book.TxtParser;
import com.chumoreader.book.UmdParser;

import java.io.File;

/** 桌面端解析冒烟测试：打印章节结构，人工核对。 */
public class TestParse {

    public static void main(String[] args) throws Exception {
        for (String a : args) {
            File f = new File(a);
            if (!f.exists()) {
                System.out.println("MISSING " + a);
                continue;
            }
            String ext = a.substring(a.lastIndexOf('.') + 1).toLowerCase();
            ParseResult r;
            try {
                if (ext.equals("txt")) {
                    r = TxtParser.parse(f);
                } else if (ext.equals("md")) {
                    r = MarkdownParser.parse(f);
                } else if (ext.equals("html") || ext.equals("htm")) {
                    r = HtmlBookParser.parse(f);
                } else if (ext.equals("fb2")) {
                    r = Fb2Parser.parse(f);
                } else if (ext.equals("docx")) {
                    r = DocxParser.parse(f);
                } else if (ext.equals("rtf")) {
                    r = RtfParser.parse(f);
                } else if (ext.equals("epub")) {
                    r = EpubParser.parse(f);
                } else if (ext.equals("mobi") || ext.equals("azw3")) {
                    r = MobiParser.parse(f);
                } else if (ext.equals("umd")) {
                    r = UmdParser.parse(f);
                } else {
                    System.out.println("SKIP " + a);
                    continue;
                }
            } catch (Exception e) {
                System.out.println("FAIL " + f.getName() + " -> " + e);
                continue;
            }
            System.out.println("FILE   " + f.getName());
            System.out.println("TITLE  " + r.title);
            System.out.println("AUTHOR " + r.author);
            System.out.println("CHAPS  " + r.chapters.size() + "  CHARS " + r.totalChars());
            int show = Math.min(6, r.chapters.size());
            for (int i = 0; i < show; i++) {
                Chapter c = r.chapters.get(i);
                System.out.println("  [" + i + "] " + c.title + "  blocks=" + c.blocks.size()
                        + " chars=" + c.charCount());
            }
            if (!r.chapters.isEmpty()) {
                Chapter c = r.chapters.get(r.chapters.size() - 1);
                System.out.println("  [last] " + c.title + " chars=" + c.charCount());
                Block b = null;
                for (Block x : c.blocks) {
                    if (x.type == Block.PARA) {
                        b = x;
                        break;
                    }
                }
                if (b != null) {
                    String t = b.text.length() > 90 ? b.text.substring(0, 90) + "..." : b.text;
                    System.out.println("  sample: " + t);
                }
            }
            System.out.println();
        }
    }
}
