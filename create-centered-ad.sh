#!/bin/bash

echo "🎬 Creating Centered 3-Slide LinguaLink Ad..."

# Create output directory
mkdir -p output temp

# Video settings
WIDTH=1920
HEIGHT=1080
FPS=30
PURPLE_COLOR="0x5B4FE8"

echo "📊 Centering text vertically with better spacing:"
echo "   🗣️ Slide 2: Centered voice demo"
echo "   🔒🖼️ Slide 3: Centered combined slide with more line spacing"
echo ""

# Keep intro the same (10 seconds)
echo "📱 Creating intro slide (10s)..."
ffmpeg -y -f lavfi -i "color=c=${PURPLE_COLOR}:size=${WIDTH}x${HEIGHT}:duration=10:rate=${FPS}" \
    -vf "drawtext=text='LinguaLink':fontsize=120:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-150:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Real-time Speech Translation':fontsize=50:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-50:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='● → ♪':fontsize=80:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+50:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Supporting 36+ Languages':fontsize=35:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2+150:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         fade=in:st=0:d=0.5,fade=out:st=9.5:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/intro.mp4

# Updated voice demo - centered vertically (10 seconds)
echo "🗣️ Creating centered voice demo slide (10s)..."
ffmpeg -y -f lavfi -i "color=c=${PURPLE_COLOR}:size=${WIDTH}x${HEIGHT}:duration=10:rate=${FPS}" \
    -vf "drawtext=text='Voice-to-Voice Translation':fontsize=60:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-200:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Speak in one language':fontsize=45:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-100:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='hear voice spoken in another':fontsize=45:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-50:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Hello how are you?':fontsize=35:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+30:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='↓':fontsize=60:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+80:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Como estas?':fontsize=35:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+150:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         fade=in:st=0:d=0.5,fade=out:st=9.5:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/demo.mp4

# Updated combined slide - centered with more spacing (10 seconds)
echo "🔒🖼️ Creating centered combined slide with better spacing (10s)..."
ffmpeg -y -f lavfi -i "color=c=${PURPLE_COLOR}:size=${WIDTH}x${HEIGHT}:duration=10:rate=${FPS}" \
    -vf "drawtext=text='LinguaLink':fontsize=90:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-250:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Real time device to device conversation':fontsize=38:fontcolor=0xFFD700:x=(w-text_w)/2:y=(h-text_h)/2-150:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='using translated speech':fontsize=38:fontcolor=0xFFD700:x=(w-text_w)/2:y=(h-text_h)/2-100:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         
         drawtext=text='End-to-End Encryption':fontsize=35:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-20:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         
         drawtext=text='● ↔ ●':fontsize=60:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+40:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         
         drawtext=text='Your conversations are private':fontsize=30:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+120:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='Available on Google Play':fontsize=35:fontcolor=0x5B4FE8:x=(w-text_w)/2:y=(h-text_h)/2+180:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:box=1:boxcolor=white@0.9:boxborderw=10,
         
         drawtext=text='App supported by ads':fontsize=20:fontcolor=0xC0C0C0:x=(w-text_w)/2:y=(h-text_h)/2+240:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         fade=in:st=0:d=0.5,fade=out:st=9.5:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/combined.mp4

echo "🔍 Verifying segments..."
for file in intro.mp4 demo.mp4 combined.mp4; do
    if [ -f "temp/$file" ] && [ -s "temp/$file" ]; then
        echo "✅ temp/$file ($(ls -lh temp/$file | awk '{print $5}'))"
    else
        echo "❌ Problem with temp/$file"
        exit 1
    fi
done

# Combine segments
echo "🔗 Combining segments..."
echo "file 'intro.mp4'" > temp/filelist.txt
echo "file 'demo.mp4'" >> temp/filelist.txt  
echo "file 'combined.mp4'" >> temp/filelist.txt

ffmpeg -y -f concat -safe 0 -i temp/filelist.txt -c copy output/lingualink-ad-enhanced.mp4

# Clean up
rm -rf temp/

echo ""
echo "✅ Centered 3-Slide LinguaLink ad complete!"
echo "📁 Output: output/lingualink-ad-enhanced.mp4"
echo "🎯 Updated Structure:"
echo "   📱 Slide 1: Intro (10s) - LinguaLink branding & 36+ languages"
echo "   🗣️ Slide 2: Voice Demo (10s) - CENTERED vertically"
echo "   🔒🖼️ Slide 3: Combined (10s) - CENTERED with MORE SPACING between lines"
echo "⏱️ Total: Exactly 30 seconds"
echo ""
echo "🎨 Improved Layout:"
echo "   ✅ Slide 2: All text vertically centered"
echo "   ✅ Slide 3: Better vertical centering + increased line spacing"
echo "   ✅ Maintained all key messaging and features"

if [ -f "output/lingualink-ad-enhanced.mp4" ]; then
    echo ""
    echo "📊 File Information:"
    ls -lh output/lingualink-ad-enhanced.mp4
    ffprobe -v quiet -show_entries format=duration:stream=width,height,codec_name output/lingualink-ad-enhanced.mp4
else
    echo "❌ Error: Centered ad was not created"
fi