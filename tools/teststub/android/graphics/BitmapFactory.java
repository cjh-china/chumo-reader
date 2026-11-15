package android.graphics;
/** 仅用于桌面端解析测试的占位类。 */
public class BitmapFactory {
    public static class Options {
        public boolean inJustDecodeBounds;
        public int outWidth;
        public int outHeight;
        public int inSampleSize = 1;
        public Bitmap.Config inPreferredConfig;
    }
    public static Bitmap decodeByteArray(byte[] data, int offset, int length) { return null; }
    public static Bitmap decodeByteArray(byte[] data, int offset, int length, Options opts) { return null; }
}
