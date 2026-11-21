#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
初墨阅读 · 手工构建脚本（不依赖 Gradle / 不依赖网络）

流程：aapt2 compile -> aapt2 link -> javac -> d8 -> 打包对齐 -> apksigner
产物：build/ChumoReader-<variant>.apk
"""

import os
import shutil
import subprocess
import sys
import zipfile

ROOT = os.path.dirname(os.path.abspath(__file__))
APP = os.path.join(ROOT, "app", "src", "main")
BUILD = os.path.join(ROOT, "build")
GEN = os.path.join(BUILD, "gen")
CLASSES = os.path.join(BUILD, "classes")
DEX = os.path.join(BUILD, "dex")

def _detect_sdk():
    prop = os.path.join(ROOT, "local.properties")
    if os.path.exists(prop):
        for line in open(prop, encoding="utf-8"):
            if line.strip().startswith("sdk.dir="):
                return line.split("=", 1)[1].strip().replace("\\\\", "\\")
    return _guess_sdk()


def _guess_sdk():
    """没有任何配置时，扫一遍常见安装位置"""
    for key in ("ANDROID_SDK_ROOT", "ANDROID_HOME"):
        v = os.environ.get(key)
        if v and os.path.isdir(v):
            return v
    home = os.path.expanduser("~")
    for cand in (
        os.path.join(home, "AppData", "Local", "Android", "Sdk"),   # Windows
        os.path.join(home, "Library", "Android", "sdk"),            # macOS
        os.path.join(home, "Android", "Sdk"),                       # Linux
        "/usr/local/lib/android/sdk",
    ):
        if os.path.isdir(cand):
            return cand
    return ""


def _detect_jdk():
    """按 JAVA_HOME -> 常见安装位置 的顺序找 JDK 17"""
    v = os.environ.get("JAVA_HOME")
    if v and os.path.isdir(v):
        return v
    for cand in (
        r"D:\dsh-build\jdk-17.0.2",
        r"C:\Program Files\Java\jdk-17",
        r"C:\Program Files\Android\Android Studio\jbr",
        "/usr/lib/jvm/java-17-openjdk-amd64",
    ):
        if os.path.isdir(cand):
            return cand
    return ""


SDK = _detect_sdk()
JAVA_HOME = _detect_jdk()

if not SDK:
    raise SystemExit("找不到 Android SDK：请设置 ANDROID_SDK_ROOT，或在本目录建 local.properties 写入 sdk.dir=<路径>")
if not JAVA_HOME:
    raise SystemExit("找不到 JDK 17：请设置 JAVA_HOME 指向 JDK 17 安装目录")

BT = os.path.join(SDK, "build-tools", "34.0.0")
PLATFORM = os.path.join(SDK, "platforms", "android-34", "android.jar")

for _p, _hint in ((BT, "build-tools;34.0.0"), (PLATFORM, "platforms;android-34")):
    if not os.path.exists(_p):
        raise SystemExit("SDK 缺少组件 %s，请用 sdkmanager 安装：%s" % (_p, _hint))

AAPT2 = os.path.join(BT, "aapt2.exe")
D8 = os.path.join(BT, "d8.bat")
ZIPALIGN = os.path.join(BT, "zipalign.exe")
APKSIGNER = os.path.join(BT, "apksigner.bat")
JAVAC = os.path.join(JAVA_HOME, "bin", "javac.exe")
KEYTOOL = os.path.join(JAVA_HOME, "bin", "keytool.exe")

VERSION_CODE = "5"
VERSION_NAME = "1.0.4"


def run(cmd, **kw):
    printable = " ".join('"%s"' % c if " " in c else c for c in cmd)
    print("  > " + printable[:160])
    r = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace", **kw)
    if r.returncode != 0:
        print(r.stdout[-4000:] if r.stdout else "")
        print(r.stderr[-6000:] if r.stderr else "")
        raise SystemExit("命令失败：%s" % printable)
    return r


def clean():
    for p in (GEN, CLASSES, DEX, os.path.join(BUILD, "res.zip")):
        if os.path.isdir(p):
            shutil.rmtree(p, ignore_errors=True)
        elif os.path.isfile(p):
            os.remove(p)
    os.makedirs(GEN, exist_ok=True)
    os.makedirs(CLASSES, exist_ok=True)
    os.makedirs(DEX, exist_ok=True)


def java_files():
    out = []
    for base, _, files in os.walk(os.path.join(APP, "java")):
        for f in files:
            if f.endswith(".java"):
                out.append(os.path.join(base, f))
    for base, _, files in os.walk(GEN):
        for f in files:
            if f.endswith(".java"):
                out.append(os.path.join(base, f))
    return out


def ensure_keystore():
    ks = os.path.join(ROOT, "chumo-reader.jks")
    if os.path.exists(ks):
        return ks
    run([KEYTOOL, "-genkeypair", "-v", "-keystore", ks, "-alias", "chumo",
         "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
         "-storepass", "chumo123", "-keypass", "chumo123",
         "-dname", "CN=Chumo Reader, OU=Chumo, O=Chumo, L=CN, S=CN, C=CN"])
    return ks


def prepare_manifest():
    """aapt2 需要 manifest 带 package 属性；源文件保持干净，这里生成构建用副本。"""
    src = os.path.join(APP, "AndroidManifest.xml")
    text = open(src, encoding="utf-8").read()
    if "package=" not in text.split(">", 1)[0]:
        text = text.replace("<manifest ", '<manifest package="com.chumoreader" ', 1)
    out = os.path.join(BUILD, "AndroidManifest.xml")
    open(out, "w", encoding="utf-8").write(text)
    return out


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    flags = [a for a in sys.argv[1:] if a.startswith("--")]
    variant = args[0] if args else "debug"
    for p in (AAPT2, PLATFORM, JAVAC):
        if not os.path.exists(p):
            raise SystemExit("缺少构建工具：%s" % p)

    if "--no-clean" in flags:
        # 只改了几行代码时跳过清理：javac 会覆盖同名 class，省掉整目录重建
        print("[1/6] 跳过清理（--no-clean）")
        for d in (GEN, CLASSES, DEX):
            os.makedirs(d, exist_ok=True)
    else:
        print("[1/6] 清理")
        clean()

    print("[2/6] 编译资源")
    res_zip = os.path.join(BUILD, "res.zip")
    run([AAPT2, "compile", "--dir", os.path.join(APP, "res"), "-o", res_zip])

    print("[3/6] 链接资源并生成 R.java")
    unsigned = os.path.join(BUILD, "app-unsigned.apk")
    link = [AAPT2, "link", "-o", unsigned,
            "-I", PLATFORM,
            "--manifest", prepare_manifest(),
            "-R", res_zip,
            "--java", GEN,
            "--min-sdk-version", "21",
            "--target-sdk-version", "34",
            "--version-code", VERSION_CODE,
            "--version-name", VERSION_NAME,
            "--no-version-vectors",
            "--auto-add-overlay"]
    assets = os.path.join(APP, "assets")
    if os.path.isdir(assets):
        link += ["-A", assets]
    run(link)

    print("[4/6] javac 编译")
    srcs = java_files()
    arg_file = os.path.join(BUILD, "sources.txt")
    with open(arg_file, "w", encoding="utf-8") as f:
        for s in srcs:
            f.write('"%s"\n' % s.replace("\\", "\\\\"))
    run([JAVAC, "-J-Duser.language=en", "-J-Duser.country=US", "-source", "8", "-target", "8", "-encoding", "UTF-8",
         "-nowarn", "-Xlint:-options",
         "-classpath", PLATFORM,
         "-d", CLASSES,
         "@" + arg_file])

    print("[5/6] dex + 打包")
    class_files = []
    for base, _, files in os.walk(CLASSES):
        for f in files:
            if f.endswith(".class"):
                class_files.append(os.path.join(base, f))
    run([D8, "--lib", PLATFORM, "--min-api", "21", "--release",
         "--output", DEX] + class_files)

    dex_file = os.path.join(DEX, "classes.dex")
    staged = os.path.join(BUILD, "app-staged.apk")
    with zipfile.ZipFile(unsigned, "r") as zin, zipfile.ZipFile(staged, "w", zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            zout.writestr(item, zin.read(item.filename))
        zout.write(dex_file, "classes.dex")

    aligned = os.path.join(BUILD, "app-aligned.apk")
    run([ZIPALIGN, "-f", "-p", "4", staged, aligned])

    print("[6/6] 签名")
    ks = ensure_keystore()
    out_apk = os.path.join(BUILD, "ChumoReader-%s.apk" % variant)
    if os.path.exists(out_apk):
        os.remove(out_apk)
    run([APKSIGNER, "sign", "--ks", ks, "--ks-pass", "pass:chumo123",
         "--key-pass", "pass:chumo123", "--ks-key-alias", "chumo",
         "--min-sdk-version", "21", "--out", out_apk, aligned])

    size = os.path.getsize(out_apk) / 1024.0 / 1024.0
    print("\n构建完成：%s  (%.2f MB)" % (out_apk, size))


if __name__ == "__main__":
    main()
