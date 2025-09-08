#!/bin/bash

echo "🎬 Updating LinguaLink Ad Final Slide..."

# Create output directory
mkdir -p output temp

# Video settings
WIDTH=1920
HEIGHT=1080
FPS=30
PURPLE_COLOR="0x5B4FE8"

echo "📊 Updating finale slide with new messaging:"
echo "   'Real time device to device conversation using translated speech'"
echo ""

# Keep intro the same (7.5 seconds)
echo "📱 Creating intro (7.5s)..."
ffmpeg -y -f lavfi -i "color=c=${PURPLE_COLOR}:size=${WIDTH}x${HEIGHT}:duration=7.5:rate=${FPS}" \
    -vf "drawtext=text='LinguaLink':fontsize=120:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-150:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Real-time Speech Translation':fontsize=50:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-50:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='● → ♪':fontsize=80:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+50:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Supporting 36+ Languages':fontsize=35:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2+150:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         fade=in:st=0:d=0.5,fade=out:st=7:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/intro.mp4

# Keep voice demo the same (7.5 seconds)
echo "🗣️ Creating voice demo (7.5s)..."
ffmpeg -y -f lavfi -i "color=c=${PURPLE_COLOR}:size=${WIDTH}x${HEIGHT}:duration=7.5:rate=${FPS}" \
    -vf "drawtext=text='Voice-to-Voice Translation':fontsize=60:fontcolor=white:x=(w-text_w)/2:y=150:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Speak in one language':fontsize=45:fontcolor=white:x=(w-text_w)/2:y=250:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='hear voice spoken in another':fontsize=45:fontcolor=white:x=(w-text_w)/2:y=300:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Hello how are you?':fontsize=35:fontcolor=white:x=(w-text_w)/2:y=400:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='↓':fontsize=60:fontcolor=white:x=(w-text_w)/2:y=450:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Como estas?':fontsize=35:fontcolor=white:x=(w-text_w)/2:y=520:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         fade=in:st=0:d=0.5,fade=out:st=7:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/demo.mp4

# Keep D2D slide the same (7.5 seconds)
echo "🔒 Creating D2D conversation slide (7.5s)..."
ffmpeg -y -f lavfi -i "color=c=${PURPLE_COLOR}:size=${WIDTH}x${HEIGHT}:duration=7.5:rate=${FPS}" \
    -vf "drawtext=text='Device-to-Device Conversation':fontsize=50:fontcolor=white:x=(w-text_w)/2:y=200:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='End-to-End Encryption':fontsize=50:fontcolor=0xFFD700:x=(w-text_w)/2:y=260:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='● ↔ ●':fontsize=80:fontcolor=white:x=(w-text_w)/2:y=350:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Your conversations are private':fontsize=35:fontcolor=white:x=(w-text_w)/2:y=450:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='App supported by ads':fontsize=25:fontcolor=0xC0C0C0:x=(w-text_w)/2:y=520:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         fade=in:st=0:d=0.5,fade=out:st=7:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/d2d.mp4

# NEW: Updated finale with the requested text (7.5 seconds)
echo "🖼️ Creating updated finale with new messaging (7.5s)..."
ffmpeg -y -f lavfi -i "color=c=${PURPLE_COLOR}:size=${WIDTH}x${HEIGHT}:duration=7.5:rate=${FPS}" \
    -vf "drawtext=text='LinguaLink':fontsize=100:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-200:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Real time device to device conversation':fontsize=40:fontcolor=0xFFD700:x=(w-text_w)/2:y=(h-text_h)/2-100:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='using translated speech':fontsize=40:fontcolor=0xFFD700:x=(w-text_w)/2:y=(h-text_h)/2-50:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='● → ♪':fontsize=80:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+30:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Available on Google Play':fontsize=40:fontcolor=0x5B4FE8:x=(w-text_w)/2:y=(h-text_h)/2+130:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:box=1:boxcolor=white@0.9:boxborderw=12,
         fade=in:st=0:d=0.5,fade=out:st=7:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/finale.mp4

echo "🔍 Verifying segments..."
for file in intro.mp4 demo.mp4 d2d.mp4 finale.mp4; do
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
echo "file 'd2d.mp4'" >> temp/filelist.txt  
echo "file 'finale.mp4'" >> temp/filelist.txt

ffmpeg -y -f concat -safe 0 -i temp/filelist.txt -c copy output/lingualink-ad-enhanced.mp4

# Clean up
rm -rf temp/

echo ""
echo "✅ Updated LinguaLink ad complete!"
echo "📁 Output: output/lingualink-ad-enhanced.mp4"
echo "🎯 Final Structure:"
echo "   📱 Intro (7.5s) - LinguaLink branding"
echo "   🗣️ Voice Demo (7.5s) - Translation example"
echo "   🔒 D2D & Encryption (7.5s) - Device-to-device conversation"  
echo "   🖼️ UPDATED Finale (7.5s) - 'Real time device to device conversation using translated speech'"
echo "⏱️ Total: Exactly 30 seconds"

if [ -f "output/lingualink-ad-enhanced.mp4" ]; then
    echo ""
    echo "📊 File Information:"
    ls -lh output/lingualink-ad-enhanced.mp4
    ffprobe -v quiet -show_entries format=duration:stream=width,height,codec_name output/lingualink-ad-enhanced.mp4
else
    echo "❌ Error: Updated ad was not created"
fi