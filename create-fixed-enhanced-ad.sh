#!/bin/bash

echo "🎬 Creating Fixed Enhanced LinguaLink Ad..."

# Create output directory
mkdir -p output temp

# Video settings
WIDTH=1920
HEIGHT=1080
FPS=30
PURPLE_COLOR="0x5B4FE8"

echo "📊 Adding D2D slide to existing consistent ad structure"
echo ""

# Create intro screen (7 seconds)
echo "📱 Creating intro screen (7s)..."
ffmpeg -y -f lavfi -i "color=c=${PURPLE_COLOR}:size=${WIDTH}x${HEIGHT}:duration=7:rate=${FPS}" \
    -vf "drawtext=text='LinguaLink':fontsize=140:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-200:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Real-time Speech Translation':fontsize=55:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-80:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='for Seamless Communication':fontsize=55:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-20:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='● → ♪':fontsize=80:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+80:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Supporting 36+ Languages':fontsize=40:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2+180:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         fade=in:st=0:d=0.5,fade=out:st=6.5:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/intro.mp4

# Create voice demo (8 seconds)
echo "🎯 Creating voice demo (8s)..."
ffmpeg -y -f lavfi -i "color=c=${PURPLE_COLOR}:size=${WIDTH}x${HEIGHT}:duration=8:rate=${FPS}" \
    -vf "drawtext=text='Voice-to-Voice Translation':fontsize=60:fontcolor=white:x=(w-text_w)/2:y=120:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Speak in one language':fontsize=45:fontcolor=white:x=(w-text_w)/2:y=200:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='hear voice spoken in another!':fontsize=45:fontcolor=white:x=(w-text_w)/2:y=250:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Hello how are you?':fontsize=40:fontcolor=white:x=(w-text_w)/2:y=350:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=10,
         drawtext=text='● ENGLISH INPUT':fontsize=25:fontcolor=0xC0C0C0:x=(w-text_w)/2:y=390:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='↓':fontsize=60:fontcolor=white:x=(w-text_w)/2:y=430:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Como estas?':fontsize=40:fontcolor=white:x=(w-text_w)/2:y=500:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=10,
         drawtext=text='♪ SPANISH OUTPUT':fontsize=25:fontcolor=0xC0C0C0:x=(w-text_w)/2:y=540:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Real-time • Natural Voices':fontsize=35:fontcolor=white:x=(w-text_w)/2:y=620:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         fade=in:st=0:d=0.5,fade=out:st=7.5:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/demo.mp4

# Create D2D and encryption slide (8 seconds)
echo "🔒 Creating D2D encryption slide (8s)..."
ffmpeg -y -f lavfi -i "color=c=${PURPLE_COLOR}:size=${WIDTH}x${HEIGHT}:duration=8:rate=${FPS}" \
    -vf "drawtext=text='Device-to-Device Translation':fontsize=55:fontcolor=white:x=(w-text_w)/2:y=150:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='End-to-End Encryption':fontsize=55:fontcolor=0xFFD700:x=(w-text_w)/2:y=210:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='● ↔ ●':fontsize=80:fontcolor=white:x=(w-text_w)/2:y=300:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Your conversations are private':fontsize=40:fontcolor=white:x=(w-text_w)/2:y=420:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Direct device communication':fontsize=40:fontcolor=white:x=(w-text_w)/2:y=470:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Note: App supported by ads which may collect':fontsize=22:fontcolor=0xC0C0C0:x=(w-text_w)/2:y=550:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='anonymous usage data.':fontsize=22:fontcolor=0xC0C0C0:x=(w-text_w)/2:y=575:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         fade=in:st=0:d=0.5,fade=out:st=7.5:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/d2d.mp4

# Create finale (7 seconds)
echo "🖼️ Creating finale (7s)..."
ffmpeg -y -f lavfi -i "color=c=${PURPLE_COLOR}:size=${WIDTH}x${HEIGHT}:duration=7:rate=${FPS}" \
    -vf "drawtext=text='LinguaLink':fontsize=120:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-200:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Break Language Barriers':fontsize=50:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-110:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Privately and Securely':fontsize=50:fontcolor=0xFFD700:x=(w-text_w)/2:y=(h-text_h)/2-60:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='● → ♪':fontsize=100:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+40:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Available on Google Play':fontsize=45:fontcolor=0x5B4FE8:x=(w-text_w)/2:y=(h-text_h)/2+150:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:box=1:boxcolor=white@0.9:boxborderw=15,
         drawtext=text='Real-time Translation for Everyone':fontsize=35:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+220:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         fade=in:st=0:d=0.5,fade=out:st=6.5:d=0.5" \
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

# Combine all segments
echo "🔗 Combining segments..."
echo "file 'intro.mp4'" > temp/filelist.txt
echo "file 'demo.mp4'" >> temp/filelist.txt  
echo "file 'd2d.mp4'" >> temp/filelist.txt  
echo "file 'finale.mp4'" >> temp/filelist.txt

ffmpeg -y -f concat -safe 0 -i temp/filelist.txt -c copy output/lingualink-ad-enhanced.mp4

# Clean up
rm -rf temp/

echo ""
echo "✅ Enhanced LinguaLink ad complete!"
echo "📁 Output: output/lingualink-ad-enhanced.mp4"
echo "🎯 Structure:"
echo "   📱 LinguaLink Intro (7s)"
echo "   🗣️ Voice Demo (8s)"
echo "   🔒 NEW: Device-to-Device & Encryption (8s)"  
echo "   🖼️ Call-to-Action Finale (7s)"
echo "⏱️ Total Duration: 30 seconds"
echo ""
echo "🆕 Added Features:"
echo "   ✅ Device-to-Device translation messaging"
echo "   ✅ End-to-End encryption highlights"
echo "   ✅ Ad support disclosure note"

if [ -f "output/lingualink-ad-enhanced.mp4" ]; then
    echo ""
    echo "📊 File Information:"
    ls -lh output/lingualink-ad-enhanced.mp4
    ffprobe -v quiet -show_entries format=duration:stream=width,height,codec_name output/lingualink-ad-enhanced.mp4
else
    echo "❌ Error: Enhanced ad was not created"
fi