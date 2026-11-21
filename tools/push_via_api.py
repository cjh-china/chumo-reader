#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""git push 被代理挡住时的应急通道：用 gh 的 API 把本地 HEAD 的树推成一个提交。

只做一件事：以远端 main 为父提交，把「本地 HEAD 相对父提交变了的文件」写成新 blob，
组一棵树、建一个提交、移动 refs/heads/main。完成后本地用 update-ref 对齐到新提交。
"""
import base64
import json
import subprocess
import sys

REPO = sys.argv[1] if len(sys.argv) > 1 else "cjh-china/chumo-reader"
ROOT = sys.argv[2] if len(sys.argv) > 2 else r"D:\chumo reader"
BRANCH = sys.argv[3] if len(sys.argv) > 3 else "main"


def gh(*args, body=None):
    cmd = ["gh", "api"] + list(args)
    data = None
    if body is not None:
        # gh 只从 --input 读请求体，stdin 要用 "-"
        cmd += ["--input", "-"]
        data = json.dumps(body).encode("utf-8")
    p = subprocess.run(cmd, input=data, cwd=ROOT, capture_output=True)
    if p.returncode != 0:
        raise SystemExit("gh api 失败: %s\n%s" % (" ".join(cmd), p.stderr.decode("utf-8", "replace")))
    out = p.stdout.decode("utf-8", "replace").strip()
    return json.loads(out) if out else None


def git(*args):
    p = subprocess.run(["git"] + list(args), cwd=ROOT, capture_output=True)
    if p.returncode != 0:
        raise SystemExit("git %s 失败: %s" % (" ".join(args), p.stderr.decode("utf-8", "replace")))
    return p.stdout.decode("utf-8").strip()


def main():
    remote = gh("repos/%s/git/ref/heads/%s" % (REPO, BRANCH))
    base_sha = remote["object"]["sha"]
    base = gh("repos/%s/git/commits/%s" % (REPO, base_sha))
    print("远端 main:", base_sha[:8], base["message"].splitlines()[0][:40])

    # 远端提交本地不一定有（上一次就是走 API 推的），允许用本地等价基点来算差异
    local_base = sys.argv[4] if len(sys.argv) > 4 else base_sha
    changed = git("diff", "--name-only", local_base, "HEAD").splitlines()
    if not changed:
        print("没有差异，无需推送")
        return
    print("待推送文件 %d 个" % len(changed))

    entries = []
    for path in changed:
        raw = open("%s\\%s" % (ROOT, path.replace("/", "\\")), "rb").read()
        blob = gh("-X", "POST", "repos/%s/git/blobs" % REPO, body={
            "content": base64.b64encode(raw).decode("ascii"),
            "encoding": "base64",
        })
        entries.append({"path": path, "mode": "100644", "type": "blob", "sha": blob["sha"]})
        print("  blob %s  %s (%d B)" % (blob["sha"][:8], path, len(raw)))

    tree = gh("-X", "POST", "repos/%s/git/trees" % REPO, body={
        "base_tree": base["tree"]["sha"],
        "tree": entries,
    })

    message = git("log", "-1", "--pretty=%B")
    author_name = git("log", "-1", "--pretty=%an")
    author_email = git("log", "-1", "--pretty=%ae")
    commit = gh("-X", "POST", "repos/%s/git/commits" % REPO, body={
        "message": message,
        "tree": tree["sha"],
        "parents": [base_sha],
        "author": {"name": author_name, "email": author_email},
    })
    print("新提交:", commit["sha"][:8])

    gh("-X", "PATCH", "repos/%s/git/refs/heads/%s" % (REPO, BRANCH), body={
        "sha": commit["sha"], "force": False,
    })
    print("refs/heads/%s -> %s" % (BRANCH, commit["sha"][:8]))
    print("NEW_SHA=%s" % commit["sha"])


if __name__ == "__main__":
    main()
