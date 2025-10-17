import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.lingualink.test',
  appName: 'LinguaLink',
  webDir: 'dist',
  plugins: {
    App: {
      appUrlScheme: 'linguagt'
    }
  },
  server: {
    androidScheme: 'https',
    hostname: 'linguagt.com'
  }
};

export default config;