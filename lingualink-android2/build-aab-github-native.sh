#!/bin/bash

echo "🚀 Building AAB using GitHub Native Android Environment"
echo "======================================================"

# Check for required environment variables
if [ -z "$GITHUB_TOKEN" ]; then
    echo "❌ GITHUB_TOKEN not found in environment"
    echo "Please ensure GITHUB_TOKEN is set in Replit Secrets"
    exit 1
fi

# Configuration
REPO_OWNER="j272-glitch"
REPO_NAME="lingualink-android"
WORKFLOW_FILE="build-aab-native.yml"
BRANCH="main"

echo "🔧 Configuration:"
echo "Repository: $REPO_OWNER/$REPO_NAME"
echo "Workflow: $WORKFLOW_FILE"
echo "Branch: $BRANCH"
echo ""

# Function to trigger GitHub Actions workflow
trigger_workflow() {
    echo "🚀 Triggering GitHub Actions workflow..."
    
    # Create workflow dispatch payload
    PAYLOAD=$(cat <<EOF
{
  "ref": "$BRANCH",
  "inputs": {
    "build_type": "release"
  }
}
EOF
)

    # Trigger the workflow
    RESPONSE=$(curl -s -w "%{http_code}" \
        -X POST \
        -H "Authorization: Bearer $GITHUB_TOKEN" \
        -H "Accept: application/vnd.github.v3+json" \
        -H "Content-Type: application/json" \
        -d "$PAYLOAD" \
        "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/actions/workflows/$WORKFLOW_FILE/dispatches")
    
    HTTP_CODE="${RESPONSE: -3}"
    
    if [ "$HTTP_CODE" = "204" ]; then
        echo "✅ Workflow triggered successfully!"
        return 0
    else
        echo "❌ Failed to trigger workflow (HTTP: $HTTP_CODE)"
        echo "Response: ${RESPONSE%???}"
        return 1
    fi
}

# Function to get latest workflow run
get_latest_run() {
    echo "🔍 Getting latest workflow run..."
    
    RESPONSE=$(curl -s \
        -H "Authorization: Bearer $GITHUB_TOKEN" \
        -H "Accept: application/vnd.github.v3+json" \
        "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/actions/workflows/$WORKFLOW_FILE/runs?per_page=1")
    
    RUN_ID=$(echo "$RESPONSE" | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)
    RUN_STATUS=$(echo "$RESPONSE" | grep -o '"status":"[^"]*' | head -1 | cut -d'"' -f4)
    RUN_CONCLUSION=$(echo "$RESPONSE" | grep -o '"conclusion":"[^"]*' | head -1 | cut -d'"' -f4)
    
    echo "Run ID: $RUN_ID"
    echo "Status: $RUN_STATUS"
    echo "Conclusion: $RUN_CONCLUSION"
}

# Function to monitor workflow progress
monitor_workflow() {
    echo "📊 Monitoring workflow progress..."
    echo "You can also monitor at: https://github.com/$REPO_OWNER/$REPO_NAME/actions"
    echo ""
    
    local max_attempts=60  # 10 minutes (60 * 10 seconds)
    local attempt=0
    
    while [ $attempt -lt $max_attempts ]; do
        get_latest_run
        
        case "$RUN_STATUS" in
            "completed")
                echo ""
                if [ "$RUN_CONCLUSION" = "success" ]; then
                    echo "✅ Workflow completed successfully!"
                    get_artifacts
                    return 0
                else
                    echo "❌ Workflow failed with conclusion: $RUN_CONCLUSION"
                    return 1
                fi
                ;;
            "in_progress"|"queued")
                echo "⏳ Workflow is $RUN_STATUS... (attempt $((attempt + 1))/$max_attempts)"
                sleep 10
                ;;
            *)
                echo "❓ Unknown status: $RUN_STATUS"
                sleep 10
                ;;
        esac
        
        ((attempt++))
    done
    
    echo "⏰ Timeout reached. Check manually: https://github.com/$REPO_OWNER/$REPO_NAME/actions"
    return 1
}

# Function to get artifacts
get_artifacts() {
    echo "📦 Getting build artifacts..."
    
    ARTIFACTS_RESPONSE=$(curl -s \
        -H "Authorization: Bearer $GITHUB_TOKEN" \
        -H "Accept: application/vnd.github.v3+json" \
        "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/actions/runs/$RUN_ID/artifacts")
    
    echo "Available artifacts:"
    echo "$ARTIFACTS_RESPONSE" | grep -o '"name":"[^"]*' | cut -d'"' -f4 | while read artifact; do
        echo "  - $artifact"
    done
    
    echo ""
    echo "📁 Artifacts available at:"
    echo "   https://github.com/$REPO_OWNER/$REPO_NAME/actions/runs/$RUN_ID"
    echo ""
    echo "📱 Expected files:"
    echo "   - lingualink-native-android-v1.0.61.aab"
    echo "   - lingualink-native-android-v1.0.61.apk"
}

# Function to get latest release
get_latest_release() {
    echo "🏷️ Checking for latest release..."
    
    RELEASE_RESPONSE=$(curl -s \
        -H "Authorization: Bearer $GITHUB_TOKEN" \
        -H "Accept: application/vnd.github.v3+json" \
        "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest")
    
    RELEASE_TAG=$(echo "$RELEASE_RESPONSE" | grep -o '"tag_name":"[^"]*' | cut -d'"' -f4)
    
    if [ -n "$RELEASE_TAG" ]; then
        echo "Latest release: $RELEASE_TAG"
        echo "Download at: https://github.com/$REPO_OWNER/$REPO_NAME/releases/latest"
    else
        echo "No releases found yet"
    fi
}

# Main execution
echo "Starting AAB build process..."
echo ""

# Trigger the workflow
if trigger_workflow; then
    echo ""
    sleep 5  # Give GitHub a moment to process
    
    # Monitor the workflow
    if monitor_workflow; then
        echo ""
        get_latest_release
        echo ""
        echo "🎉 AAB build completed successfully!"
        echo "📥 Download your files from:"
        echo "   - Artifacts: https://github.com/$REPO_OWNER/$REPO_NAME/actions"
        echo "   - Releases: https://github.com/$REPO_OWNER/$REPO_NAME/releases"
    else
        echo ""
        echo "❌ Build process encountered issues"
        echo "🔍 Check the workflow logs at:"
        echo "   https://github.com/$REPO_OWNER/$REPO_NAME/actions"
    fi
else
    echo ""
    echo "❌ Failed to trigger workflow"
    exit 1
fi