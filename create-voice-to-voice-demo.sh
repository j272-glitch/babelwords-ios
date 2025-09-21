#!/bin/bash

echo "🎤 Creating 30-Second Voice-to-Voice Translation Demo (No Conversation Mode)..."

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

echo "🎬 Creating voice-to-voice translation demo with 3 scenes (10s each):"
echo "   🎤 Scene 1: Voice Input Interface (10s)"
echo "   🔄 Scene 2: Translation Processing (10s)"  
echo "   🔊 Scene 3: Voice Output & Features (10s)"
echo "⏱️ Total: 30 seconds"
echo ""

# Scene 1: Voice Input Interface (10 seconds)
echo "🎤 Creating voice input scene (10s)..."
ffmpeg -y -f lavfi -i "color=c=${PURPLE_COLOR}:size=${WIDTH}x${HEIGHT}:duration=10:rate=${FPS}" \
    -vf "drawtext=text='LinguaLink':fontsize=80:fontcolor=white:x=(w-text_w)/2:y=100:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Voice-to-Voice Translation':fontsize=45:fontcolor=white:x=(w-text_w)/2:y=200:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawbox=x=(w-500)/2:y=280:w=500:h=400:color=${GRAY_COLOR}:t=4,
         drawbox=x=(w-480)/2:y=300:w=480:h=360:color=white@0.9:t=fill,
         drawtext=text='🎤':fontsize=100:fontcolor=${PURPLE_COLOR}:x=(w-text_w)/2:y=350:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='Tap to Speak':fontsize=35:fontcolor=${DARK_PURPLE}:x=(w-text_w)/2:y=480:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='FROM: English':fontsize=30:fontcolor=${PURPLE_COLOR}:x=(w-text_w)/2:y=540:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='TO: Spanish':fontsize=30:fontcolor=${PURPLE_COLOR}:x=(w-text_w)/2:y=580:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Speak naturally in your language':fontsize=35:fontcolor=white:x=(w-text_w)/2:y=720:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Get instant voice translation':fontsize=35:fontcolor=0xFFD700:x=(w-text_w)/2:y=770:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         fade=in:st=0:d=0.5,fade=out:st=9.5:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/input.mp4

# Scene 2: Translation Processing (10 seconds)
echo "🔄 Creating translation processing scene (10s)..."
ffmpeg -y -f lavfi -i "color=c=${PURPLE_COLOR}:size=${WIDTH}x${HEIGHT}:duration=10:rate=${FPS}" \
    -vf "drawtext=text='Voice Translation in Progress':fontsize=50:fontcolor=white:x=(w-text_w)/2:y=120:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawbox=x=(w-600)/2:y=200:w=600:h=200:color=white@0.9:t=fill,
         drawtext=text='🎤 Voice Input':fontsize=30:fontcolor=${DARK_PURPLE}:x=(w-text_w)/2:y=230:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='\"Hello, how are you today?\"':fontsize=28:fontcolor=black:x=(w-text_w)/2:y=270:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='🌊 Audio waveform detected':fontsize=22:fontcolor=${PURPLE_COLOR}:x=(w-text_w)/2:y=320:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Processing...':fontsize=50:fontcolor=white:x=(w-text_w)/2:y=430:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='🔄 Speech Recognition → Text':fontsize=25:fontcolor=white:x=(w-text_w)/2:y=500:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='🌐 Language Translation':fontsize=25:fontcolor=white:x=(w-text_w)/2:y=540:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='🗣️ Text → Speech Synthesis':fontsize=25:fontcolor=white:x=(w-text_w)/2:y=580:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Real-time device to device translation':fontsize=35:fontcolor=0xFFD700:x=(w-text_w)/2:y=650:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='using translated speech':fontsize=35:fontcolor=0xFFD700:x=(w-text_w)/2:y=690:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         fade=in:st=0:d=0.5,fade=out:st=9.5:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/processing.mp4

# Scene 3: Voice Output & Features (10 seconds)
echo "🔊 Creating voice output & features scene (10s)..."
ffmpeg -y -f lavfi -i "color=c=${PURPLE_COLOR}:size=${WIDTH}x${HEIGHT}:duration=10:rate=${FPS}" \
    -vf "drawtext=text='Translation Complete':fontsize=60:fontcolor=white:x=(w-text_w)/2:y=100:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawbox=x=(w-600)/2:y=180:w=600:h=180:color=white@0.9:t=fill,
         drawtext=text='🔊 Voice Output':fontsize=30:fontcolor=${DARK_PURPLE}:x=(w-text_w)/2:y=210:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='\"Hola, ¿cómo estás hoy?\"':fontsize=28:fontcolor=black:x=(w-text_w)/2:y=250:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='♪ Natural voice synthesis':fontsize=22:fontcolor=${PURPLE_COLOR}:x=(w-text_w)/2:y=300:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='✨ Features:':fontsize=40:fontcolor=white:x=(w-text_w)/2:y=400:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         drawtext=text='🌐 36+ Languages • 🔒 Private • ⚡ Instant':fontsize=30:fontcolor=white:x=(w-text_w)/2:y=460:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='🎤 Voice-to-Voice Translation':fontsize=30:fontcolor=white:x=(w-text_w)/2:y=510:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='📱 Download LinguaLink':fontsize=35:fontcolor=${PURPLE_COLOR}:x=(w-text_w)/2:y=580:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:box=1:boxcolor=white@0.9:boxborderw=10,
         drawtext=text='Available on Google Play':fontsize=25:fontcolor=white:x=(w-text_w)/2:y=630:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
         drawtext=text='Break language barriers with your voice':fontsize=30:fontcolor=0xFFD700:x=(w-text_w)/2:y=690:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf,
         fade=in:st=0:d=0.5,fade=out:st=9.5:d=0.5" \
    -c:v libx264 -pix_fmt yuv420p -preset fast temp/output.mp4

echo "🔍 Verifying video segments..."
for file in input.mp4 processing.mp4 output.mp4; do
    if [ -f "temp/$file" ] && [ -s "temp/$file" ]; then
        echo "✅ temp/$file ($(ls -lh temp/$file | awk '{print $5}'))"
    else
        echo "❌ Problem with temp/$file"
        exit 1
    fi
done

# Combine all segments
echo "🔗 Combining voice-to-voice translation scenes..."
echo "file 'input.mp4'" > temp/filelist.txt
echo "file 'processing.mp4'" >> temp/filelist.txt  
echo "file 'output.mp4'" >> temp/filelist.txt

ffmpeg -y -f concat -safe 0 -i temp/filelist.txt -c copy output/lingualink-voice-to-voice-demo.mp4

# Clean up
rm -rf temp/

echo ""
echo "✅ LinguaLink Voice-to-Voice Translation Demo Complete!"
echo "📁 Output: output/lingualink-voice-to-voice-demo.mp4"
echo "🎯 Video Structure (No Conversation Mode):"
echo "   🎤 Scene 1: Voice Input (10s) - Microphone interface, language selection"
echo "   🔄 Scene 2: Translation Processing (10s) - Real-time processing stages"
echo "   🔊 Scene 3: Voice Output (10s) - Translated speech output & features"
echo "⏱️ Total Duration: Exactly 30 seconds"
echo ""
echo "🚀 Key Focus Areas:"
echo "   ✅ Individual voice-to-voice translation (no conversation)"
echo "   ✅ 'Real-time device to device translation using translated speech'"
echo "   ✅ Processing pipeline visualization"
echo "   ✅ Natural speech synthesis"
echo "   ✅ 36+ language support"
echo "   ✅ Google Play Store availability"

if [ -f "output/lingualink-voice-to-voice-demo.mp4" ]; then
    echo ""
    echo "📊 Video Information:"
    ls -lh output/lingualink-voice-to-voice-demo.mp4
    ffprobe -v quiet -show_entries format=duration:stream=width,height,codec_name output/lingualink-voice-to-voice-demo.mp4 2>/dev/null || echo "Video created successfully"
else
    echo "❌ Error: Voice-to-voice demo video was not created"
fi