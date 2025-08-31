#!/bin/bash

echo "🎬 Creating LinguaLink Ad with Custom Thumbnail Finale..."

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
echo "   Finale: Custom YouTube thumbnail"
echo ""

# Copy thumbnail to temp directory for FFmpeg access
echo "📁 Preparing thumbnail image..."
cp "attached_assets/LinguaLink_ad_YouTube_thumbnail_d2830c91_1756652984731.png" temp/thumbnail.png

# Create speech intro section (12 seconds)
echo "🎙️ Creating speech intro section (12s)..."
ffmpeg -y -f lavfi -i "color=c=0x6C63FF:size=${WIDTH}x${HEIGHT}:duration=12:rate=${FPS}" \
    -vf "drawtext=text='LinguaLink':fontsize=140:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-150:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Speak in one language':fontsize=60:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-50:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='hear voice spoken in another!':fontsize=60:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+10:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='[MIC] -> [SPEAKER]':fontsize=80:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+120:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='AI-Powered Voice Translation':fontsize=45:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2+250:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         fade=in:st=0:d=0.5,fade=out:st=11.5:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/intro.mp4

# Create speech demonstration section (10 seconds)
echo "🎯 Creating speech demonstration section (10s)..."
ffmpeg -y -f lavfi -i "color=c=0x5B4FE8:size=${WIDTH}x${HEIGHT}:duration=10:rate=${FPS}" \
    -vf "drawtext=text='Voice-to-Voice Translation':fontsize=70:fontcolor=white:x=(w-text_w)/2:y=120:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         
         drawtext=text='[MIC] SPEAK':fontsize=55:fontcolor=white:x=200:y=280:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Natural speech recognition':fontsize=35:fontcolor=0xE0E0E0:x=200:y=330:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='[AI] TRANSLATE':fontsize=55:fontcolor=white:x=200:y=420:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Real-time AI processing':fontsize=35:fontcolor=0xE0E0E0:x=200:y=470:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='[SPEAKER] LISTEN':fontsize=55:fontcolor=white:x=200:y=560:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Natural voice output':fontsize=35:fontcolor=0xE0E0E0:x=200:y=610:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='DEMO:':fontsize=45:fontcolor=white:x=1000:y=300:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Hello how are you?':fontsize=42:fontcolor=white:x=1000:y=350:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=12,
         drawtext=text='[MIC] English':fontsize=28:fontcolor=0xC0C0C0:x=1000:y=400:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='Como estas?':fontsize=42:fontcolor=white:x=1000:y=480:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=12,
         drawtext=text='[SPEAKER] Spanish':fontsize=28:fontcolor=0xC0C0C0:x=1000:y=530:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='36+ Languages • Instant Results':fontsize=45:fontcolor=white:x=(w-text_w)/2:y=720:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         fade=in:st=0:d=0.5,fade=out:st=9.5:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/demo.mp4

# Create custom thumbnail finale section (8 seconds)
echo "📱 Creating custom thumbnail finale section (8s)..."
ffmpeg -y -i temp/thumbnail.png -f lavfi -i "color=c=0x6C63FF:size=${WIDTH}x${HEIGHT}:duration=8:rate=${FPS}" \
    -filter_complex "[0:v]scale=${WIDTH}:${HEIGHT}:force_original_aspect_ratio=decrease,pad=${WIDTH}:${HEIGHT}:(ow-iw)/2:(oh-ih)/2:color=0x6C63FF[thumb];
                     [1:v][thumb]overlay=0:0,
                     fade=in:st=0:d=0.5,fade=out:st=7.5:d=0.5[v]" \
    -map "[v]" -c:v libx264 -pix_fmt yuv420p -preset fast temp/finale.mp4

# Check that all segments were created
if [ ! -f "temp/intro.mp4" ] || [ ! -f "temp/demo.mp4" ] || [ ! -f "temp/finale.mp4" ]; then
    echo "❌ Error: Not all video segments were created"
    ls -la temp/
    exit 1
fi

# Concatenate all sections into final video
echo "🔗 Combining sections into final video..."
echo "file 'intro.mp4'" > temp/filelist.txt
echo "file 'demo.mp4'" >> temp/filelist.txt
echo "file 'finale.mp4'" >> temp/filelist.txt

ffmpeg -y -f concat -safe 0 -i temp/filelist.txt -c copy output/lingualink-ad-thumbnail-finale.mp4

# Clean up temporary files
rm -rf temp/

echo ""
echo "✅ LinguaLink ad with custom thumbnail finale complete!"
echo "📁 Output: output/lingualink-ad-thumbnail-finale.mp4"
echo "⏱️ Duration: 30 seconds"
echo "🎯 Resolution: 1920x1080 (1080p)"
echo "🖼️ Finale: Custom YouTube thumbnail"
echo "📤 Perfect for marketing campaigns!"

# Show file info
if [ -f "output/lingualink-ad-thumbnail-finale.mp4" ]; then
    echo ""
    echo "📊 File Information:"
    ls -lh output/lingualink-ad-thumbnail-finale.mp4
    
    echo ""
    echo "🔍 Video Details:"
    ffprobe -v quiet -show_entries format=duration:stream=width,height,codec_name output/lingualink-ad-thumbnail-finale.mp4
else
    echo "❌ Error: Thumbnail finale video file was not created"
fi