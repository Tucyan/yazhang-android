#!/usr/bin/env python3
"""Record a non-destructive walkthrough of the installed debug application.

Requires an already running ADB device. Existing ledger data is displayed as-is;
no account, payment, AI request, or database reset is performed.
"""
import argparse
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'com.example.yazhang'
REMOTE = '/sdcard/yazhang-demo-recording.mp4'

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--adb', default=os.environ.get('ADB', str(Path.home() / 'AppData/Local/Android/Sdk/platform-tools/adb.exe')))
    parser.add_argument('--serial', default='emulator-5554')
    parser.add_argument('--out', default=str(ROOT / 'docs' / 'demo'))
    args = parser.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    base = [args.adb, '-s', args.serial]
    def adb(*a, **kw):
        return subprocess.run(base + list(a), check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, **kw).stdout
    def tree():
        adb('shell', 'uiautomator', 'dump', '/sdcard/yazhang-demo-window.xml')
        root = ET.fromstring(adb('shell', 'cat', '/sdcard/yazhang-demo-window.xml'))
        if not any(n.get('package') == PACKAGE for n in root.iter('node')):
            raise RuntimeError('芽账未显示在前台，已停止录制。')
        return root
    def click(label):
        for n in tree().iter('node'):
            if n.get('text') == label or n.get('content-desc') == label:
                b = list(map(int, re.findall(r'\d+', n.get('bounds', ''))))
                if len(b) == 4:
                    adb('shell', 'input', 'tap', str((b[0]+b[2])//2), str((b[1]+b[3])//2))
                    time.sleep(0.8)
                    return
        raise RuntimeError('找不到界面控件：' + label)
    def screenshot(name):
        tree()
        (out / (name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p'))
    adb('wait-for-device')
    adb('shell', 'am', 'start', '-n', PACKAGE + '/.MainActivity')
    time.sleep(1.5)
    tree()
    record = subprocess.Popen(base + ['shell', 'screenrecord', '--bit-rate', '4000000', '--time-limit', '150', REMOTE], stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    start = time.monotonic()
    captions = []
    def scene(name, caption):
        t = time.monotonic() - start
        screenshot(name)
        time.sleep(2)
        captions.append((t, time.monotonic() - start, caption))
        print(name + ': ' + caption, flush=True)
    try:
        time.sleep(1)
        scene('01_home', '首页：查看今日支出、攒钱目标，并从这里开始记账')
        click('记一笔')
        scene('02_entry', '手动记账：选择类型，填写金额、分类和日期')
        adb('shell', 'input', 'keyevent', '4')
        time.sleep(.5)
        click('新建目标')
        scene('03_goal', '攒钱目标：设置目标名称、金额和日期')
        adb('shell', 'input', 'keyevent', '4')
        time.sleep(.5)
        click('统计')
        scene('04_stats', '统计：按周期、类型和分类筛选本地账目')
        click('AI 代理')
        scene('05_agent', 'AI 代理：使用自然语言记账和查询统计')
        click('设置')
        scene('06_settings', '设置：配置 AI 服务、管理本地数据和导出')
    finally:
        subprocess.run(base + ['shell', 'pkill', '-INT', '-f', REMOTE], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            record.wait(timeout=15)
        except subprocess.TimeoutExpired:
            record.terminate()
            record.wait(timeout=5)
        subprocess.run(base + ['pull', REMOTE, str(out / 'demo.mp4')], check=False, stdout=subprocess.DEVNULL)
    def stamp(t):
        ms = round(t * 1000)
        return f'{ms//3600000:02}:{ms//60000%60:02}:{ms//1000%60:02},{ms%1000:03}'
    (out / 'demo.srt').write_text('\n\n'.join(f'{i}\n{stamp(a)} --> {stamp(b)}\n{c}' for i, (a, b, c) in enumerate(captions, 1)) + '\n', encoding='utf-8')
    print('演示素材：', out)

if __name__ == '__main__':
    main()
