#!/usr/bin/env python3
"""Burn the demo's Chinese subtitles into the picture (requires imageio-ffmpeg)."""
from pathlib import Path
import subprocess
import imageio_ffmpeg

directory = Path(__file__).resolve().parents[1] / 'docs' / 'demo'
style = ('fps=12,subtitles=demo.srt:force_style='
         "'FontName=Microsoft YaHei,FontSize=10,PrimaryColour=&H00FFFFFF,"
         "OutlineColour=&H90000000,BorderStyle=3,Outline=1,MarginV=20'")
subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), '-y', '-i', 'demo.mp4', '-vf', style,
                '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '22', '-an',
                'demo-captioned.mp4'], cwd=directory, check=True)
