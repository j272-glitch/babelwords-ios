#!/bin/bash

echo "🎬 Creating LinguaLink Ad without Encryption Text..."

# Create output directory
mkdir -p output temp

# Video settings
WIDTH=1920
HEIGHT=1080
FPS=30
PURPLE_COLOR="0x5B4FE8"

echo "📊 Removing 'End-to-End Encryption' from third slide"
echo "   🔄 Adjusting spacing and layout"
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

# Keep voice demo the same (10 seconds)
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

# Updated combined slide without encryption text (10 seconds)
echo "🔒🖼️ Creating updated combined slide without encryption (10s)..."
ffmpeg -y -f lavfi -i "color=c=${PURPLE_COLOR}:size=${WIDTH}x${HEIGHT}:duration=10:rate=${FPS}" \
    -vf "drawtext=text='LinguaLink':fontsize=90:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-220:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Real time device to device conversation':fontsize=45:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-120:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='using translated speech':fontsize=45:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-70:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         
         drawtext=text='● ↔ ●':fontsize=60:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+20:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         
         drawtext=text='Your conversations are private':fontsize=30:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+100:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='Available on Google Play':fontsize=35:fontcolor=0x5B4FE8:x=(w-text_w)/2:y=(h-text_h)/2+160:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:box=1:boxcolor=white@0.9:boxborderw=10,
         
         drawtext=text='App supported by ads':fontsize=28:fontcolor=0xC0C0C0:x=(w-text_w)/2:y=(h-text_h)/2+220:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
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
echo "✅ LinguaLink ad without encryption text complete!"
echo "📁 Output: output/lingualink-ad-enhanced.mp4"
echo "🎯 Updated Third Slide:"
echo "   📱 'Real time device to device conversation using translated speech' (WHITE, 45px)"
echo "   🔄 Device-to-device visual (● ↔ ●)"
echo "   🔒 'Your conversations are private' (privacy messaging)"
echo "   📱 'Available on Google Play' (call-to-action)"
echo "   📢 'App supported by ads' (28px)"
echo "   ❌ REMOVED: 'End-to-End Encryption'"
echo "⏱️ Total: Exactly 30 seconds"

if [ -f "output/lingualink-ad-enhanced.mp4" ]; then
    echo ""
    echo "📊 File Information:"
    ls -lh output/lingualink-ad-enhanced.mp4
    ffprobe -v quiet -show_entries format=duration:stream=width,height,codec_name output/lingualink-ad-enhanced.mp4
else
    echo "❌ Error: Updated ad was not created"
fi