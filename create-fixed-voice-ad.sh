#!/bin/bash

echo "🎬 Creating Fixed Voice Magic LinguaLink Ad (No Emoji Issues)..."

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
echo "   Fix: Replace emoji with text-based icons"
echo ""

# Create thumbnail screen section (8 seconds)
echo "🖼️ Creating thumbnail screen section with text icons (8s)..."
ffmpeg -y -f lavfi -i "color=c=0x6C63FF:size=${WIDTH}x${HEIGHT}:duration=8:rate=${FPS}" \
    -vf "drawtext=text='LinguaLink':fontsize=140:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-150:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Speech-to-Speech Translation':fontsize=55:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-20:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='[MIC]':fontsize=80:fontcolor=white:x=(w-text_w)/2-150:y=(h-text_h)/2+80:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='->':fontsize=60:fontcolor=white:x=(w-text_w)/2-40:y=(h-text_h)/2+90:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='[SPEAKER]':fontsize=80:fontcolor=white:x=(w-text_w)/2+50:y=(h-text_h)/2+80:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Speak Naturally • Hear Naturally':fontsize=45:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2+250:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         fade=in:st=0:d=0.5,fade=out:st=7.5:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/thumbnail.mp4

# Create speech features showcase section (17 seconds)
echo "🎙️ Creating speech features showcase section (17s)..."
ffmpeg -y -f lavfi -i "color=c=0x5B4FE8:size=${WIDTH}x${HEIGHT}:duration=17:rate=${FPS}" \
    -vf "drawtext=text='AI Speech Recognition':fontsize=75:fontcolor=white:x=(w-text_w)/2:y=100:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Natural Voice Translation':fontsize=55:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=180:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='[MIC] SPEAK':fontsize=50:fontcolor=white:x=150:y=280:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Advanced speech recognition':fontsize=32:fontcolor=0xE0E0E0:x=150:y=330:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='understands natural language':fontsize=32:fontcolor=0xE0E0E0:x=150:y=365:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='[AI] TRANSLATE':fontsize=50:fontcolor=white:x=150:y=430:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Real-time AI processing':fontsize=32:fontcolor=0xE0E0E0:x=150:y=480:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='preserves meaning and context':fontsize=32:fontcolor=0xE0E0E0:x=150:y=515:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='[SPEAKER] LISTEN':fontsize=50:fontcolor=white:x=150:y=580:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Natural voice output':fontsize=32:fontcolor=0xE0E0E0:x=150:y=630:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='in target language':fontsize=32:fontcolor=0xE0E0E0:x=150:y=665:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='Hello how are you?':fontsize=38:fontcolor=white:x=950:y=300:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=12,
         drawtext=text='[MIC] ENGLISH VOICE INPUT':fontsize=24:fontcolor=0xC0C0C0:x=950:y=350:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='\\|/':fontsize=60:fontcolor=white:x=(w-text_w)/2:y=380:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         
         drawtext=text='Como estas?':fontsize=38:fontcolor=white:x=950:y=420:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:box=1:boxcolor=0x4B3FD8@0.8:boxborderw=12,
         drawtext=text='[SPEAKER] SPANISH VOICE OUTPUT':fontsize=24:fontcolor=0xC0C0C0:x=950:y=470:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         
         drawtext=text='Voice-to-Voice • 36+ Languages':fontsize=45:fontcolor=white:x=(w-text_w)/2:y=770:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Natural Speech Recognition & Output':fontsize=35:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=820:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         fade=in:st=0:d=0.5,fade=out:st=16.5:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/speech.mp4

# Create CTA section (5 seconds)
echo "📱 Creating call-to-action section (5s)..."
ffmpeg -y -f lavfi -i "color=c=0x4B3FD8:size=${WIDTH}x${HEIGHT}:duration=5:rate=${FPS}" \
    -vf "drawtext=text='Experience Voice Translation':fontsize=75:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-150:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Download LinguaLink':fontsize=70:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=(h-text_h)/2-60:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Available on Google Play':fontsize=60:fontcolor=0x6C63FF:x=(w-text_w)/2:y=(h-text_h)/2+60:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:box=1:boxcolor=white@0.95:boxborderw=20,
         drawtext=text='Speak • Translate • Listen':fontsize=40:fontcolor=0xD0D0D0:x=(w-text_w)/2:y=(h-text_h)/2+180:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         fade=in:st=0:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/cta.mp4

# Check that all segments were created
if [ ! -f "temp/thumbnail.mp4" ] || [ ! -f "temp/speech.mp4" ] || [ ! -f "temp/cta.mp4" ]; then
    echo "❌ Error: Not all video segments were created"
    ls -la temp/
    exit 1
fi

# Concatenate all sections into final video
echo "🔗 Combining sections into final video..."
echo "file 'thumbnail.mp4'" > temp/filelist.txt
echo "file 'speech.mp4'" >> temp/filelist.txt
echo "file 'cta.mp4'" >> temp/filelist.txt

ffmpeg -y -f concat -safe 0 -i temp/filelist.txt -c copy output/lingualink-ad-fixed.mp4

# Clean up temporary files
rm -rf temp/

echo ""
echo "✅ Fixed Voice Translation ad generation complete!"
echo "📁 Output: output/lingualink-ad-fixed.mp4"
echo "⏱️ Duration: 30 seconds"
echo "🎯 Resolution: 1920x1080 (1080p)"
echo "🔧 Fixed: Text-based icons instead of emoji"
echo "📤 Ready for YouTube upload!"

# Show file info
if [ -f "output/lingualink-ad-fixed.mp4" ]; then
    echo ""
    echo "📊 File Information:"
    ls -lh output/lingualink-ad-fixed.mp4
    
    echo ""
    echo "🔍 Video Details:"
    ffprobe -v quiet -show_entries format=duration:stream=width,height,codec_name output/lingualink-ad-fixed.mp4
else
    echo "❌ Error: Fixed video file was not created"
fi