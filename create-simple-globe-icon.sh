#!/bin/bash

echo "🌐 Creating Simple 1024x1024 Globe Icon..."

# Check if ImageMagick is available
if ! command -v convert &> /dev/null; then
    echo "Installing ImageMagick..."
    apt-get update && apt-get install -y imagemagick
fi

# Create the 1024x1024 wireframe globe icon
echo "📱 Creating wireframe globe icon using ImageMagick..."

convert -size 1024x1024 xc:"#6366F1" \
    -fill none -stroke white -strokewidth 8 \
    -draw "roundrectangle 0,0 1023,1023 180,180" \
    -fill none -stroke white -strokewidth 4 \
    -draw "circle 512,512 512,112" \
    -draw "line 112,512 912,512" \
    -draw "line 512,112 512,912" \
    -draw "line 182,512 842,512" \
    -draw "line 512,182 512,842" \
    -strokewidth 3 \
    -draw "ellipse 512,512 200,400 0,360" \
    -draw "ellipse 512,512 400,200 0,360" \
    -draw "ellipse 512,512 150,350 0,360" \
    -draw "ellipse 512,512 350,150 0,360" \
    -strokewidth 2 \
    -draw "circle 512,512 8,8" \
    -fill white -stroke none \
    -draw "polygon 482,502 492,512 482,522" \
    -draw "polygon 542,502 532,512 542,522" \
    output/lingualink-globe-icon-1024.png

# If ImageMagick fails, try ffmpeg simple approach
if [ ! -f "output/lingualink-globe-icon-1024.png" ] || [ ! -s "output/lingualink-globe-icon-1024.png" ]; then
    echo "🔄 Fallback: Creating with ffmpeg..."
    
    ffmpeg -y -f lavfi -i "color=c=0x6366F1:size=1024x1024:duration=0.1:rate=1" \
        -vf "drawtext=text='○':fontsize=400:fontcolor=white@0.9:x=(w-text_w)/2:y=(w-text_h)/2:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
             drawtext=text='│':fontsize=400:fontcolor=white@0.7:x=(w-text_w)/2:y=(w-text_h)/2:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
             drawtext=text='—':fontsize=320:fontcolor=white@0.7:x=(w-text_w)/2:y=(w-text_h)/2:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
             drawtext=text='│':fontsize=350:fontcolor=white@0.5:x=(w-text_w)/2-80:y=(w-text_h)/2:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
             drawtext=text='│':fontsize=350:fontcolor=white@0.5:x=(w-text_w)/2+80:y=(w-text_h)/2:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
             drawtext=text='—':fontsize=280:fontcolor=white@0.5:x=(w-text_w)/2:y=(w-text_h)/2-80:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
             drawtext=text='—':fontsize=280:fontcolor=white@0.5:x=(w-text_w)/2:y=(w-text_h)/2+80:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf,
             drawtext=text='●':fontsize=20:fontcolor=white:x=(w-text_w)/2:y=(w-text_h)/2:fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf" \
        -frames:v 1 -q:v 1 output/lingualink-globe-icon-1024.png
fi

echo ""
if [ -f "output/lingualink-globe-icon-1024.png" ] && [ -s "output/lingualink-globe-icon-1024.png" ]; then
    echo "✅ 1024x1024 LinguaLink Globe Icon created!"
    echo "📁 Output: output/lingualink-globe-icon-1024.png"
    echo "📊 File size: $(ls -lh output/lingualink-globe-icon-1024.png | awk '{print $5}')"
    echo ""
    echo "🎨 Design Features:"
    echo "   ✅ 1024x1024 pixels (app store ready)"
    echo "   ✅ Purple background (#6366F1)"
    echo "   ✅ White wireframe globe design"
    echo "   ✅ Grid lines and center point"
    echo "   ✅ Based on your wireframe globe model"
else
    echo "❌ Error: Globe icon creation failed"
fi