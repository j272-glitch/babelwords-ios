#!/bin/bash

echo "🎬 Creating LinguaLink Ad with Thumbnail Finale..."

# Create output directory
mkdir -p output temp

# Video settings
WIDTH=1920
HEIGHT=1080
FPS=30

echo "📊 Using existing working sections + custom thumbnail finale"
echo ""

# Copy thumbnail to temp directory
echo "📁 Preparing thumbnail image..."
cp "attached_assets/LinguaLink_ad_YouTube_thumbnail_d2830c91_1756652984731.png" temp/thumbnail.png

# Create speech intro section (12 seconds) - known working
echo "🎙️ Creating speech intro section (12s)..."
ffmpeg -y -f lavfi -i "color=c=0x6C63FF:size=${WIDTH}x${HEIGHT}:duration=12:rate=${FPS}" \
    -vf "drawtext=text='LinguaLink':fontsize=140:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-150:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Speech-to-Speech Translation':fontsize=55:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-20:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='[MIC] -> [SPEAKER]':fontsize=70:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+80:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Speak Naturally • Hear Naturally':fontsize=45:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2+250:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         fade=in:st=0:d=0.5,fade=out:st=11.5:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/intro.mp4

# Create demo section (10 seconds) - simplified version  
echo "🎯 Creating demo section (10s)..."
ffmpeg -y -f lavfi -i "color=c=0x5B4FE8:size=${WIDTH}x${HEIGHT}:duration=10:rate=${FPS}" \
    -vf "drawtext=text='Hello how are you?':fontsize=50:fontcolor=white:x=(w-text_w)/2:y=300:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=15,
         drawtext=text='[MIC] ENGLISH INPUT':fontsize=35:fontcolor=0xC0C0C0:x=(w-text_w)/2:y=370:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='\\|/':fontsize=80:fontcolor=white:x=(w-text_w)/2:y=450:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Como estas?':fontsize=50:fontcolor=white:x=(w-text_w)/2:y=550:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=15,
         drawtext=text='[SPEAKER] SPANISH OUTPUT':fontsize=35:fontcolor=0xC0C0C0:x=(w-text_w)/2:y=620:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Voice-to-Voice Translation':fontsize=60:fontcolor=white:x=(w-text_w)/2:y=150:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         fade=in:st=0:d=0.5,fade=out:st=9.5:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/demo.mp4

# Create custom thumbnail finale (8 seconds)
echo "📱 Creating custom thumbnail finale (8s)..."
ffmpeg -y -loop 1 -i temp/thumbnail.png -t 8 -vf "scale=${WIDTH}:${HEIGHT}:force_original_aspect_ratio=decrease,pad=${WIDTH}:${HEIGHT}:(ow-iw)/2:(oh-ih)/2:color=0x6C63FF,fade=in:st=0:d=0.5,fade=out:st=7.5:d=0.5" -c:v libx264 -pix_fmt yuv420p -preset fast -r ${FPS} temp/finale.mp4

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

ffmpeg -y -f concat -safe 0 -i temp/filelist.txt -c copy output/lingualink-ad-with-thumbnail.mp4

# Clean up
rm -rf temp/

echo ""
echo "✅ LinguaLink ad with custom YouTube thumbnail complete!"
echo "📁 Output: output/lingualink-ad-with-thumbnail.mp4"
echo "🎯 Structure: Intro (12s) + Demo (10s) + Your Thumbnail (8s)"
echo "⏱️ Total Duration: 30 seconds"
echo "🖼️ Finale: Your custom YouTube thumbnail!"

# Final file check
if [ -f "output/lingualink-ad-with-thumbnail.mp4" ]; then
    echo ""
    echo "📊 File Information:"
    ls -lh output/lingualink-ad-with-thumbnail.mp4
    
    echo ""
    echo "🔍 Video Details:"
    ffprobe -v quiet -show_entries format=duration:stream=width,height,codec_name output/lingualink-ad-with-thumbnail.mp4
else
    echo "❌ Error: Final video was not created"
fi