#!/usr/bin/env python3
"""Exercise the real, already-configured AI text probe on a connected emulator."""
import argparse
import os
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET

p = argparse.ArgumentParser()
p.add_argument('--adb', default=os.environ.get('ADB', str(Path.home() / 'AppData/Local/Android/Sdk/platform-tools/adb.exe')))
p.add_argument('--serial', default='emulator-5554')
a = p.parse_args()
base = [a.adb, '-s', a.serial]
def adb(*args):
    return subprocess.run(base + list(args), check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout
def labels():
    adb('shell', 'uiautomator', 'dump', '/sdcard/yazhang-probe-window.xml')
    root = ET.fromstring(adb('shell', 'cat', '/sdcard/yazhang-probe-window.xml'))
    return {n.get('text', '') for n in root.iter('node')}

adb('shell', 'am', 'force-stop', 'com.example.yazhang')
adb('shell', 'am', 'start', '-n', 'com.example.yazhang/.MainActivity')
time.sleep(2)
# MainActivity starts on the ledger tab. Click the Settings tab and text test.
adb('shell', 'input', 'tap', '920', '2300')
time.sleep(1)
if '测试文本连接' not in labels():
    raise SystemExit('未找到文本连接测试按钮；请检查模拟器分辨率和当前页面。')
adb('shell', 'input', 'tap', '500', '970')
end = time.monotonic() + 75
while time.monotonic() < end:
    time.sleep(2)
    texts = labels()
    if '测试成功' in texts:
        print('PASS: 真实文本连接成功，界面正确显示测试成功。')
        break
    if '连接测试失败' in texts:
        print('FAIL: 界面显示连接测试失败。')
        print('文本探测成功' if '文本探测成功' in texts else '服务端或网络未通过探测。')
        raise SystemExit(1)
else:
    raise SystemExit('FAIL: 等待连接结果超时。')
