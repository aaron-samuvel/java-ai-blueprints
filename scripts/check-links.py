#!/usr/bin/env python3
"""Fail if any relative link in a Markdown file or in site/*.html points at a file that does not exist.

External (http/https) links are not fetched here; check them by hand with curl when they change.
Usage: python3 scripts/check-links.py   (from anywhere; paths resolve from the repo root)
"""
import glob
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.chdir(ROOT)

MD_LINK = re.compile(r'\]\(([^)\s]+)(?:\s+"[^"]*")?\)')
HTML_LINK = re.compile(r'(?:href|src)="([^"]+)"')

files = [f for f in glob.glob("**/*.md", recursive=True) + glob.glob("site/**/*.html", recursive=True)
         if "/target/" not in f and not f.startswith("target/")]
broken = []
for f in files:
    text = open(f, encoding="utf-8").read()
    for link in MD_LINK.findall(text) + HTML_LINK.findall(text):
        if link.startswith(("http://", "https://", "mailto:", "#")):
            continue
        path = link.split("#")[0]
        if path and not os.path.exists(os.path.normpath(os.path.join(os.path.dirname(f), path))):
            broken.append(f"{f}: {link}")

print(f"checked {len(files)} files")
for b in broken:
    print("BROKEN", b)
sys.exit(1 if broken else 0)
