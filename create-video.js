const puppeteer = require('puppeteer');
const fs = require('fs');
const path = require('path');
const { exec } = require('child_process');

class LinguaLinkVideoCreator {
    constructor() {
        this.frameRate = 30; // 30 FPS
        this.duration = 90; // 90 seconds
        this.totalFrames = this.frameRate * this.duration; // 2700 frames
        this.outputDir = 'frames';
        this.browser = null;
        this.page = null;
    }

    async init() {
        console.log('🎬 Initializing LinguaLink Video Creator...');
        
        // Create frames directory
        if (!fs.existsSync(this.outputDir)) {
            fs.mkdirSync(this.outputDir);
        }

        // Launch browser
        this.browser = await puppeteer.launch({
            headless: 'new',
            args: [
                '--no-sandbox',
                '--disable-setuid-sandbox',
                '--disable-dev-shm-usage',
                '--disable-gpu',
                '--window-size=1920,1080'
            ]
        });

        this.page = await this.browser.newPage();
        await this.page.setViewport({ width: 1920, height: 1080 });
        
        console.log('✅ Browser initialized');
    }

    async captureFrames() {
        console.log('📹 Starting frame capture...');
        console.log(`📊 Total frames: ${this.totalFrames} (${this.duration}s @ ${this.frameRate}fps)`);

        // Load the ad page
        await this.page.goto('http://localhost:3000', { 
            waitUntil: 'networkidle0',
            timeout: 30000 
        });

        // Wait for animations to be ready
        await this.page.waitForTimeout(2000);

        // Inject restart function
        await this.page.evaluate(() => {
            window.restartAd = () => {
                location.reload();
            };
        });

        // Start capturing frames
        const frameInterval = 1000 / this.frameRate; // ms per frame
        
        for (let frame = 0; frame < this.totalFrames; frame++) {
            const timestamp = frame * frameInterval;
            
            // Restart ad if this is frame 0
            if (frame === 0) {
                await this.page.reload({ waitUntil: 'networkidle0' });
                await this.page.waitForTimeout(1000);
            }
            
            // Take screenshot
            const framePath = path.join(this.outputDir, `frame_${frame.toString().padStart(6, '0')}.png`);
            await this.page.screenshot({
                path: framePath,
                fullPage: false,
                clip: { x: 0, y: 0, width: 1920, height: 1080 }
            });

            // Progress indicator
            if (frame % 90 === 0) { // Every 3 seconds
                const progress = ((frame / this.totalFrames) * 100).toFixed(1);
                console.log(`📸 Frame ${frame}/${this.totalFrames} (${progress}%)`);
            }

            // Small delay between frames
            await this.page.waitForTimeout(frameInterval);
        }

        console.log('✅ Frame capture complete!');
    }

    async createVideo() {
        console.log('🎞️ Creating MP4 video with FFmpeg...');

        const inputPattern = path.join(this.outputDir, 'frame_%06d.png');
        const outputFile = 'lingualink-ad.mp4';

        const ffmpegCommand = [
            'ffmpeg',
            '-y', // Overwrite output file
            '-framerate', this.frameRate.toString(),
            '-i', inputPattern,
            '-c:v', 'libx264',
            '-pix_fmt', 'yuv420p',
            '-preset', 'medium',
            '-crf', '23',
            '-t', this.duration.toString(),
            outputFile
        ].join(' ');

        return new Promise((resolve, reject) => {
            console.log(`⚙️ Running: ${ffmpegCommand}`);
            
            exec(ffmpegCommand, (error, stdout, stderr) => {
                if (error) {
                    console.error('❌ FFmpeg error:', error);
                    reject(error);
                } else {
                    console.log('✅ Video created successfully!');
                    console.log(`📁 Output: ${outputFile}`);
                    resolve(outputFile);
                }
            });
        });
    }

    async cleanup() {
        console.log('🧹 Cleaning up...');
        
        // Close browser
        if (this.browser) {
            await this.browser.close();
        }

        // Optional: Remove frame files to save space
        // Uncomment if you want to delete frames after video creation
        /*
        try {
            const files = fs.readdirSync(this.outputDir);
            for (const file of files) {
                fs.unlinkSync(path.join(this.outputDir, file));
            }
            fs.rmdirSync(this.outputDir);
            console.log('🗑️ Frame files cleaned up');
        } catch (err) {
            console.log('⚠️ Could not clean up frame files:', err.message);
        }
        */
    }

    async createMP4() {
        try {
            await this.init();
            await this.captureFrames();
            await this.createVideo();
            await this.cleanup();
            
            console.log('');
            console.log('🎉 SUCCESS! Your LinguaLink ad MP4 is ready!');
            console.log('📱 File: lingualink-ad.mp4');
            console.log('⏱️ Duration: 90 seconds');
            console.log('🎯 Resolution: 1920x1080 (1080p)');
            console.log('📤 Ready for YouTube upload!');
            
        } catch (error) {
            console.error('❌ Error creating video:', error);
            await this.cleanup();
        }
    }
}

// Export for use or run directly
if (require.main === module) {
    const creator = new LinguaLinkVideoCreator();
    creator.createMP4();
}

module.exports = LinguaLinkVideoCreator;