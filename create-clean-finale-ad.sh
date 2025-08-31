#!/bin/bash

echo "🎬 Creating LinguaLink Ad with Clean Final Screen..."

# Create output directory
mkdir -p output temp

# Video settings
WIDTH=1920
HEIGHT=1080
FPS=30

echo "📊 Removing hashtag color references from final screen"
echo ""

# Copy both images to temp directory
echo "📁 Preparing images..."
cp "attached_assets/IMG_1865_1756653686966.PNG" temp/app_screen.png
cp "attached_assets/LinguaLink_ad_YouTube_thumbnail_d2830c91_1756652984731.png" temp/thumbnail.png

# Create app interface opening screen (10 seconds) - same as before
echo "📱 Creating app interface opening (10s)..."
ffmpeg -y -loop 1 -i temp/app_screen.png -t 10 -vf "scale=${WIDTH}:${HEIGHT}:force_original_aspect_ratio=decrease,pad=${WIDTH}:${HEIGHT}:(ow-iw)/2:(oh-ih)/2:color=0x6C63FF,fade=in:st=0:d=0.5,fade=out:st=9.5:d=0.5" -c:v libx264 -pix_fmt yuv420p -preset fast -r ${FPS} temp/app_intro.mp4

# Create voice demo section (12 seconds) - same as before
echo "🎯 Creating voice demo section (12s)..."
ffmpeg -y -f lavfi -i "color=c=0x5B4FE8:size=${WIDTH}x${HEIGHT}:duration=12:rate=${FPS}" \
    -vf "drawtext=text='Voice-to-Voice Translation':fontsize=70:fontcolor=white:x=(w-text_w)/2:y=120:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Speak in one language':fontsize=50:fontcolor=white:x=(w-text_w)/2:y=220:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='hear voice spoken in another!':fontsize=50:fontcolor=white:x=(w-text_w)/2:y=280:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='Hello how are you today?':fontsize=48:fontcolor=white:x=(w-text_w)/2:y=400:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=15,
         drawtext=text='● ENGLISH INPUT':fontsize=32:fontcolor=0xC0C0C0:x=(w-text_w)/2:y=460:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='↓':fontsize=80:fontcolor=white:x=(w-text_w)/2:y=520:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         
         drawtext=text='Como estas hoy?':fontsize=48:fontcolor=white:x=(w-text_w)/2:y=600:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=15,
         drawtext=text='♪ SPANISH OUTPUT':fontsize=32:fontcolor=0xC0C0C0:x=(w-text_w)/2:y=660:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='36+ Languages • Real-time • Natural Voices':fontsize=42:fontcolor=white:x=(w-text_w)/2:y=780:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         fade=in:st=0:d=0.5,fade=out:st=11.5:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/demo.mp4

# Create clean finale with hashtag strings covered (8 seconds)
echo "🖼️ Creating clean finale without hashtag strings (8s)..."
ffmpeg -y -loop 1 -i temp/thumbnail.png -t 8 -vf "scale=${WIDTH}:${HEIGHT}:force_original_aspect_ratio=decrease,pad=${WIDTH}:${HEIGHT}:(ow-iw)/2:(oh-ih)/2:color=0x6C63FF[scaled];
[scaled]drawbox=x=0:y=900:w=400:h=180:color=0x6C63FF:t=fill[clean];
[clean]fade=in:st=0:d=0.5,fade=out:st=7.5:d=0.5" -c:v libx264 -pix_fmt yuv420p -preset fast -r ${FPS} temp/finale.mp4

echo "🔍 Verifying segments..."
for file in app_intro.mp4 demo.mp4 finale.mp4; do
    if [ -f "temp/$file" ] && [ -s "temp/$file" ]; then
        echo "✅ temp/$file ($(ls -lh temp/$file | awk '{print $5}'))"
    else
        echo "❌ Problem with temp/$file"
        exit 1
    fi
done

# Create final ad
echo "🔗 Combining all sections..."
echo "file 'app_intro.mp4'" > temp/filelist.txt
echo "file 'demo.mp4'" >> temp/filelist.txt  
echo "file 'finale.mp4'" >> temp/filelist.txt

ffmpeg -y -f concat -safe 0 -i temp/filelist.txt -c copy output/lingualink-ad-clean.mp4

# Clean up
rm -rf temp/

echo ""
echo "✅ Clean LinguaLink ad complete!"
echo "📁 Output: output/lingualink-ad-clean.mp4"
echo "🎯 Fixed: Removed hashtag color references from final screen"
echo "⏱️ Duration: 30 seconds"

# Final verification
if [ -f "output/lingualink-ad-clean.mp4" ]; then
    echo ""
    echo "📊 File Information:"
    ls -lh output/lingualink-ad-clean.mp4
    
    echo ""
    echo "🔍 Video Details:"
    ffprobe -v quiet -show_entries format=duration:stream=width,height,codec_name output/lingualink-ad-clean.mp4
else
    echo "❌ Error: Clean ad was not created"
fi