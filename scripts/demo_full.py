"""Record the ledger walkthrough on the dedicated test emulator."""
from pathlib import Path
import argparse, base64, re, subprocess as sp, time, xml.etree.ElementTree as ET

p=argparse.ArgumentParser()
p.add_argument('--step', action='store_true')
p.add_argument('--chapter', choices=['ledger','ai','agent','data','settings','goal-query'], required=True)
args=p.parse_args()
base=[str(Path.home()/'AppData/Local/Android/Sdk/platform-tools/adb.exe'),'-s','emulator-5554']
out=Path('docs/demo-cn')
out.mkdir(parents=True, exist_ok=True)
def adb(*args):
    return sp.check_output(base+list(map(str,args)),timeout=25)
def ui():
    adb('shell','uiautomator','dump','/sdcard/wqt-demo.xml')
    return list(ET.fromstring(adb('shell','cat','/sdcard/wqt-demo.xml')).iter('node'))
def tap(x,y):
    adb('shell','input','tap',x,y)
    time.sleep(.35)
def click(label):
    for n in ui():
        if label in (n.get('text'),n.get('content-desc'),n.get('hint')):
            x,y,X,Y=map(int,re.findall(r'\d+',n.get('bounds')))
            tap((x+X)//2,(y+Y)//2)
            return
    raise RuntimeError('Missing control: '+label)
def key(k):
    adb('shell','input','keyevent',k)
    time.sleep(.35)
def enter(v):
    encoded=base64.b64encode(v.encode('utf-8')).decode('ascii')
    adb('shell','am','broadcast','-a','com.yazhang.demoime.INPUT','--es','msg',encoded)
    time.sleep(.4)
def clip(name,fn):
    if args.step:
        input(name+' Enter to continue')
    remote='/sdcard/wqt-'+name+'.mp4'
    proc=sp.Popen(base+['shell','screenrecord','--bit-rate','5000000','--time-limit','150',remote],stdout=sp.DEVNULL)
    try:
        time.sleep(.8)
        fn()
        time.sleep(3)
        (out/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
    finally:
        adb('shell','pkill','-INT','-f',remote)
        proc.wait(timeout=15)
        adb('pull',remote,out/(name+'.mp4'))
    print(name,flush=True)
def entry(kind,amount):
    click('记一笔')
    if kind!='支出':
        click('支出')
        click(kind)
    click('0.00')
    enter(amount)
    click('保存')
def goal():
    if any(n.get('text')=='存入一笔' for n in ui()):
        click('存入一笔')
        click('0.00')
        enter('500')
        click('保存')
        return
    click('新建目标')
    click('例如：旅行基金')
    enter('旅行基金')
    click('目标金额（元）')
    enter('5000')
    click('创建')
    click('存入一笔')
    click('0.00')
    enter('500')
    click('保存')
def stats():
    click('统计')
    click('全部')
    click('支出')
    click('全部分类')
    click('餐饮')
    time.sleep(2)
    click('本月')
    click('本周')
if args.chapter=='ledger':
    clip('01-home',lambda: click('记账'))
    clip('02-expense',lambda: entry('支出','25.50'))
    clip('03-income',lambda: entry('收入','8000'))
    clip('04-goal',goal)
    clip('05-stats',stats)
elif args.chapter=='ai':
    def probe():
        click('设置')
        click('测试文本连接')
        for _ in range(12):
            if any(n.get('text') in ('测试成功','连接测试失败') for n in ui()):
                return
        raise RuntimeError('No probe result')
    if args.chapter=='ai':
        clip('06-ai-probe',probe)
        key(4)
    def picked(x,name):
        click('记账')
        click('从账单图片记账（AI）')
        time.sleep(1)
        photos=[n for n in ui() if n.get('content-desc','').startswith('Photo taken on')]
        a,b,c,d=map(int,re.findall(r'\d+',photos[1 if x==540 else 0].get('bounds')))
        tap((a+c)//2,(b+d)//2)
        click('Done')
        def result():
            for _ in range(12):
                labels={n.get('text') for n in ui()}
                if labels & {'识别完成','确认识别结果','没有记账','无法识别图片'}:
                    print(labels & {'识别完成','确认识别结果','没有记账','无法识别图片'},flush=True)
                    return
            raise RuntimeError('No image recognition result')
        clip(name,result)
        labels={n.get('text') for n in ui()}
        if '确认识别结果' in labels and name=='07-image':
            return
        else:
            key(4)
    def confirm_bill():
        click('YYYY-MM-DD（请填写）')
        enter('2026-11-08')
        click('保存')
    picked(540,'07-image')
    if any(n.get('text')=='确认识别结果' for n in ui()):
        clip('07b-confirm',confirm_bill)
    picked(540,'08-duplicate')
    picked(175,'09-multiple')
if args.chapter in ('ai','agent','goal-query'):
    click('AI 代理')
    def question(prompt):
        for n in ui():
            if n.get('class')=='android.widget.EditText':
                x,y,X,Y=map(int,re.findall(r'\d+',n.get('bounds')))
                tap((x+X)//2,(y+Y)//2)
                break
        enter(prompt)
        click('发送')
        for _ in range(12):
            labels={n.get('text') for n in ui()}
            if 'AI 正在处理' not in labels:
                return
    if args.chapter!='goal-query':
        clip('10-agent-ask',lambda: question('帮我记一笔打车费'))
        clip('11-agent-record',lambda: question('今天交通打车花了20元'))
        clip('12-agent-stats',lambda: question('这个月餐饮花了多少钱'))
    clip('13-agent-goal',lambda: question('查看当前攒钱目标进度'))
elif args.chapter=='data':
    click('设置')
    def export(label):
        click(label)
        time.sleep(3)
        click('SAVE')
        time.sleep(2)
    clip('14-json',lambda: export('导出 JSON 备份'))
    clip('15-csv',lambda: export('导出 CSV 表格'))
    def restore():
        click('从备份恢复')
        for n in ui():
            label=n.get('text','')
            if label.startswith('yazhang-') and label.endswith('.json'):
                click(label)
                break
        else:
            raise RuntimeError('Exported backup not visible in the picker')
        click('确认导入')
    clip('16-restore',restore)








if args.chapter=='settings':
    click('设置')
    def model_list():
        click('配置 AI 服务')
        click('自动探测可用模型列表')
        for _ in range(12):
            if any(n.get('text') in ('选择可用模型','探测模型失败') for n in ui()):
                return
    clip('06a-models',model_list)
    key(4)
    key(4)
    def vision():
        click('测试视觉能力（识别图片）')
        for _ in range(12):
            if any(n.get('text') in ('测试成功','连接测试失败') for n in ui()):
                return
    clip('06c-vision',vision)








