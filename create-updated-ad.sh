#!/bin/bash

echo "🎬 Creating Updated LinguaLink Ad with GTLingua Branding..."

# Create output directory
mkdir -p output temp

# Video settings
WIDTH=1920
HEIGHT=1080
FPS=30

echo "📊 Video Settings:"
echo "   Resolution: ${WIDTH}x${HEIGHT}"
echo "   Frame Rate: ${FPS} FPS"
echo "   Duration: 90 seconds"
echo "   Brand: GTLingua purple styling (#6C63FF)"
echo ""

# Create intro section (10 seconds) - Purple background #6C63FF
echo "🎨 Creating intro section..."
ffmpeg -y -f lavfi -i "color=c=0x6C63FF:size=${WIDTH}x${HEIGHT}:duration=10:rate=${FPS}" \
    -vf "drawtext=text='🌐':fontsize=160:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-160:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='LinguaLink':fontsize=80:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-20:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Real-time speech translation':fontsize=40:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+60:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='for seamless communication':fontsize=40:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+110:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Supporting 36 languages with advanced recognition':fontsize=24:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2+170:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         fade=in:st=0:d=1,fade=out:st=9:d=1" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/intro.mp4

# Create features section (60 seconds) - Darker purple #5B4FE8
echo "🎯 Creating features section..."
ffmpeg -y -f lavfi -i "color=c=0x5B4FE8:size=${WIDTH}x${HEIGHT}:duration=60:rate=${FPS}" \
    -vf "drawtext=text='🎤 Voice Translation':fontsize=55:fontcolor=white:x=80:y=120:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Speak naturally and get instant translations':fontsize=32:fontcolor=0xE0E0E0:x=80:y=180:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='⚡ Real-Time Processing':fontsize=55:fontcolor=white:x=80:y=280:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Lightning-fast AI with advanced algorithms':fontsize=32:fontcolor=0xE0E0E0:x=80:y=340:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='🌍 Global Communication':fontsize=55:fontcolor=white:x=80:y=440:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Connect across cultures seamlessly':fontsize=32:fontcolor=0xE0E0E0:x=80:y=500:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='🗣️ Natural Speech Recognition':fontsize=55:fontcolor=white:x=80:y=600:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='AI understands context and nuance':fontsize=32:fontcolor=0xE0E0E0:x=80:y=660:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Hello how are you':fontsize=32:fontcolor=white:x=950:y=180:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=10,
         drawtext=text='Hola como estas':fontsize=32:fontcolor=white:x=950:y=230:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=10,
         drawtext=text='Bonjour comment allez-vous':fontsize=28:fontcolor=white:x=950:y=280:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=10,
         drawtext=text='Guten Tag wie geht es Ihnen':fontsize=28:fontcolor=white:x=950:y=330:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=10,
         drawtext=text='Konnichiwa genki desu ka':fontsize=28:fontcolor=white:x=950:y=380:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=10,
         drawtext=text='36 Languages • Real-time • Accurate':fontsize=30:fontcolor=white:x=950:y=450:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         fade=in:st=0:d=1,fade=out:st=59:d=1" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/features.mp4

# Create CTA section (20 seconds) - Deep purple #4B3FD8  
echo "📱 Creating call-to-action section..."
ffmpeg -y -f lavfi -i "color=c=0x4B3FD8:size=${WIDTH}x${HEIGHT}:duration=20:rate=${FPS}" \
    -vf "drawtext=text='Ready to Connect Globally?':fontsize=65:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-160:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Join thousands using LinguaLink':fontsize=42:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2-70:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='for seamless communication':fontsize=42:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2-20:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Download on Google Play':fontsize=50:fontcolor=0x6C63FF:x=(w-text_w)/2:y=(h-text_h)/2+60:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:box=1:boxcolor=white@0.95:boxborderw=18,
         drawtext=text='gtlingua@pm.me • https://gtlingua.com':fontsize=28:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2+150:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Free Download • Premium Features • Enterprise Solutions':fontsize=24:fontcolor=0xD0D0D0:x=(w-text_w)/2:y=(h-text_h)/2+190:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         fade=in:st=0:d=1" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/cta.mp4

# Concatenate all sections into final video
echo "🔗 Combining sections into final video..."
echo "file 'intro.mp4'" > temp/filelist.txt
echo "file 'features.mp4'" >> temp/filelist.txt
echo "file 'cta.mp4'" >> temp/filelist.txt

ffmpeg -y -f concat -safe 0 -i temp/filelist.txt -c copy output/lingualink-ad-updated.mp4

# Clean up temporary files
rm -rf temp/

echo ""
echo "✅ Updated GTLingua ad generation complete!"
echo "📁 Output: output/lingualink-ad-updated.mp4"
echo "⏱️ Duration: 90 seconds"
echo "🎯 Resolution: 1920x1080 (1080p)"
echo "🎨 Brand: GTLingua purple gradient styling"
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