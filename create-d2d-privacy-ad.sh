#!/bin/bash

echo "🎬 Creating LinguaLink D2D Privacy Ad..."

# Create output directory
mkdir -p output temp

# Video settings
WIDTH=1920
HEIGHT=1080
FPS=30
PURPLE_COLOR="0x5B4FE8"

echo "📊 New Ad Features:"
echo "   🔄 Device-to-Device (D2D) conversation mode"
echo "   🔒 Privacy messaging and encryption highlights"
echo "   💎 Premium vs Free model explanation"
echo "   ⏱️ Duration: 30 seconds"
echo ""

# Create D2D intro screen (8 seconds)
echo "📱 Creating D2D intro screen (8s)..."
ffmpeg -y -f lavfi -i "color=c=${PURPLE_COLOR}:size=${WIDTH}x${HEIGHT}:duration=8:rate=${FPS}" \
    -vf "drawtext=text='LinguaLink':fontsize=140:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-250:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Device-to-Device Translation':fontsize=60:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-150:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Real Conversations • Real Time':fontsize=45:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2-90:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='📱 ↔ 📱':fontsize=100:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-10:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         
         drawtext=text='Speak in One Language':fontsize=40:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+80:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Hear Voice in Another':fontsize=40:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+130:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='36+ Languages Supported':fontsize=35:fontcolor=0xC0C0C0:x=(w-text_w)/2:y=(h-text_h)/2+200:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         fade=in:st=0:d=0.5,fade=out:st=7.5:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/d2d_intro.mp4

# Create privacy features screen (12 seconds)
echo "🔒 Creating privacy features screen (12s)..."
ffmpeg -y -f lavfi -i "color=c=${PURPLE_COLOR}:size=${WIDTH}x${HEIGHT}:duration=12:rate=${FPS}" \
    -vf "drawtext=text='Your Conversations Are Private':fontsize=60:fontcolor=white:x=(w-text_w)/2:y=80:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Even From Us':fontsize=60:fontcolor=0xFFD700:x=(w-text_w)/2:y=150:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         
         drawtext=text='✓ End-to-end encryption for message content':fontsize=35:fontcolor=white:x=150:y=250:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='✓ We cannot read your translations':fontsize=35:fontcolor=white:x=150:y=300:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='✓ Conversations stay between participants':fontsize=35:fontcolor=white:x=150:y=350:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='🔒':fontsize=80:fontcolor=0xFFD700:x=(w-text_w)/2:y=420:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         
         drawtext=text='Note: This free app is supported by ads which may':fontsize=25:fontcolor=0xC0C0C0:x=(w-text_w)/2:y=540:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='collect anonymous usage data. Premium users':fontsize=25:fontcolor=0xC0C0C0:x=(w-text_w)/2:y=570:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='enjoy an ad-free experience.':fontsize=25:fontcolor=0xC0C0C0:x=(w-text_w)/2:y=600:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         fade=in:st=0:d=0.5,fade=out:st=11.5:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/privacy.mp4

# Create call-to-action finale (10 seconds)
echo "🚀 Creating call-to-action finale (10s)..."
ffmpeg -y -f lavfi -i "color=c=${PURPLE_COLOR}:size=${WIDTH}x${HEIGHT}:duration=10:rate=${FPS}" \
    -vf "drawtext=text='LinguaLink':fontsize=120:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-220:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Break Language Barriers Privately':fontsize=50:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-130:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         
         drawtext=text='📱 → 🔒 → 📱':fontsize=80:fontcolor=0xFFD700:x=(w-text_w)/2:y=(h-text_h)/2-40:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         
         drawtext=text='Device-to-Device • End-to-End Encrypted':fontsize=40:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+40:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Your Privacy Is Our Priority':fontsize=40:fontcolor=0xFFD700:x=(w-text_w)/2:y=(h-text_h)/2+90:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         
         drawtext=text='Available on Google Play':fontsize=45:fontcolor=0x5B4FE8:x=(w-text_w)/2:y=(h-text_h)/2+180:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:box=1:boxcolor=white@0.95:boxborderw=15,
         drawtext=text='Free with Ads • Premium Ad-Free':fontsize=35:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+250:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         fade=in:st=0:d=0.5,fade=out:st=9.5:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/finale.mp4

echo "🔍 Verifying segments..."
for file in d2d_intro.mp4 privacy.mp4 finale.mp4; do
    if [ -f "temp/$file" ] && [ -s "temp/$file" ]; then
        echo "✅ temp/$file ($(ls -lh temp/$file | awk '{print $5}'))"
    else
        echo "❌ Problem with temp/$file"
        exit 1
    fi
done

# Create final ad
echo "🔗 Combining all sections..."
echo "file 'd2d_intro.mp4'" > temp/filelist.txt
echo "file 'privacy.mp4'" >> temp/filelist.txt  
echo "file 'finale.mp4'" >> temp/filelist.txt

ffmpeg -y -f concat -safe 0 -i temp/filelist.txt -c copy output/lingualink-d2d-privacy-ad.mp4

# Clean up
rm -rf temp/

echo ""
echo "✅ LinguaLink D2D Privacy Ad complete!"
echo "📁 Output: output/lingualink-d2d-privacy-ad.mp4"
echo "🎯 New Features Highlighted:"
echo "   📱 Device-to-Device (D2D) conversation mode"
echo "   🔒 'Your Conversations Are Private, Even From Us'"
echo "   ✓ End-to-end encryption messaging"
echo "   ✓ Privacy protection guarantees"
echo "   💎 Free vs Premium model explanation"
echo "   🚫 No beta references"
echo "   🎨 Consistent purple background throughout"
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