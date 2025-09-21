#!/bin/bash

echo "📱 Creating 30-Second LinguaLink App Demonstration Video..."

# Create output directory
mkdir -p output temp

# Video settings
WIDTH=1920
HEIGHT=1080
FPS=30
PURPLE_COLOR="0x5B4FE8"
DARK_PURPLE="0x4338CA"
WHITE_COLOR="white"
GRAY_COLOR="0xE5E7EB"

echo "🎬 Creating app demonstration with 3 scenes (10s each):"
echo "   📱 Scene 1: App Interface Overview (10s)"
echo "   🗣️ Scene 2: Voice Translation Demo (10s)"  
echo "   🔒 Scene 3: Privacy & Features (10s)"
echo "⏱️ Total: 30 seconds"
echo ""

# Scene 1: App Interface Overview (10 seconds)
echo "📱 Creating app interface scene (10s)..."
ffmpeg -y -f lavfi -i "color=c=${PURPLE_COLOR}:size=${WIDTH}x${HEIGHT}:duration=10:rate=${FPS}" \
    -vf "drawtext=text='LinguaLink':fontsize=80:fontcolor=white:x=(w-text_w)/2:y=100:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Real-Time Translation App':fontsize=40:fontcolor=white:x=(w-text_w)/2:y=200:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawbox=x=(w-400)/2:y=280:w=400:h=600:color=${GRAY_COLOR}:t=4,
         drawbox=x=(w-380)/2:y=300:w=380:h=560:color=white@0.9:t=fill,
         drawtext=text='🎤':fontsize=60:fontcolor=${PURPLE_COLOR}:x=(w-text_w)/2:y=350:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Tap to speak':fontsize=30:fontcolor=${DARK_PURPLE}:x=(w-text_w)/2:y=430:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='English → Spanish':fontsize=25:fontcolor=${PURPLE_COLOR}:x=(w-text_w)/2:y=500:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='🔄':fontsize=40:fontcolor=${PURPLE_COLOR}:x=(w-text_w)/2:y=540:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='36+ Languages Supported':fontsize=25:fontcolor=${DARK_PURPLE}:x=(w-text_w)/2:y=600:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='🔒 Private • 🚀 Fast • 📱 Mobile':fontsize=30:fontcolor=white:x=(w-text_w)/2:y=720:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         fade=in:st=0:d=0.5,fade=out:st=9.5:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/interface.mp4

# Scene 2: Voice Translation Demo (10 seconds)
echo "🗣️ Creating voice translation demo scene (10s)..."
ffmpeg -y -f lavfi -i "color=c=${PURPLE_COLOR}:size=${WIDTH}x${HEIGHT}:duration=10:rate=${FPS}" \
    -vf "drawtext=text='Voice Translation in Action':fontsize=50:fontcolor=white:x=(w-text_w)/2:y=120:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawbox=x=100:y=200:w=700:h=150:color=white@0.9:t=fill,
         drawtext=text='Person A (English)':fontsize=25:fontcolor=${DARK_PURPLE}:x=120:y=220:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='\"Hello, how are you?\"':fontsize=30:fontcolor=black:x=120:y=260:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='🎤':fontsize=40:fontcolor=${PURPLE_COLOR}:x=750:y=240:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='↓ Processing... ↓':fontsize=35:fontcolor=white:x=(w-text_w)/2:y=400:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawbox=x=1120:y=500:w=700:h=150:color=white@0.9:t=fill,
         drawtext=text='Person B (Spanish)':fontsize=25:fontcolor=${DARK_PURPLE}:x=1140:y=520:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='\"Hola, ¿cómo estás?\"':fontsize=30:fontcolor=black:x=1140:y=560:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='🔊':fontsize=40:fontcolor=${PURPLE_COLOR}:x=1070:y=540:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Real-time device to device conversation':fontsize=35:fontcolor=0xFFD700:x=(w-text_w)/2:y=700:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='using translated speech':fontsize=35:fontcolor=0xFFD700:x=(w-text_w)/2:y=750:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         fade=in:st=0:d=0.5,fade=out:st=9.5:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/demo.mp4

# Scene 3: Privacy & Features (10 seconds)
echo "🔒 Creating privacy & features scene (10s)..."
ffmpeg -y -f lavfi -i "color=c=${PURPLE_COLOR}:size=${WIDTH}x${HEIGHT}:duration=10:rate=${FPS}" \
    -vf "drawtext=text='LinguaLink Features':fontsize=60:fontcolor=white:x=(w-text_w)/2:y=100:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='🔒 End-to-End Encryption':fontsize=40:fontcolor=white:x=(w-text_w)/2:y=220:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Your conversations stay private':fontsize=30:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=270:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='🌐 36+ Languages':fontsize=40:fontcolor=white:x=(w-text_w)/2:y=340:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Connect with anyone, anywhere':fontsize=30:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=390:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='⚡ Real-Time Translation':fontsize=40:fontcolor=white:x=(w-text_w)/2:y=460:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Instant voice-to-voice communication':fontsize=30:fontcolor=0xE0E0E0:x=(w-text_w)/2:y=510:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='📱 Download on Google Play':fontsize=35:fontcolor=${PURPLE_COLOR}:x=(w-text_w)/2:y=600:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:box=1:boxcolor=white@0.9:boxborderw=10,
         drawtext=text='Available now - Free with ads':fontsize=25:fontcolor=white:x=(w-text_w)/2:y=660:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='LinguaLink - Break Language Barriers':fontsize=30:fontcolor=0xFFD700:x=(w-text_w)/2:y=720:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         fade=in:st=0:d=0.5,fade=out:st=9.5:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/features.mp4

echo "🔍 Verifying video segments..."
for file in interface.mp4 demo.mp4 features.mp4; do
    if [ -f "temp/$file" ] && [ -s "temp/$file" ]; then
        echo "✅ temp/$file ($(ls -lh temp/$file | awk '{print $5}'))"
    else
        echo "❌ Problem with temp/$file"
        exit 1
    fi
done

# Combine all segments
echo "🔗 Combining demonstration scenes..."
echo "file 'interface.mp4'" > temp/filelist.txt
echo "file 'demo.mp4'" >> temp/filelist.txt  
echo "file 'features.mp4'" >> temp/filelist.txt

ffmpeg -y -f concat -safe 0 -i temp/filelist.txt -c copy output/lingualink-app-demo.mp4

# Clean up
rm -rf temp/

echo ""
echo "✅ LinguaLink App Demonstration Video Complete!"
echo "📁 Output: output/lingualink-app-demo.mp4"
echo "🎯 Video Structure:"
echo "   📱 Scene 1: App Interface (10s) - UI overview with microphone and language selection"
echo "   🗣️ Scene 2: Translation Demo (10s) - Live conversation example"
echo "   🔒 Scene 3: Features & Privacy (10s) - End-to-end encryption, 36+ languages, Google Play"
echo "⏱️ Total Duration: Exactly 30 seconds"
echo ""
echo "🚀 Key Messages Included:"
echo "   ✅ 'Real-time device to device conversation using translated speech'"
echo "   ✅ End-to-end encryption & privacy"
echo "   ✅ 36+ language support"
echo "   ✅ Voice-to-voice translation demo"
echo "   ✅ Google Play Store availability"

if [ -f "output/lingualink-app-demo.mp4" ]; then
    echo ""
    echo "📊 Video Information:"
    ls -lh output/lingualink-app-demo.mp4
    ffprobe -v quiet -show_entries format=duration:stream=width,height,codec_name output/lingualink-app-demo.mp4 2>/dev/null || echo "Video created successfully"
else
    echo "❌ Error: Demo video was not created"
fi