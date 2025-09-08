#!/bin/bash

echo "🎬 Enhancing LinguaLink Consistent Ad with D2D & Encryption..."

# Create output directory
mkdir -p output temp

# Video settings
WIDTH=1920
HEIGHT=1080
FPS=30
PURPLE_COLOR="0x5B4FE8"

echo "📊 Adding new slide to existing consistent ad:"
echo "   🔄 Device-to-Device translation messaging"
echo "   🔒 End-to-end encryption highlights"
echo "   📢 Ad support disclosure"
echo "⏱️ Target Duration: ~30 seconds (4 slides of ~7.5s each)"
echo ""

# Recreate original intro screen - shortened to 7.5 seconds
echo "📱 Creating intro screen (7.5s)..."
ffmpeg -y -f lavfi -i "color=c=${PURPLE_COLOR}:size=${WIDTH}x${HEIGHT}:duration=7.5:rate=${FPS}" \
    -vf "drawtext=text='LinguaLink':fontsize=160:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-200:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Real-time Speech Translation':fontsize=65:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-80:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='for Seamless Communication':fontsize=65:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-10:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='● → ♪':fontsize=120:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+120:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Supporting 36+ Languages':fontsize=50:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2+250:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         fade=in:st=0:d=0.5,fade=out:st=7:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/intro.mp4

# Create voice demo section - shortened to 7.5 seconds  
echo "🎯 Creating voice demo section (7.5s)..."
ffmpeg -y -f lavfi -i "color=c=${PURPLE_COLOR}:size=${WIDTH}x${HEIGHT}:duration=7.5:rate=${FPS}" \
    -vf "drawtext=text='Voice-to-Voice Translation':fontsize=70:fontcolor=white:x=(w-text_w)/2:y=120:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Speak in one language':fontsize=50:fontcolor=white:x=(w-text_w)/2:y=220:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='hear voice spoken in another!':fontsize=50:fontcolor=white:x=(w-text_w)/2:y=280:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='Hello how are you today?':fontsize=48:fontcolor=white:x=(w-text_w)/2:y=400:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=15,
         drawtext=text='● ENGLISH INPUT':fontsize=32:fontcolor=0xC0C0C0:x=(w-text_w)/2:y=460:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='↓':fontsize=80:fontcolor=white:x=(w-text_w)/2:y=520:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         
         drawtext=text='Como estas hoy?':fontsize=48:fontcolor=white:x=(w-text_w)/2:y=600:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=15,
         drawtext=text='♪ SPANISH OUTPUT':fontsize=32:fontcolor=0xC0C0C0:x=(w-text_w)/2:y=660:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='36+ Languages • Real-time • Natural Voices':fontsize=42:fontcolor=white:x=(w-text_w)/2:y=780:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         fade=in:st=0:d=0.5,fade=out:st=7:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/demo.mp4

# NEW: Create device-to-device and encryption slide (7.5 seconds)
echo "🔒 Creating D2D & Encryption slide (7.5s)..."
ffmpeg -y -f lavfi -i "color=c=${PURPLE_COLOR}:size=${WIDTH}x${HEIGHT}:duration=7.5:rate=${FPS}" \
    -vf "drawtext=text='Device-to-Device Translation':fontsize=65:fontcolor=white:x=(w-text_w)/2:y=150:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='End-to-End Encryption':fontsize=65:fontcolor=0xFFD700:x=(w-text_w)/2:y=220:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         
         drawtext=text='● ↔ ●':fontsize=100:fontcolor=white:x=(w-text_w)/2:y=320:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         
         drawtext=text='Your conversations are private':fontsize=45:fontcolor=white:x=(w-text_w)/2:y=450:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Direct device communication':fontsize=45:fontcolor=white:x=(w-text_w)/2:y=500:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Secure & encrypted messaging':fontsize=45:fontcolor=white:x=(w-text_w)/2:y=550:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='Note: App supported by ads which may collect':fontsize=25:fontcolor=0xC0C0C0:x=(w-text_w)/2:y=650:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='anonymous usage data.':fontsize=25:fontcolor=0xC0C0C0:x=(w-text_w)/2:y=680:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         fade=in:st=0:d=0.5,fade=out:st=7:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/d2d_encryption.mp4

# Recreate finale screen - shortened to 7.5 seconds
echo "🖼️ Creating finale screen (7.5s)..."
ffmpeg -y -f lavfi -i "color=c=${PURPLE_COLOR}:size=${WIDTH}x${HEIGHT}:duration=7.5:rate=${FPS}" \
    -vf "drawtext=text='LinguaLink':fontsize=140:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-250:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Break Language Barriers':fontsize=60:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-160:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Speak Naturally • Hear Naturally':fontsize=50:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2-90:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='● → ♪':fontsize=150:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+20:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         
         drawtext=text='Available on Google Play':fontsize=55:fontcolor=0x5B4FE8:x=(w-text_w)/2:y=(h-text_h)/2+180:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:box=1:boxcolor=white@0.95:boxborderw=20,
         drawtext=text='Real-time Translation for Everyone':fontsize=45:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+270:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         fade=in:st=0:d=0.5,fade=out:st=7:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/finale.mp4

echo "🔍 Verifying segments..."
for file in intro.mp4 demo.mp4 d2d_encryption.mp4 finale.mp4; do
    if [ -f "temp/$file" ] && [ -s "temp/$file" ]; then
        echo "✅ temp/$file ($(ls -lh temp/$file | awk '{print $5}'))"
    else
        echo "❌ Problem with temp/$file"
        exit 1
    fi
done

# Create enhanced ad
echo "🔗 Combining all sections..."
echo "file 'intro.mp4'" > temp/filelist.txt
echo "file 'demo.mp4'" >> temp/filelist.txt  
echo "file 'd2d_encryption.mp4'" >> temp/filelist.txt  
echo "file 'finale.mp4'" >> temp/filelist.txt

ffmpeg -y -f concat -safe 0 -i temp/filelist.txt -c copy output/lingualink-ad-enhanced.mp4

# Clean up
rm -rf temp/

echo ""
echo "✅ Enhanced LinguaLink ad complete!"
echo "📁 Output: output/lingualink-ad-enhanced.mp4"
echo "🎯 Enhanced Structure:"
echo "   📱 LinguaLink Intro (7.5s) - Brand and core messaging"
echo "   🗣️ Voice Demo (7.5s) - Translation example"
echo "   🔒 NEW: D2D & Encryption (7.5s) - Device-to-device + privacy"  
echo "   🖼️ Call-to-Action Finale (7.5s) - Google Play promotion"
echo "⏱️ Total Duration: 30 seconds"
echo ""
echo "🆕 New Features Added:"
echo "   ✅ Device-to-Device translation messaging"
echo "   ✅ End-to-End encryption highlights"
echo "   ✅ Ad support disclosure note"
echo "   🎨 Consistent purple background maintained"

# Final verification
if [ -f "output/lingualink-ad-enhanced.mp4" ]; then
    echo ""
    echo "📊 File Information:"
    ls -lh output/lingualink-ad-enhanced.mp4
    
    echo ""
    echo "🔍 Video Details:"
    ffprobe -v quiet -show_entries format=duration:stream=width,height,codec_name output/lingualink-ad-enhanced.mp4
else
    echo "❌ Error: Enhanced ad was not created"
fi