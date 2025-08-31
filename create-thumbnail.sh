#!/bin/bash

echo "🖼️ Creating Thumbnail for LinguaLink Ad..."

# Create output directory
mkdir -p output temp

# Thumbnail settings
WIDTH=1920
HEIGHT=1080
PURPLE_COLOR="0x5B4FE8"

echo "📊 Thumbnail Settings:"
echo "   Resolution: ${WIDTH}x${HEIGHT}"
echo "   Background: #5B4FE8 (matching ad)"
echo "   Format: PNG (high quality)"
echo ""

# Create attractive thumbnail
echo "🎨 Creating thumbnail design..."
ffmpeg -y -f lavfi -i "color=c=${PURPLE_COLOR}:size=${WIDTH}x${HEIGHT}:duration=1:rate=1" \
    -vf "drawtext=text='LinguaLink':fontsize=180:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-280:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Voice-to-Voice Translation':fontsize=70:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-150:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Speak in one language':fontsize=50:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2-70:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='hear voice spoken in another!':fontsize=50:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2-20:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='● → ♪':fontsize=140:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+80:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         
         drawtext=text='36+ Languages • Real-time • Natural Voices':fontsize=45:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+200:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='30 Second Ad':fontsize=35:fontcolor=0xC0C0C0:x=(w-text_w)/2:y=(h-text_h)/2+280:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='▶':fontsize=100:fontcolor=white:x=100:y=100:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=20" \
    -frames:v 1 -c:v png output/lingualink-ad-thumbnail.png

# Also create a YouTube-style thumbnail with play button
echo "📺 Creating YouTube-style thumbnail..."
ffmpeg -y -f lavfi -i "color=c=${PURPLE_COLOR}:size=${WIDTH}x${HEIGHT}:duration=1:rate=1" \
    -vf "drawtext=text='LinguaLink':fontsize=160:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-250:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='VOICE TRANSLATION':fontsize=60:fontcolor=0xFFD700:x=(w-text_w)/2:y=(h-text_h)/2-150:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Speak in One Language':fontsize=45:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-80:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Hear Voice in Another!':fontsize=45:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-35:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='●':fontsize=80:fontcolor=white:x=(w-text_w)/2-120:y=(h-text_h)/2+50:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='→':fontsize=60:fontcolor=0xFFD700:x=(w-text_w)/2-30:y=(h-text_h)/2+60:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='♪':fontsize=80:fontcolor=white:x=(w-text_w)/2+60:y=(h-text_h)/2+50:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         
         drawtext=text='36+ LANGUAGES':fontsize=35:fontcolor=0xFFD700:x=(w-text_w)/2-200:y=(h-text_h)/2+150:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:box=1:boxcolor=0x4B3FD8@0.9:boxborderw=10,
         drawtext=text='REAL-TIME':fontsize=35:fontcolor=0xFFD700:x=(w-text_w)/2+50:y=(h-text_h)/2+150:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:box=1:boxcolor=0x4B3FD8@0.9:boxborderw=10,
         
         drawtext=text='30 SEC DEMO':fontsize=30:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+220:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawbox=x=(w-200)/2:y=(h-80)/2:w=200:h=80:color=white@0.2:t=fill,
         drawtext=text='▶':fontsize=60:fontcolor=white:x=(w-text_w)/2+10:y=(h-text_h)/2+5:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" \
    -frames:v 1 -c:v png output/lingualink-ad-youtube-thumbnail.png

echo "🔍 Verifying thumbnails..."
for file in lingualink-ad-thumbnail.png lingualink-ad-youtube-thumbnail.png; do
    if [ -f "output/$file" ] && [ -s "output/$file" ]; then
        echo "✅ output/$file ($(ls -lh output/$file | awk '{print $5}'))"
    else
        echo "❌ Problem with output/$file"
        exit 1
    fi
done

echo ""
echo "✅ Thumbnails created successfully!"
echo "📁 Generated Files:"
echo "   📄 output/lingualink-ad-thumbnail.png - Clean professional thumbnail"
echo "   📺 output/lingualink-ad-youtube-thumbnail.png - YouTube-style with play button"
echo ""
echo "🎯 Thumbnail Features:"
echo "   🎨 Consistent purple background (#5B4FE8)"
echo "   📝 Key messaging: Voice-to-Voice Translation"
echo "   ● Professional icons showing process flow"
echo "   📱 LinguaLink branding (no beta)"
echo "   ⏱️ Duration indicator (30 seconds)"
echo "   🌍 Language support highlight"

# Display file information
if [ -f "output/lingualink-ad-thumbnail.png" ]; then
    echo ""
    echo "📊 Thumbnail Details:"
    file output/lingualink-ad-thumbnail.png
    ls -lh output/lingualink-ad-thumbnail.png
fi