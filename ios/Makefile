.PHONY: setup generate pods build test clean archive

setup: generate pods
	@echo "Open LinguaVibe.xcworkspace in Xcode to build and run."

generate:
	@if command -v xcodegen >/dev/null 2>&1; then \
		xcodegen generate; \
	else \
		echo "xcodegen not found; install with: brew install xcodegen"; \
		exit 1; \
	fi

pods:
	pod install --repo-update

build:
	xcodebuild build \
		-workspace LinguaVibe.xcworkspace \
		-scheme LinguaVibe \
		-destination 'platform=iOS Simulator,name=iPhone 15,OS=latest' \
		CODE_SIGN_IDENTITY="" \
		CODE_SIGNING_REQUIRED=NO

test:
	xcodebuild test \
		-workspace LinguaVibe.xcworkspace \
		-scheme LinguaVibe \
		-destination 'platform=iOS Simulator,name=iPhone 15,OS=latest' \
		CODE_SIGN_IDENTITY="" \
		CODE_SIGNING_REQUIRED=NO

archive:
	xcodebuild archive \
		-workspace LinguaVibe.xcworkspace \
		-scheme LinguaVibe \
		-destination 'generic/platform=iOS' \
		-archivePath build/LinguaVibe.xcarchive

clean:
	rm -rf build DerivedData
