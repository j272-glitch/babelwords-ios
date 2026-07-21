.PHONY: setup generate pods build test clean archive

setup: generate pods
	@echo "Open BabelWords.xcworkspace in Xcode to build and run."

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
		-workspace BabelWords.xcworkspace \
		-scheme BabelWords \
		-destination 'platform=iOS Simulator,name=iPhone 15,OS=latest' \
		CODE_SIGN_IDENTITY="" \
		CODE_SIGNING_REQUIRED=NO

test:
	xcodebuild test \
		-workspace BabelWords.xcworkspace \
		-scheme BabelWords \
		-destination 'platform=iOS Simulator,name=iPhone 15,OS=latest' \
		CODE_SIGN_IDENTITY="" \
		CODE_SIGNING_REQUIRED=NO

archive:
	xcodebuild archive \
		-workspace BabelWords.xcworkspace \
		-scheme BabelWords \
		-destination 'generic/platform=iOS' \
		-archivePath build/BabelWords.xcarchive

clean:
	rm -rf build DerivedData
