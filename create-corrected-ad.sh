#!/bin/bash

echo "🎬 Creating Corrected LinguaLink Ad..."

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
echo "   Brand: LinguaLink only (contact: gtlingua)"
echo ""

# Create intro section (10 seconds)
echo "🎨 Creating intro section (10s)..."
ffmpeg -y -f lavfi -i "color=c=0x6C63FF:size=${WIDTH}x${HEIGHT}:duration=10:rate=${FPS}" \
    -vf "drawtext=text='LinguaLink':fontsize=120:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-100:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Real-time speech translation':fontsize=50:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='for seamless communication':fontsize=50:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+80:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         fade=in:st=0:d=1,fade=out:st=9:d=1" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/intro.mp4

# Create features section (60 seconds)
echo "🎯 Creating features section (60s)..."
ffmpeg -y -f lavfi -i "color=c=0x5B4FE8:size=${WIDTH}x${HEIGHT}:duration=60:rate=${FPS}" \
    -vf "drawtext=text='Voice Translation':fontsize=70:fontcolor=white:x=100:y=150:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Speak naturally and get instant translations':fontsize=40:fontcolor=0xE0E0E0:x=100:y=220:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Real-Time Processing':fontsize=70:fontcolor=white:x=100:y=350:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Lightning-fast AI with advanced algorithms':fontsize=40:fontcolor=0xE0E0E0:x=100:y=420:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Global Communication':fontsize=70:fontcolor=white:x=100:y=550:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Connect across cultures seamlessly':fontsize=40:fontcolor=0xE0E0E0:x=100:y=620:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Hello how are you':fontsize=35:fontcolor=white:x=1000:y=200:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=10,
         drawtext=text='Hola como estas':fontsize=35:fontcolor=white:x=1000:y=260:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=10,
         drawtext=text='Bonjour comment allez-vous':fontsize=30:fontcolor=white:x=1000:y=320:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=10,
         drawtext=text='36 Languages Real-time Accurate':fontsize=32:fontcolor=white:x=1000:y=450:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         fade=in:st=0:d=1,fade=out:st=59:d=1" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/features.mp4

# Create CTA section (20 seconds) 
echo "📱 Creating call-to-action section (20s)..."
ffmpeg -y -f lavfi -i "color=c=0x4B3FD8:size=${WIDTH}x${HEIGHT}:duration=20:rate=${FPS}" \
    -vf "drawtext=text='Ready to Connect Globally?':fontsize=80:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-200:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Download LinguaLink Today':fontsize=50:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2-100:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Join thousands in seamless communication':fontsize=45:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2-40:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Download on Google Play':fontsize=60:fontcolor=0x6C63FF:x=(w-text_w)/2:y=(h-text_h)/2+60:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:box=1:boxcolor=white@0.95:boxborderw=20,
         drawtext=text='gtlingua@pm.me':fontsize=35:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2+180:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='https://gtlingua.com':fontsize=35:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2+230:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         fade=in:st=0:d=1" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/cta.mp4

# Check that all segments were created
if [ ! -f "temp/intro.mp4" ] || [ ! -f "temp/features.mp4" ] || [ ! -f "temp/cta.mp4" ]; then
    echo "❌ Error: Not all video segments were created"
    ls -la temp/
    exit 1
fi

# Concatenate all sections into final video
echo "🔗 Combining sections into final video..."
echo "file 'intro.mp4'" > temp/filelist.txt
echo "file 'features.mp4'" >> temp/filelist.txt
echo "file 'cta.mp4'" >> temp/filelist.txt

ffmpeg -y -f concat -safe 0 -i temp/filelist.txt -c copy output/lingualink-ad-final.mp4

# Clean up temporary files
rm -rf temp/

echo ""
echo "✅ Final LinguaLink ad generation complete!"
echo "📁 Output: output/lingualink-ad-final.mp4"
echo "⏱️ Duration: 90 seconds"
echo "🎯 Resolution: 1920x1080 (1080p)"
echo "🎨 Brand: LinguaLink throughout (GTLingua only in contact)"
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