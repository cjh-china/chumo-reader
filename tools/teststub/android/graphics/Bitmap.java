package android.graphics;
/** 仅用于桌面端解析测试的占位类。 */
public class Bitmap {
    public enum Config { ALPHA_8, RGB_565, ARGB_4444, ARGB_8888 }
    public void eraseColor(int c) { }
    public int getWidth() { return 0; }
    public int getHeight() { return 0; }
    public void recycle() { }
}
