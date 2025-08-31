#!/bin/bash

echo "🎬 Creating Final LinguaLink Ad with Corrected Branding..."

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
echo "   Brand: LinguaLink with purple styling"
echo ""

# Create the complete 90-second ad with LinguaLink branding only
ffmpeg -y -f lavfi -i "color=c=0x6C63FF:size=${WIDTH}x${HEIGHT}:duration=90:rate=${FPS}" \
    -vf "drawtext=text='LinguaLink':fontsize=120:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-100:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:enable='between(t,0,10)',
         drawtext=text='Real-time speech translation':fontsize=50:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:enable='between(t,0,10)',
         drawtext=text='for seamless communication':fontsize=50:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+80:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:enable='between(t,0,10)',
         
         drawtext=text='Voice Translation':fontsize=70:fontcolor=white:x=100:y=150:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:enable='between(t,10,70)',
         drawtext=text='Speak naturally and get instant translations':fontsize=40:fontcolor=0xE0E0E0:x=100:y=220:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:enable='between(t,10,70)',
         drawtext=text='Real-Time Processing':fontsize=70:fontcolor=white:x=100:y=350:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:enable='between(t,10,70)',
         drawtext=text='Lightning-fast AI with advanced algorithms':fontsize=40:fontcolor=0xE0E0E0:x=100:y=420:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:enable='between(t,10,70)',
         drawtext=text='Global Communication':fontsize=70:fontcolor=white:x=100:y=550:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:enable='between(t,10,70)',
         drawtext=text='Connect across cultures seamlessly':fontsize=40:fontcolor=0xE0E0E0:x=100:y=620:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:enable='between(t,10,70)',
         
         drawtext=text='Hello how are you':fontsize=35:fontcolor=white:x=1000:y=200:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=10:enable='between(t,10,70)',
         drawtext=text='Hola como estas':fontsize=35:fontcolor=white:x=1000:y=260:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=10:enable='between(t,10,70)',
         drawtext=text='Bonjour comment allez-vous':fontsize=30:fontcolor=white:x=1000:y=320:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=10:enable='between(t,10,70)',
         drawtext=text='36 Languages Real-time Accurate':fontsize=32:fontcolor=white:x=1000:y=450:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:enable='between(t,10,70)',
         
         drawtext=text='Ready to Connect Globally?':fontsize=80:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-200:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:enable='between(t,70,90)',
         drawtext=text='Download LinguaLink Today':fontsize=50:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2-100:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:enable='between(t,70,90)',
         drawtext=text='Join thousands in seamless communication':fontsize=45:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2-40:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:enable='between(t,70,90)',
         drawtext=text='Download on Google Play':fontsize=60:fontcolor=0x6C63FF:x=(w-text_w)/2:y=(h-text_h)/2+60:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:box=1:boxcolor=white@0.95:boxborderw=20:enable='between(t,70,90)',
         drawtext=text='gtlingua@pm.me':fontsize=35:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2+180:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:enable='between(t,70,90)',
         drawtext=text='https://gtlingua.com':fontsize=35:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2+230:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:enable='between(t,70,90)'" \
    -c:v libx264 -pix_fmt yuv420p -preset medium -crf 23 -r ${FPS} -t 90 \
    output/lingualink-ad-final.mp4

echo ""
echo "✅ Final LinguaLink ad generation complete!"
echo "📁 Output: output/lingualink-ad-final.mp4"
echo "⏱️ Duration: 90 seconds"
echo "🎯 Resolution: 1920x1080 (1080p)"
echo "🎨 Brand: LinguaLink only (contact info shows gtlingua)"
echo "📧 Contact: gtlingua@pm.me"
echo "🌐 Website: https://gtlingua.com"
echo "📤 Ready for YouTube upload!"

# Show file info
if [ -f "output/lingualink-ad-final.mp4" ]; then
    echo ""
    echo "📊 File Information:"
    ls -lh output/lingualink-ad-final.mp4
    
    echo ""
    echo "🔍 Video Details:"
    ffprobe -v quiet -show_entries format=duration:stream=width,height,codec_name output/lingualink-ad-final.mp4
else
    echo "❌ Error: Final video file was not created"
fi