#!/bin/bash

echo "🎬 Creating 30-Second LinguaLink Ad with Thumbnail Screen..."

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
echo "   Content: Thumbnail + Languages + CTA"
echo ""

# Create thumbnail screen section (8 seconds)
echo "🖼️ Creating thumbnail screen section (8s)..."
ffmpeg -y -f lavfi -i "color=c=0x6C63FF:size=${WIDTH}x${HEIGHT}:duration=8:rate=${FPS}" \
    -vf "drawtext=text='LinguaLink':fontsize=140:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-120:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Real-time Translation':fontsize=60:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-20:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='🌐':fontsize=200:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+100:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Break Language Barriers':fontsize=45:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2+250:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         fade=in:st=0:d=0.5,fade=out:st=7.5:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/thumbnail.mp4

# Create languages showcase section (17 seconds)
echo "🌍 Creating languages showcase section (17s)..."
ffmpeg -y -f lavfi -i "color=c=0x5B4FE8:size=${WIDTH}x${HEIGHT}:duration=17:rate=${FPS}" \
    -vf "drawtext=text='Speak Any Language':fontsize=80:fontcolor=white:x=(w-text_w)/2:y=120:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Instant AI Translation':fontsize=50:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=200:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='English':fontsize=40:fontcolor=white:x=200:y=320:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:enable='between(t,0,17)',
         drawtext=text='Hello World':fontsize=32:fontcolor=0xE0E0E0:x=200:y=360:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:enable='between(t,0,17)',
         
         drawtext=text='Spanish':fontsize=40:fontcolor=white:x=500:y=320:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:enable='between(t,0,17)',
         drawtext=text='Hola Mundo':fontsize=32:fontcolor=0xE0E0E0:x=500:y=360:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:enable='between(t,0,17)',
         
         drawtext=text='French':fontsize=40:fontcolor=white:x=800:y=320:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:enable='between(t,0,17)',
         drawtext=text='Bonjour le Monde':fontsize=32:fontcolor=0xE0E0E0:x=800:y=360:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:enable='between(t,0,17)',
         
         drawtext=text='German':fontsize=40:fontcolor=white:x=1200:y=320:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:enable='between(t,0,17)',
         drawtext=text='Hallo Welt':fontsize=32:fontcolor=0xE0E0E0:x=1200:y=360:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:enable='between(t,0,17)',
         
         drawtext=text='Japanese':fontsize=40:fontcolor=white:x=200:y=480:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:enable='between(t,0,17)',
         drawtext=text='Konnichiwa Sekai':fontsize=32:fontcolor=0xE0E0E0:x=200:y=520:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:enable='between(t,0,17)',
         
         drawtext=text='Italian':fontsize=40:fontcolor=white:x=500:y=480:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:enable='between(t,0,17)',
         drawtext=text='Ciao Mondo':fontsize=32:fontcolor=0xE0E0E0:x=500:y=520:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:enable='between(t,0,17)',
         
         drawtext=text='Portuguese':fontsize=40:fontcolor=white:x=800:y=480:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:enable='between(t,0,17)',
         drawtext=text='Ola Mundo':fontsize=32:fontcolor=0xE0E0E0:x=800:y=520:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:enable='between(t,0,17)',
         
         drawtext=text='Chinese':fontsize=40:fontcolor=white:x=1200:y=480:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:enable='between(t,0,17)',
         drawtext=text='Nihao Shijie':fontsize=32:fontcolor=0xE0E0E0:x=1200:y=520:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:enable='between(t,0,17)',
         
         drawtext=text='Russian':fontsize=40:fontcolor=white:x=200:y=640:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:enable='between(t,0,17)',
         drawtext=text='Privet Mir':fontsize=32:fontcolor=0xE0E0E0:x=200:y=680:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:enable='between(t,0,17)',
         
         drawtext=text='Arabic':fontsize=40:fontcolor=white:x=500:y=640:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:enable='between(t,0,17)',
         drawtext=text='Marhaba Alam':fontsize=32:fontcolor=0xE0E0E0:x=500:y=680:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:enable='between(t,0,17)',
         
         drawtext=text='Korean':fontsize=40:fontcolor=white:x=800:y=640:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:enable='between(t,0,17)',
         drawtext=text='Annyeong Segye':fontsize=32:fontcolor=0xE0E0E0:x=800:y=680:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:enable='between(t,0,17)',
         
         drawtext=text='Hindi':fontsize=40:fontcolor=white:x=1200:y=640:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:enable='between(t,0,17)',
         drawtext=text='Namaste Vishwa':fontsize=32:fontcolor=0xE0E0E0:x=1200:y=680:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:enable='between(t,0,17)',
         
         drawtext=text='36+ Languages Supported':fontsize=50:fontcolor=white:x=(w-text_w)/2:y=800:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Real-time • Accurate • Fast':fontsize=40:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=860:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         fade=in:st=0:d=0.5,fade=out:st=16.5:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/languages.mp4

# Create CTA section (5 seconds) - no contact info
echo "📱 Creating call-to-action section (5s)..."
ffmpeg -y -f lavfi -i "color=c=0x4B3FD8:size=${WIDTH}x${HEIGHT}:duration=5:rate=${FPS}" \
    -vf "drawtext=text='Get LinguaLink':fontsize=90:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-150:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Download Today':fontsize=70:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2-60:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Available on Google Play':fontsize=60:fontcolor=0x6C63FF:x=(w-text_w)/2:y=(h-text_h)/2+60:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:box=1:boxcolor=white@0.95:boxborderw=20,
         drawtext=text='Connect Globally in Real-Time':fontsize=40:fontcolor=0xD0D0D0:x=(w-text_w)/2:y=(h-text_h)/2+180:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         fade=in:st=0:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/cta.mp4

# Check that all segments were created
if [ ! -f "temp/thumbnail.mp4" ] || [ ! -f "temp/languages.mp4" ] || [ ! -f "temp/cta.mp4" ]; then
    echo "❌ Error: Not all video segments were created"
    ls -la temp/
    exit 1
fi

# Concatenate all sections into final video
echo "🔗 Combining sections into final video..."
echo "file 'thumbnail.mp4'" > temp/filelist.txt
echo "file 'languages.mp4'" >> temp/filelist.txt
echo "file 'cta.mp4'" >> temp/filelist.txt

ffmpeg -y -f concat -safe 0 -i temp/filelist.txt -c copy output/lingualink-ad-30sec.mp4

# Clean up temporary files
rm -rf temp/

echo ""
echo "✅ 30-Second LinguaLink ad generation complete!"
echo "📁 Output: output/lingualink-ad-30sec.mp4"
echo "⏱️ Duration: 30 seconds"
echo "🎯 Resolution: 1920x1080 (1080p)"
echo "🎨 Features: Thumbnail screen + 12 languages + No contact info"
echo "📤 Ready for YouTube upload!"

# Show file info
if [ -f "output/lingualink-ad-30sec.mp4" ]; then
    echo ""
    echo "📊 File Information:"
    ls -lh output/lingualink-ad-30sec.mp4
    
    echo ""
    echo "🔍 Video Details:"
    ffprobe -v quiet -show_entries format=duration:stream=width,height,codec_name output/lingualink-ad-30sec.mp4
else
    echo "❌ Error: 30-second video file was not created"
fi