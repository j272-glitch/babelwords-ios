#!/bin/bash

echo "🎬 Creating Simple D2D Privacy Ad..."

# Create output directory
mkdir -p output temp

# Video settings
WIDTH=1920
HEIGHT=1080
FPS=30
PURPLE_COLOR="0x5B4FE8"

echo "📊 Creating 30-second ad with D2D and privacy features"
echo ""

# Create D2D intro screen (10 seconds)
echo "📱 Creating D2D intro screen (10s)..."
ffmpeg -y -f lavfi -i "color=c=${PURPLE_COLOR}:size=${WIDTH}x${HEIGHT}:duration=10:rate=${FPS}" \
    -vf "drawtext=text='LinguaLink':fontsize=120:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-200:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Device-to-Device Translation':fontsize=55:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-100:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Real Conversations in Real Time':fontsize=40:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2-40:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='● ↔ ●':fontsize=80:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+50:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Speak • Translate • Listen':fontsize=45:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+150:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='36+ Languages Supported':fontsize=35:fontcolor=0xC0C0C0:x=(w-text_w)/2:y=(h-text_h)/2+220:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         fade=in:st=0:d=0.5,fade=out:st=9.5:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/intro.mp4

# Create privacy screen (10 seconds)
echo "🔒 Creating privacy screen (10s)..."
ffmpeg -y -f lavfi -i "color=c=${PURPLE_COLOR}:size=${WIDTH}x${HEIGHT}:duration=10:rate=${FPS}" \
    -vf "drawtext=text='Your Conversations Are Private':fontsize=55:fontcolor=white:x=(w-text_w)/2:y=120:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Even From Us':fontsize=55:fontcolor=0xFFD700:x=(w-text_w)/2:y=180:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='End-to-end encryption for message content':fontsize=30:fontcolor=white:x=(w-text_w)/2:y=280:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='We cannot read your translations':fontsize=30:fontcolor=white:x=(w-text_w)/2:y=320:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Conversations stay between participants':fontsize=30:fontcolor=white:x=(w-text_w)/2:y=360:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Free with ads • Premium ad-free':fontsize=35:fontcolor=0xC0C0C0:x=(w-text_w)/2:y=450:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         fade=in:st=0:d=0.5,fade=out:st=9.5:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/privacy.mp4

# Create finale screen (10 seconds)
echo "🚀 Creating finale screen (10s)..."
ffmpeg -y -f lavfi -i "color=c=${PURPLE_COLOR}:size=${WIDTH}x${HEIGHT}:duration=10:rate=${FPS}" \
    -vf "drawtext=text='LinguaLink':fontsize=100:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-180:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Break Language Barriers':fontsize=50:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-80:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Privately and Securely':fontsize=50:fontcolor=0xFFD700:x=(w-text_w)/2:y=(h-text_h)/2-30:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='● → ● → ●':fontsize=60:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+50:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Available on Google Play':fontsize=40:fontcolor=0x5B4FE8:x=(w-text_w)/2:y=(h-text_h)/2+150:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:box=1:boxcolor=white@0.9:boxborderw=12,
         drawtext=text='Device-to-Device Translation':fontsize=35:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+220:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         fade=in:st=0:d=0.5,fade=out:st=9.5:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/finale.mp4

echo "🔍 Verifying segments..."
for file in intro.mp4 privacy.mp4 finale.mp4; do
    if [ -f "temp/$file" ] && [ -s "temp/$file" ]; then
        echo "✅ temp/$file ($(ls -lh temp/$file | awk '{print $5}'))"
    else
        echo "❌ Problem with temp/$file"
        ls -la temp/
        exit 1
    fi
done

# Create final ad
echo "🔗 Combining all sections..."
echo "file 'intro.mp4'" > temp/filelist.txt
echo "file 'privacy.mp4'" >> temp/filelist.txt  
echo "file 'finale.mp4'" >> temp/filelist.txt

ffmpeg -y -f concat -safe 0 -i temp/filelist.txt -c copy output/lingualink-d2d-privacy-ad.mp4

# Clean up
rm -rf temp/

echo ""
echo "✅ LinguaLink D2D Privacy Ad complete!"
echo "📁 Output: output/lingualink-d2d-privacy-ad.mp4"
echo "🎯 Features:"
echo "   📱 Device-to-Device conversation mode"
echo "   🔒 Privacy messaging: 'Your Conversations Are Private, Even From Us'"
echo "   ✓ End-to-end encryption highlights"
echo "   💎 Free with ads / Premium ad-free model"
echo "   🎨 Consistent purple background"
echo "⏱️ Duration: 30 seconds"

# Final verification
if [ -f "output/lingualink-d2d-privacy-ad.mp4" ]; then
    echo ""
    echo "📊 File Information:"
    ls -lh output/lingualink-d2d-privacy-ad.mp4
    
    echo ""
    echo "🔍 Video Details:"
    ffprobe -v quiet -show_entries format=duration:stream=width,height,codec_name output/lingualink-d2d-privacy-ad.mp4
else
    echo "❌ Error: D2D Privacy ad was not created"
fi