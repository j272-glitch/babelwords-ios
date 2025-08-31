#!/bin/bash

echo "🎬 Creating Improved LinguaLink Ad with Unicode Icons..."

# Create output directory
mkdir -p output temp

# Video settings
WIDTH=1920
HEIGHT=1080
FPS=30

echo "📊 Video Settings:"
echo "   Resolution: ${WIDTH}x${HEIGHT}"
echo "   Frame Rate: ${FPS} FPS"
echo "   Duration: 30 seconds"
echo "   Icons: Unicode characters instead of text"
echo "   Background: Consistent purple gradient"
echo ""

# Copy thumbnail to temp directory
echo "📁 Preparing thumbnail image..."
cp "attached_assets/LinguaLink_ad_YouTube_thumbnail_d2830c91_1756652984731.png" temp/thumbnail.png

# Create speech intro section (12 seconds) with Unicode icons
echo "🎙️ Creating speech intro section with Unicode icons (12s)..."
ffmpeg -y -f lavfi -i "color=c=0x6C63FF:size=${WIDTH}x${HEIGHT}:duration=12:rate=${FPS}" \
    -vf "drawtext=text='LinguaLink':fontsize=140:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-150:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Speech-to-Speech Translation':fontsize=55:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-20:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='♪':fontsize=90:fontcolor=white:x=(w-text_w)/2-150:y=(h-text_h)/2+80:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='→':fontsize=60:fontcolor=white:x=(w-text_w)/2-40:y=(h-text_h)/2+90:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='♫':fontsize=90:fontcolor=white:x=(w-text_w)/2+100:y=(h-text_h)/2+80:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Speak Naturally • Hear Naturally':fontsize=45:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2+250:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         fade=in:st=0:d=0.5,fade=out:st=11.5:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/intro.mp4

# Create demo section (10 seconds) with Unicode icons
echo "🎯 Creating demo section with Unicode icons (10s)..."
ffmpeg -y -f lavfi -i "color=c=0x5B4FE8:size=${WIDTH}x${HEIGHT}:duration=10:rate=${FPS}" \
    -vf "drawtext=text='Voice-to-Voice Translation':fontsize=60:fontcolor=white:x=(w-text_w)/2:y=150:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         
         drawtext=text='Hello how are you?':fontsize=50:fontcolor=white:x=(w-text_w)/2:y=300:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=15,
         drawtext=text='♪ ENGLISH INPUT':fontsize=35:fontcolor=0xC0C0C0:x=(w-text_w)/2:y=370:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='↓':fontsize=80:fontcolor=white:x=(w-text_w)/2:y=450:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         
         drawtext=text='Como estas?':fontsize=50:fontcolor=white:x=(w-text_w)/2:y=550:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=15,
         drawtext=text='♫ SPANISH OUTPUT':fontsize=35:fontcolor=0xC0C0C0:x=(w-text_w)/2:y=620:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='AI-Powered • 36+ Languages • Real-time':fontsize=40:fontcolor=white:x=(w-text_w)/2:y=750:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         fade=in:st=0:d=0.5,fade=out:st=9.5:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/demo.mp4

# Create custom thumbnail finale with purple background overlay (8 seconds)
echo "📱 Creating custom thumbnail finale with matching background (8s)..."
ffmpeg -y -loop 1 -i temp/thumbnail.png -f lavfi -i "color=c=0x6C63FF:size=${WIDTH}x${HEIGHT}:duration=8:rate=${FPS}" \
    -filter_complex "[0:v]scale=${WIDTH}:${HEIGHT}:force_original_aspect_ratio=decrease,pad=${WIDTH}:${HEIGHT}:(ow-iw)/2:(oh-ih)/2:color=0x6C63FF[thumb];
                     [1:v][thumb]overlay=0:0[bg];
                     [bg]drawtext=text='LinguaLink':fontsize=120:fontcolor=white:x=(w-text_w)/2:y=100:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
                     drawtext=text='Real-time Translation':fontsize=60:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=180:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
                     drawtext=text='Available on Google Play':fontsize=50:fontcolor=white:x=(w-text_w)/2:y=900:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:box=1:boxcolor=white@0.2:boxborderw=15,
                     drawtext=text='Break Language Barriers':fontsize=45:fontcolor=0xD0D0D0:x=(w-text_w)/2:y=970:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
                     fade=in:st=0:d=0.5,fade=out:st=7.5:d=0.5[v]" \
    -map "[v]" -c:v libx264 -pix_fmt yuv420p -preset fast -r ${FPS} temp/finale.mp4

echo "🔍 Verifying all segments created..."
for file in intro.mp4 demo.mp4 finale.mp4; do
    if [ -f "temp/$file" ] && [ -s "temp/$file" ]; then
        echo "✅ temp/$file ($(ls -lh temp/$file | awk '{print $5}'))"
    else
        echo "❌ Problem with temp/$file"
        exit 1
    fi
done

# Create final video
echo "🔗 Combining all sections..."
echo "file 'intro.mp4'" > temp/filelist.txt
echo "file 'demo.mp4'" >> temp/filelist.txt  
echo "file 'finale.mp4'" >> temp/filelist.txt

ffmpeg -y -f concat -safe 0 -i temp/filelist.txt -c copy output/lingualink-ad-improved.mp4

# Clean up
rm -rf temp/

echo ""
echo "✅ Improved LinguaLink ad complete!"
echo "📁 Output: output/lingualink-ad-improved.mp4"
echo "🎯 Improvements:"
echo "   ♪ Unicode music note for microphone input"
echo "   ♫ Unicode music note for speaker output"  
echo "   🎨 Consistent purple gradient background"
echo "   🚫 No hashtag color references"
echo "⏱️ Total Duration: 30 seconds"

# Final file check
if [ -f "output/lingualink-ad-improved.mp4" ]; then
    echo ""
    echo "📊 File Information:"
    ls -lh output/lingualink-ad-improved.mp4
    
    echo ""
    echo "🔍 Video Details:"
    ffprobe -v quiet -show_entries format=duration:stream=width,height,codec_name output/lingualink-ad-improved.mp4
else
    echo "❌ Error: Improved video was not created"
fi