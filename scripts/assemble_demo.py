"""Assemble the recorded walkthrough with readable, burned-in subtitles."""
import subprocess as sp
from pathlib import Path
import re
import imageio_ffmpeg

d=Path('docs/demo-cn')
ff=imageio_ffmpeg.get_ffmpeg_exe()
captions={
 '01-home':'首页显示支出概览、攒钱目标和最近账目',
 '02-expense':'手动录入 25.50 元餐饮支出，保存后核对首页',
 '03-income':'切换为收入，录入 8,000 元工资',
 '04-goal':'向现有攒钱目标存入 500 元并查看进度',
 '05-stats':'按周期、账目类型和分类筛选统计',
 '06-ai-probe':'实际请求 TokenDance，查看文本连接结果',
 '06a-models':'打开 AI 服务配置，探测当前服务商可用模型',
 '06c-vision':'发送测试色块，查看视觉能力探测结果',
 '06b-single-input':'单笔账单的脱敏副本，保留金额并遮盖交易编号等信息',
 '07-image':'发送脱敏后的单笔账单，查看实际识别反馈',
 '07b-confirm':'核对 18.80 元餐饮支出，补填日期后保存',
 '08-duplicate':'再次识别同一图片，实际反馈显示已记账',
 '08b-multiple-input':'多笔订单的脱敏副本，保留不同订单的金额',
 '09-multiple':'选择多笔订单截图，检查是否需要人工确认',
 '10-agent-ask':'AI 代理发现缺少金额和日期，先追问',
 '11-agent-record':'补充信息后，AI 代理记录 20 元交通支出',
 '12-agent-stats':'AI 代理查询本地账目，返回餐饮汇总和明细',
 '13-agent-goal':'询问攒钱目标，查看实际进度',
 '14-json':'导出 JSON 备份，供后续恢复',
 '15-csv':'导出 CSV，供表格软件查看',
 '16-restore':'选择备份文件，查看恢复预览和重复跳过数量',
}
def run(*args):
    sp.run([ff,'-hide_banner','-loglevel','error','-y',*map(str,args)],check=True)
def duration(path):
    r=sp.run([ff,'-i',str(path)],capture_output=True,text=True)
    h,m,s=re.search(r'Duration: (\d+):(\d+):([\d.]+)',r.stderr).groups()
    return int(h)*3600+int(m)*60+float(s)
def stamp(x):
    ms=round(x*1000)
    return f'{ms//3600000:02}:{ms//60000%60:02}:{ms//1000%60:02},{ms%1000:03}'
for name, asset in [('06b-single-input','single'),('08b-multiple-input','multiple')]:
    run('-loop','1','-i',d/'assets'/(asset+'.png'),'-vf',
        'scale=1080:2424:force_original_aspect_ratio=decrease,pad=1080:2424:(ow-iw)/2:(oh-ih)/2',
        '-r','15','-t','6','-an','-pix_fmt','yuv420p','-c:v','libx264','-preset','veryfast',d/(name+'.mp4'))
clips=[]
srt=[]
start=0
for name, caption in captions.items():
    raw=d/(name+'.mp4')
    if not raw.exists():
        continue
    target=d/('cut-'+name+'.mp4')
    run('-i',raw,'-vf','setpts=PTS/1.42,fps=15,scale=720:-2,tpad=stop_mode=clone:stop_duration=2',
        '-an','-c:v','libx264','-preset','veryfast','-crf','23',target)
    clips.append(target)
    end=start+duration(target)
    srt.append(f'{len(clips)}\n{stamp(start)} --> {stamp(end)}\n{caption}\n')
    start=end
(d/'chapters.txt').write_text(''.join(f"file '{p.name}'\n" for p in clips),encoding='utf-8')
(d/'完整演示.srt').write_text('\n'.join(srt),encoding='utf-8')
sp.run([ff,'-hide_banner','-loglevel','error','-y','-f','concat','-safe','0','-i','chapters.txt',
        '-vf',"subtitles=完整演示.srt:force_style='FontName=Microsoft YaHei,FontSize=9,BorderStyle=3,Outline=1,MarginV=15'",
        '-c:v','libx264','-preset','veryfast','-crf','22','-movflags','+faststart','芽账功能演示.mp4'],cwd=d,check=True)
print(f'{len(clips)} clips, {start:.1f} seconds')




