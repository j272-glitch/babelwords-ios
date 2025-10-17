
import { App } from '@capacitor/app';
import { useEffect } from 'react';

export function useDeepLinkHandler() {
  useEffect(() => {
    // Handle app opened via deep link
    App.addListener('appUrlOpen', (data: any) => {
      console.log('App opened with URL:', data.url);
      
      // Parse the URL
      const url = new URL(data.url);
      const path = url.pathname;
      
      // Navigate to the appropriate route
      if (path) {
        // Use your router to navigate
        window.location.pathname = path;
      }
    });

    // Cleanup listener on unmount
    return () => {
      App.removeAllListeners();
    };
  }, []);
}

// Export standalone function for non-React usage
export async function handleDeepLink() {
  const result = await App.getLaunchUrl();
  if (result?.url) {
    console.log('App launched with URL:', result.url);
    const url = new URL(result.url);
    const path = url.pathname;
    if (path) {
      window.location.pathname = path;
    }
  }
}
