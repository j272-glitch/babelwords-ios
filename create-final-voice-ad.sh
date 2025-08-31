#!/bin/bash

echo "🎬 Creating Final Voice Magic LinguaLink Ad with Updated Messaging..."

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
echo "   Key Message: Speak in one language, hear voice spoken in another!"
echo ""

# Create thumbnail screen section (8 seconds)
echo "🖼️ Creating thumbnail screen section with key message (8s)..."
ffmpeg -y -f lavfi -i "color=c=0x6C63FF:size=${WIDTH}x${HEIGHT}:duration=8:rate=${FPS}" \
    -vf "drawtext=text='LinguaLink':fontsize=140:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-180:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Speak in one language':fontsize=60:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-80:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='hear voice spoken in another!':fontsize=60:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-20:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='🎤 → 🔊':fontsize=120:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+60:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='AI-Powered Voice Translation':fontsize=45:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2+250:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         fade=in:st=0:d=0.5,fade=out:st=7.5:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/thumbnail.mp4

# Create speech demonstration section (17 seconds)
echo "🎙️ Creating speech demonstration section (17s)..."
ffmpeg -y -f lavfi -i "color=c=0x5B4FE8:size=${WIDTH}x${HEIGHT}:duration=17:rate=${FPS}" \
    -vf "drawtext=text='Voice-to-Voice Translation':fontsize=70:fontcolor=white:x=(w-text_w)/2:y=100:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Natural Speech Recognition & Output':fontsize=50:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=160:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='🎤 SPEAK':fontsize=50:fontcolor=white:x=200:y=250:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Advanced AI understands':fontsize=32:fontcolor=0xE0E0E0:x=200:y=300:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='natural speech patterns':fontsize=32:fontcolor=0xE0E0E0:x=200:y=335:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='⚡ TRANSLATE':fontsize=50:fontcolor=white:x=200:y=400:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Real-time processing':fontsize=32:fontcolor=0xE0E0E0:x=200:y=450:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='preserves meaning':fontsize=32:fontcolor=0xE0E0E0:x=200:y=485:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='🔊 LISTEN':fontsize=50:fontcolor=white:x=200:y=550:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Natural voice output':fontsize=32:fontcolor=0xE0E0E0:x=200:y=600:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='in target language':fontsize=32:fontcolor=0xE0E0E0:x=200:y=635:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='DEMO:':fontsize=40:fontcolor=white:x=900:y=270:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Hello how are you?':fontsize=38:fontcolor=white:x=900:y=320:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=12,
         drawtext=text='English Voice Input':fontsize=28:fontcolor=0xC0C0C0:x=900:y=370:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='Como estas?':fontsize=38:fontcolor=white:x=900:y=440:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=12,
         drawtext=text='Spanish Voice Output':fontsize=28:fontcolor=0xC0C0C0:x=900:y=490:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='36+ Languages • Instant Results':fontsize=45:fontcolor=white:x=(w-text_w)/2:y=740:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         fade=in:st=0:d=0.5,fade=out:st=16.5:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/demo.mp4

# Create CTA section (5 seconds)
echo "📱 Creating call-to-action section (5s)..."
ffmpeg -y -f lavfi -i "color=c=0x4B3FD8:size=${WIDTH}x${HEIGHT}:duration=5:rate=${FPS}" \
    -vf "drawtext=text='Download LinguaLink':fontsize=90:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-150:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Experience Voice Magic':fontsize=60:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2-60:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Available on Google Play':fontsize=60:fontcolor=0x6C63FF:x=(w-text_w)/2:y=(h-text_h)/2+60:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:box=1:boxcolor=white@0.95:boxborderw=20,
         drawtext=text='Speak • Translate • Listen':fontsize=40:fontcolor=0xD0D0D0:x=(w-text_w)/2:y=(h-text_h)/2+180:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         fade=in:st=0:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/cta.mp4

# Check that all segments were created
if [ ! -f "temp/thumbnail.mp4" ] || [ ! -f "temp/demo.mp4" ] || [ ! -f "temp/cta.mp4" ]; then
    echo "❌ Error: Not all video segments were created"
    ls -la temp/
    exit 1
fi

# Concatenate all sections into final video
echo "🔗 Combining sections into final video..."
echo "file 'thumbnail.mp4'" > temp/filelist.txt
echo "file 'demo.mp4'" >> temp/filelist.txt
echo "file 'cta.mp4'" >> temp/filelist.txt

ffmpeg -y -f concat -safe 0 -i temp/filelist.txt -c copy output/lingualink-ad-final-voice.mp4

# Clean up temporary files
rm -rf temp/

echo ""
echo "✅ Final Voice Magic LinguaLink ad complete!"
echo "📁 Output: output/lingualink-ad-final-voice.mp4"
echo "⏱️ Duration: 30 seconds"
echo "🎯 Resolution: 1920x1080 (1080p)"
echo "✨ Message: Speak in one language, hear voice spoken in another!"
echo "📤 Perfect for marketing campaigns!"

# Show file info
if [ -f "output/lingualink-ad-final-voice.mp4" ]; then
    echo ""
    echo "📊 File Information:"
    ls -lh output/lingualink-ad-final-voice.mp4
    
    echo ""
    echo "🔍 Video Details:"
    ffprobe -v quiet -show_entries format=duration:stream=width,height,codec_name output/lingualink-ad-final-voice.mp4
else
    echo "❌ Error: Final voice video file was not created"
fi