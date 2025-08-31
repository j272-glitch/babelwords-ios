#!/bin/bash

echo "🎬 Creating Updated LinguaLink Ad with GTLingua Branding..."

# Create output directory
mkdir -p output

# Video settings
WIDTH=1920
HEIGHT=1080
FPS=30

echo "📊 Video Settings:"
echo "   Resolution: ${WIDTH}x${HEIGHT}"
echo "   Frame Rate: ${FPS} FPS"
echo "   Duration: 90 seconds"
echo "   Brand: GTLingua purple styling"
echo ""

# Create the complete 90-second ad as one file
ffmpeg -y -f lavfi -i "color=c=0x6C63FF:size=${WIDTH}x${HEIGHT}:duration=90:rate=${FPS}" \
    -vf "
    drawtext=text='🌐':fontsize=180:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-180:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:enable='between(t,0,10)',
    drawtext=text='LinguaLink':fontsize=85:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-20:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:enable='between(t,0,10)',
    drawtext=text='Real-time speech translation':fontsize=42:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+50:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:enable='between(t,0,10)',
    drawtext=text='for seamless communication':fontsize=42:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+110:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:enable='between(t,0,10)',
    
    drawtext=text='🎤 Voice Translation':fontsize=60:fontcolor=white:x=100:y=150:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:enable='between(t,10,70)',
    drawtext=text='Speak naturally and get instant translations':fontsize=36:fontcolor=0xE0E0E0:x=100:y=220:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:enable='between(t,10,70)',
    drawtext=text='⚡ Real-Time Processing':fontsize=60:fontcolor=white:x=100:y=350:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:enable='between(t,10,70)',
    drawtext=text='Lightning-fast AI with advanced algorithms':fontsize=36:fontcolor=0xE0E0E0:x=100:y=420:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:enable='between(t,10,70)',
    drawtext=text='🌍 Global Communication':fontsize=60:fontcolor=white:x=100:y=550:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:enable='between(t,10,70)',
    drawtext=text='Connect across cultures seamlessly':fontsize=36:fontcolor=0xE0E0E0:x=100:y=620:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:enable='between(t,10,70)',
    
    drawtext=text='Hello how are you':fontsize=32:fontcolor=white:x=1000:y=200:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=10:enable='between(t,10,70)',
    drawtext=text='Hola como estas':fontsize=32:fontcolor=white:x=1000:y=250:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=10:enable='between(t,10,70)',
    drawtext=text='Bonjour comment allez-vous':fontsize=28:fontcolor=white:x=1000:y=300:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=10:enable='between(t,10,70)',
    drawtext=text='Guten Tag wie geht es Ihnen':fontsize=28:fontcolor=white:x=1000:y=350:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=10:enable='between(t,10,70)',
    drawtext=text='36 Languages Real-time Accurate':fontsize=30:fontcolor=white:x=1000:y=450:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:enable='between(t,10,70)',
    
    drawtext=text='Ready to Connect Globally?':fontsize=70:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-180:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:enable='between(t,70,90)',
    drawtext=text='Join thousands using LinguaLink':fontsize=45:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2-80:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:enable='between(t,70,90)',
    drawtext=text='for seamless communication':fontsize=45:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2-30:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:enable='between(t,70,90)',
    drawtext=text='Download on Google Play':fontsize=55:fontcolor=0x6C63FF:x=(w-text_w)/2:y=(h-text_h)/2+60:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:box=1:boxcolor=white@0.95:boxborderw=20:enable='between(t,70,90)',
    drawtext=text='gtlingua@pm.me':fontsize=32:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2+170:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:enable='between(t,70,90)',
    drawtext=text='https://gtlingua.com':fontsize=32:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2+210:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:enable='between(t,70,90)'
    " \
    -c:v libx264 -pix_fmt yuv420p -preset medium -crf 23 -r ${FPS} -t 90 \
    output/lingualink-ad-updated.mp4

echo ""
echo "✅ Updated GTLingua ad generation complete!"
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
    ffprobe -v quiet -show_entries format=duration:stream=width,height,codec_name output/lingualink-ad-updated.mp4
else
    echo "❌ Error: Updated video file was not created"
fi