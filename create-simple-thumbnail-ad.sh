#!/bin/bash

echo "🎬 Creating Simple LinguaLink Ad with Custom Thumbnail..."

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
echo "   Structure: Intro + Demo + Custom Thumbnail"
echo ""

# Copy thumbnail to temp directory
echo "📁 Preparing thumbnail image..."
cp "attached_assets/LinguaLink_ad_YouTube_thumbnail_d2830c91_1756652984731.png" temp/thumbnail.png

# Create speech intro section (10 seconds)
echo "🎙️ Creating speech intro section (10s)..."
ffmpeg -y -f lavfi -i "color=c=0x6C63FF:size=${WIDTH}x${HEIGHT}:duration=10:rate=${FPS}" \
    -vf "drawtext=text='LinguaLink':fontsize=140:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-120:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Speak in one language':fontsize=55:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-20:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='hear voice spoken in another!':fontsize=55:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+40:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Voice Translation Magic':fontsize=45:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2+200:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         fade=in:st=0:d=0.5,fade=out:st=9.5:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/intro.mp4

# Create demo section (12 seconds)
echo "🎯 Creating demo section (12s)..."
ffmpeg -y -f lavfi -i "color=c=0x5B4FE8:size=${WIDTH}x${HEIGHT}:duration=12:rate=${FPS}" \
    -vf "drawtext=text='Natural Voice Translation':fontsize=70:fontcolor=white:x=(w-text_w)/2:y=120:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='[MIC] SPEAK -> [AI] TRANSLATE -> [SPEAKER] LISTEN':fontsize=45:fontcolor=white:x=(w-text_w)/2:y=200:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         
         drawtext=text='LIVE DEMO:':fontsize=50:fontcolor=white:x=(w-text_w)/2:y=320:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         
         drawtext=text='Hello how are you today?':fontsize=48:fontcolor=white:x=(w-text_w)/2:y=400:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=15,
         drawtext=text='[MIC] English Voice Input':fontsize=35:fontcolor=0xC0C0C0:x=(w-text_w)/2:y=460:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='\\|/':fontsize=80:fontcolor=white:x=(w-text_w)/2:y=520:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         
         drawtext=text='Como estas hoy?':fontsize=48:fontcolor=white:x=(w-text_w)/2:y=600:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=15,
         drawtext=text='[SPEAKER] Spanish Voice Output':fontsize=35:fontcolor=0xC0C0C0:x=(w-text_w)/2:y=660:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='36+ Languages • Real-time • Natural Voices':fontsize=40:fontcolor=white:x=(w-text_w)/2:y=780:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         fade=in:st=0:d=0.5,fade=out:st=11.5:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/demo.mp4

# Create custom thumbnail finale (8 seconds)
echo "📱 Creating custom thumbnail finale (8s)..."
ffmpeg -y -i temp/thumbnail.png -t 8 -vf "scale=${WIDTH}:${HEIGHT}:force_original_aspect_ratio=decrease,pad=${WIDTH}:${HEIGHT}:(ow-iw)/2:(oh-ih)/2:color=0x6C63FF,fade=in:st=0:d=0.5,fade=out:st=7.5:d=0.5" -c:v libx264 -pix_fmt yuv420p -preset fast -r ${FPS} temp/finale.mp4

echo "🔍 Checking created segments..."
ls -la temp/

# Check segments exist and have content
for file in intro.mp4 demo.mp4 finale.mp4; do
    if [ ! -f "temp/$file" ]; then
        echo "❌ Missing: temp/$file"
        exit 1
    elif [ ! -s "temp/$file" ]; then
        echo "❌ Empty: temp/$file"
        exit 1
    else
        echo "✅ Created: temp/$file ($(ls -lh temp/$file | awk '{print $5}'))"
    fi
done

# Create filelist and concatenate
echo "🔗 Combining all sections..."
echo "file 'intro.mp4'" > temp/filelist.txt
echo "file 'demo.mp4'" >> temp/filelist.txt
echo "file 'finale.mp4'" >> temp/filelist.txt

ffmpeg -y -f concat -safe 0 -i temp/filelist.txt -c copy output/lingualink-ad-with-thumbnail.mp4

# Clean up
rm -rf temp/

echo ""
echo "✅ LinguaLink ad with custom thumbnail complete!"
echo "📁 Output: output/lingualink-ad-with-thumbnail.mp4"
echo "⏱️ Duration: 30 seconds"
echo "🎯 Resolution: 1920x1080 (1080p)"
echo "🖼️ Finale: Your custom YouTube thumbnail"
echo "📤 Ready for all platforms!"

# Show final file info
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