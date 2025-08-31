#!/bin/bash

echo "🎬 Creating Voice Magic LinguaLink Ad..."

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
echo "   Focus: Speak in one language, hear voice spoken in another!"
echo ""

# Create thumbnail screen section (8 seconds)
echo "🖼️ Creating thumbnail screen section (8s)..."
ffmpeg -y -f lavfi -i "color=c=0x6C63FF:size=${WIDTH}x${HEIGHT}:duration=8:rate=${FPS}" \
    -vf "drawtext=text='LinguaLink':fontsize=140:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-150:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Speak in one language':fontsize=55:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-50:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='hear voice spoken in another!':fontsize=55:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2+10:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='🎤':fontsize=120:fontcolor=white:x=(w-text_w)/2-200:y=(h-text_h)/2+100:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='→':fontsize=80:fontcolor=white:x=(w-text_w)/2-50:y=(h-text_w)/2+120:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='🔊':fontsize=120:fontcolor=white:x=(w-text_w)/2+100:y=(h-text_h)/2+100:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Voice to Voice Translation Magic':fontsize=40:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2+280:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         fade=in:st=0:d=0.5,fade=out:st=7.5:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/thumbnail.mp4

# Create voice magic showcase section (17 seconds)
echo "🎙️ Creating voice magic showcase section (17s)..."
ffmpeg -y -f lavfi -i "color=c=0x5B4FE8:size=${WIDTH}x${HEIGHT}:duration=17:rate=${FPS}" \
    -vf "drawtext=text='The Magic of Voice Translation':fontsize=70:fontcolor=white:x=(w-text_w)/2:y=100:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         
         drawtext=text='STEP 1: SPEAK':fontsize=55:fontcolor=white:x=150:y=220:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Say anything in your language':fontsize=38:fontcolor=0xE0E0E0:x=150:y=270:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='🎤':fontsize=80:fontcolor=white:x=50:y=220:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='STEP 2: HEAR':fontsize=55:fontcolor=white:x=150:y=380:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Listen to voice spoken in any language':fontsize=38:fontcolor=0xE0E0E0:x=150:y=430:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='🔊':fontsize=80:fontcolor=white:x=50:y=380:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='LIVE DEMO:':fontsize=45:fontcolor=white:x=1000:y=250:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='🎤 ENGLISH':fontsize=35:fontcolor=0xC0C0C0:x=1000:y=300:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Hello how are you today?':fontsize=42:fontcolor=white:x=1000:y=340:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=12,
         
         drawtext=text='🔊 SPANISH VOICE':fontsize=35:fontcolor=0xC0C0C0:x=1000:y=420:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Hola como estas hoy?':fontsize=42:fontcolor=white:x=1000:y=460:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=12,
         
         drawtext=text='NO TYPING • NO READING':fontsize=50:fontcolor=white:x=(w-text_w)/2:y=580:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Just speak and listen!':fontsize=45:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=640:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='36+ Languages • Natural Voices • Real-time':fontsize=40:fontcolor=white:x=(w-text_w)/2:y=720:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         fade=in:st=0:d=0.5,fade=out:st=16.5:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/voice.mp4

# Create CTA section (5 seconds)
echo "📱 Creating call-to-action section (5s)..."
ffmpeg -y -f lavfi -i "color=c=0x4B3FD8:size=${WIDTH}x${HEIGHT}:duration=5:rate=${FPS}" \
    -vf "drawtext=text='Experience the Magic':fontsize=80:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-150:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Download LinguaLink':fontsize=70:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2-60:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Available on Google Play':fontsize=60:fontcolor=0x6C63FF:x=(w-text_w)/2:y=(h-text_h)/2+60:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:box=1:boxcolor=white@0.95:boxborderw=20,
         drawtext=text='Speak One Language • Hear Another':fontsize=40:fontcolor=0xD0D0D0:x=(w-text_w)/2:y=(h-text_h)/2+180:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         fade=in:st=0:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/cta.mp4

# Check that all segments were created
if [ ! -f "temp/thumbnail.mp4" ] || [ ! -f "temp/voice.mp4" ] || [ ! -f "temp/cta.mp4" ]; then
    echo "❌ Error: Not all video segments were created"
    ls -la temp/
    exit 1
fi

# Concatenate all sections into final video
echo "🔗 Combining sections into final video..."
echo "file 'thumbnail.mp4'" > temp/filelist.txt
echo "file 'voice.mp4'" >> temp/filelist.txt
echo "file 'cta.mp4'" >> temp/filelist.txt

ffmpeg -y -f concat -safe 0 -i temp/filelist.txt -c copy output/lingualink-ad-voice-magic.mp4

# Clean up temporary files
rm -rf temp/

echo ""
echo "✅ Voice Magic LinguaLink ad generation complete!"
echo "📁 Output: output/lingualink-ad-voice-magic.mp4"
echo "⏱️ Duration: 30 seconds"
echo "🎯 Resolution: 1920x1080 (1080p)"
echo "✨ Message: Speak in one language, hear voice spoken in another!"
echo "📤 Ready for YouTube upload!"

# Show file info
if [ -f "output/lingualink-ad-voice-magic.mp4" ]; then
    echo ""
    echo "📊 File Information:"
    ls -lh output/lingualink-ad-voice-magic.mp4
    
    echo ""
    echo "🔍 Video Details:"
    ffprobe -v quiet -show_entries format=duration:stream=width,height,codec_name output/lingualink-ad-voice-magic.mp4
else
    echo "❌ Error: Voice magic video file was not created"
fi