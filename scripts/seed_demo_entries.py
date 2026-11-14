"""Add a small set of sample entries using the running emulator UI."""
import base64
from datetime import date, timedelta
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

adb = str(Path.home() / 'AppData/Local/Android/Sdk/platform-tools/adb.exe')
base = [adb, '-s', 'emulator-5554']

def run(*args):
    return subprocess.run(base + list(map(str, args)), check=True, capture_output=True).stdout

def nodes():
    run('shell', 'uiautomator', 'dump', '/sdcard/seed-window.xml')
    return list(ET.fromstring(run('shell', 'cat', '/sdcard/seed-window.xml')).iter('node'))

def tap_node(node):
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
    run('shell', 'input', 'tap', (x1+x2)//2, (y1+y2)//2)
    time.sleep(.5)

def click(label):
    for node in nodes():
        if label in (node.get('text'), node.get('content-desc'), node.get('hint')):
            tap_node(node)
            return
    raise RuntimeError('找不到控件：' + label)

def enter(value, replace=False):
    if replace:
        run('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END')
        run('shell', 'input', 'keyevent', '--longpress', 'KEYCODE_DEL')
        for _ in range(16):
            run('shell', 'input', 'keyevent', 'KEYCODE_DEL')
    encoded = base64.b64encode(value.encode()).decode()
    run('shell', 'am', 'broadcast', '-a', 'com.yazhang.demoime.INPUT', '--es', 'msg', encoded)
    time.sleep(.3)

click('记账')
today = date.today()
entries = [
    ('支出', '25.50', '餐饮', '午餐', 1),
    ('支出', '18.00', '交通', '地铁出行', 2),
    ('支出', '68.90', '购物', '日用品', 4),
    ('支出', '32.00', '餐饮', '晚餐', 6),
    ('支出', '45.00', '娱乐', '电影', 9),
    ('收入', '6500.00', '工资', '月度工资', 11),
]
for kind, amount, category, title, days in entries:
    click('记一笔')
    if kind == '收入':
        click('支出')
        click('收入')
    click('0.00')
    enter(amount)
    if category not in ('餐饮', '工资'):
        click('餐饮')
        click(category)
    click('简短备注（可选）')
    enter(title)
    target = today - timedelta(days=days)
    click(today.isoformat())
    enter(target.isoformat(), replace=True)
    click('保存')
    time.sleep(1)
    if any(n.get('text') == '记一笔' for n in nodes()):
        raise RuntimeError('账目未保存：' + title)
    print('已添加', title, target, flush=True)
click('统计')
