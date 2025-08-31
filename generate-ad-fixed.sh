#!/bin/bash

echo "🎬 Generating Updated LinguaLink Ad with GTLingua Branding..."

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
echo "   Brand: GTLingua purple styling"
echo ""

# Generate the complete 90-second ad with updated branding
ffmpeg -y \
    -f lavfi -i "color=c=0x6C63FF:size=${WIDTH}x${HEIGHT}:duration=10:rate=${FPS}" \
    -f lavfi -i "color=c=0x5B4FE8:size=${WIDTH}x${HEIGHT}:duration=60:rate=${FPS}" \
    -f lavfi -i "color=c=0x4B3FD8:size=${WIDTH}x${HEIGHT}:duration=20:rate=${FPS}" \
    -filter_complex "
        [0:v]drawtext=text='🌐':fontsize=180:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-180:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
             drawtext=text='LinguaLink':fontsize=85:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-30:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
             drawtext=text='Real-time speech translation':fontsize=42:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+50:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
             drawtext=text='for seamless communication':fontsize=42:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+110:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
             drawtext=text='Supporting 36 languages with advanced recognition':fontsize=28:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2+180:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
             fade=in:st=0:d=1:alpha=1,fade=out:st=9:d=1:alpha=1[intro];
        
        [1:v]drawtext=text='🎤 Voice Translation':fontsize=60:fontcolor=white:x=100:y=150:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
             drawtext=text='Speak naturally and get instant translations':fontsize=36:fontcolor=0xE0E0E0:x=100:y=210:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
             drawtext=text='⚡ Real-Time Processing':fontsize=60:fontcolor=white:x=100:y=320:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
             drawtext=text='Lightning-fast AI powered by advanced algorithms':fontsize=36:fontcolor=0xE0E0E0:x=100:y=380:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
             drawtext=text='🌍 Global Communication':fontsize=60:fontcolor=white:x=100:y=490:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
             drawtext=text='Connect across cultures with seamless translation':fontsize=36:fontcolor=0xE0E0E0:x=100:y=550:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
             drawtext=text='🗣️ Natural Speech':fontsize=60:fontcolor=white:x=100:y=660:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
             drawtext=text='Advanced AI understands context and nuance':fontsize=36:fontcolor=0xE0E0E0:x=100:y=720:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
             drawtext=text='Hello how are you':fontsize=35:fontcolor=white:x=1000:y=200:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=12,
             drawtext=text='Hola como estas':fontsize=35:fontcolor=white:x=1000:y=260:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=12,
             drawtext=text='Bonjour comment allez-vous':fontsize=30:fontcolor=white:x=1000:y=320:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=12,
             drawtext=text='Guten Tag wie geht es Ihnen':fontsize=30:fontcolor=white:x=1000:y=380:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=12,
             drawtext=text='Konnichiwa genki desu ka':fontsize=30:fontcolor=white:x=1000:y=440:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=12,
             drawtext=text='36 Languages • Real-time • Accurate':fontsize=32:fontcolor=0xFFFFFF:x=1000:y=520:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
             fade=in:st=0:d=1:alpha=1,fade=out:st=59:d=1:alpha=1[features];
        
        [2:v]drawtext=text='Ready to Connect Globally?':fontsize=70:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-180:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
             drawtext=text='Join thousands using LinguaLink':fontsize=45:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2-80:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
             drawtext=text='for seamless communication':fontsize=45:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2-30:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
             drawtext=text='Download on Google Play':fontsize=55:fontcolor=0x6C63FF:x=(w-text_w)/2:y=(h-text_h)/2+60:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:box=1:boxcolor=white@0.95:boxborderw=20,
             drawtext=text='gtlingua@pm.me • https://gtlingua.com':fontsize=30:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2+170:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
             drawtext=text='Free Download • Premium Features • Enterprise Solutions':fontsize=26:fontcolor=0xD0D0D0:x=(w-text_w)/2:y=(h-text_h)/2+220:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
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
    output/lingualink-ad-updated.mp4

echo ""
echo "✅ Updated GTLingua video generation complete!"
echo "📁 Output: output/lingualink-ad-updated.mp4"
echo "⏱️ Duration: 90 seconds"
echo "🎯 Resolution: 1920x1080 (1080p)"
echo "🎨 Brand: GTLingua purple styling"
echo "📧 Contact: gtlingua@pm.me"
echo "🌐 Website: https://gtlingua.com"
echo "📤 Ready for YouTube upload!"

# Show file info
if [ -f "output/lingualink-ad-updated.mp4" ]; then
    echo ""
    echo "📊 File Information:"
    ls -lh output/lingualink-ad-updated.mp4
    
    echo ""
    echo "🔍 Video Details:"
    ffprobe -v quiet -print_format json -show_format -show_streams output/lingualink-ad-updated.mp4 | grep -E '"duration"|"width"|"height"|"codec_name"' | head -10
else
    echo "❌ Error: Updated video file was not created"
fi