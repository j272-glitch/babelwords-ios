#!/bin/bash

echo "🎬 Generating LinguaLink 90-Second Ad with FFmpeg..."

# Create output directory
mkdir -p output

# Video settings
WIDTH=1920
HEIGHT=1080
FPS=30
DURATION=90

echo "📊 Video Settings:"
echo "   Resolution: ${WIDTH}x${HEIGHT}"
echo "   Frame Rate: ${FPS} FPS"
echo "   Duration: ${DURATION} seconds"
echo ""

# Generate the complete 90-second ad
ffmpeg -y \
    -f lavfi -i "color=c=0x667eea:size=${WIDTH}x${HEIGHT}:duration=10:rate=${FPS}" \
    -f lavfi -i "color=c=0x2c3e50:size=${WIDTH}x${HEIGHT}:duration=60:rate=${FPS}" \
    -f lavfi -i "color=c=0xff6b6b:size=${WIDTH}x${HEIGHT}:duration=20:rate=${FPS}" \
    -filter_complex "
        [0:v]drawtext=text='LL':fontsize=120:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-100:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:box=1:boxcolor=0xFF6B6B@0.8:boxborderw=20,
             drawtext=text='LinguaLink':fontsize=80:fontcolor=0xFFD700:x=(w-text_w)/2:y=(h-text_h)/2+50:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
             drawtext=text='Break Language Barriers Instantly':fontsize=40:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+150:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
             fade=in:st=0:d=1:alpha=1,fade=out:st=9:d=1:alpha=1[intro];
        
        [1:v]drawtext=text='🎤 Voice Translation':fontsize=60:fontcolor=white:x=100:y=200:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
             drawtext=text='Speak naturally and get instant translations':fontsize=40:fontcolor=0xBDC3C7:x=100:y=280:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
             drawtext=text='⚡ Real-Time Results':fontsize=60:fontcolor=white:x=100:y=400:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
             drawtext=text='Lightning-fast translations with AI accuracy':fontsize=40:fontcolor=0xBDC3C7:x=100:y=480:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
             drawtext=text='🌍 Global Communication':fontsize=60:fontcolor=white:x=100:y=600:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
             drawtext=text='Connect with anyone in their native language':fontsize=40:fontcolor=0xBDC3C7:x=100:y=680:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
             drawtext=text='\"Hello, how are you?\" → \"Hola, ¿cómo estás?\"':fontsize=35:fontcolor=0x3498DB:x=1000:y=300:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=white@0.9:boxborderw=10,
             drawtext=text='\"Thank you\" → \"Merci beaucoup\"':fontsize=35:fontcolor=0x3498DB:x=1000:y=400:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=white@0.9:boxborderw=10,
             drawtext=text='\"Good morning\" → \"Guten Morgen\"':fontsize=35:fontcolor=0x3498DB:x=1000:y=500:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=white@0.9:boxborderw=10,
             fade=in:st=0:d=1:alpha=1,fade=out:st=59:d=1:alpha=1[features];
        
        [2:v]drawtext=text='Ready to Connect?':fontsize=90:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-150:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
             drawtext=text='Join millions breaking language barriers':fontsize=50:fontcolor=0xFFE8E8:x=(w-text_w)/2:y=(h-text_h)/2-50:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
             drawtext=text='Download on Google Play':fontsize=60:fontcolor=0x2c3e50:x=(w-text_w)/2:y=(h-text_h)/2+80:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:box=1:boxcolor=white@0.9:boxborderw=20,
             drawtext=text='📱 Free Download • Premium Features Available':fontsize=35:fontcolor=0xFFE8E8:x=(w-text_w)/2:y=(h-text_h)/2+200:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
             fade=in:st=0:d=1:alpha=1[cta];
        
        [intro][features][cta]concat=n=3:v=1:a=0[final]
    " \
    -map "[final]" \
    -c:v libx264 \
    -pix_fmt yuv420p \
    -preset medium \
    -crf 23 \
    -r ${FPS} \
    -t ${DURATION} \
    output/lingualink-ad.mp4

echo ""
echo "✅ Video generation complete!"
echo "📁 Output: output/lingualink-ad.mp4"
echo "⏱️ Duration: 90 seconds"
echo "🎯 Resolution: 1920x1080 (1080p)"
echo "📤 Ready for YouTube upload!"

# Show file info
if [ -f "output/lingualink-ad.mp4" ]; then
    echo ""
    echo "📊 File Information:"
    ls -lh output/lingualink-ad.mp4
    
    echo ""
    echo "🔍 Video Details:"
    ffprobe -v quiet -print_format json -show_format -show_streams output/lingualink-ad.mp4 | grep -E '"duration"|"width"|"height"|"codec_name"' | head -10
else
    echo "❌ Error: Video file was not created"
fi