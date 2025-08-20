#!/bin/bash

# Fix Missing Gradle Wrapper Files
# This script fixes the missing gradle-wrapper.jar issue

echo "========================================="
echo "     FIXING GRADLE WRAPPER"
echo "========================================="
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

print_status() { echo -e "${GREEN}✓${NC} $1"; }
print_error() { echo -e "${RED}✗${NC} $1"; }
print_warning() { echo -e "${YELLOW}⚠${NC} $1"; }

# Check if gradle wrapper jar exists
if [ -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    print_status "Gradle wrapper JAR already exists"

    # Check if it's valid
    if java -jar gradle/wrapper/gradle-wrapper.jar --version 2>/dev/null; then
        print_status "Gradle wrapper JAR is valid"
    else
        print_warning "Gradle wrapper JAR exists but may be corrupted"
        read -p "Do you want to reinstall it? (y/n): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 0
        fi
    fi
else
    print_error "Gradle wrapper JAR is missing!"
fi

echo ""
echo "Installing Gradle 8.0.2 wrapper..."

# Create gradle directories
mkdir -p gradle/wrapper

# Method 1: Use gradle command if available
if command -v gradle &> /dev/null; then
    print_status "Gradle command found, using it to install wrapper"
    gradle wrapper --gradle-version 8.0.2 --distribution-type all
    print_status "Wrapper installed using gradle command"
else
    # Method 2: Download from Gradle distribution
    print_warning "Gradle command not found, downloading wrapper manually"

    # Download Gradle 8.0.2
    echo "Downloading Gradle 8.0.2..."
    wget -q --show-progress https://services.gradle.org/distributions/gradle-8.0.2-bin.zip -O gradle-temp.zip

    if [ ! -f "gradle-temp.zip" ]; then
        print_error "Failed to download Gradle"
        exit 1
    fi

    # Extract
    echo "Extracting..."
    unzip -q gradle-temp.zip

    # Copy wrapper jar
    echo "Installing wrapper JAR..."
    if [ -f "gradle-8.0.2/lib/gradle-wrapper-8.0.2.jar" ]; then
        cp gradle-8.0.2/lib/gradle-wrapper-8.0.2.jar gradle/wrapper/gradle-wrapper.jar
    elif [ -f "gradle-8.0.2/lib/gradle-wrapper.jar" ]; then
        cp gradle-8.0.2/lib/gradle-wrapper.jar gradle/wrapper/gradle-wrapper.jar
    else
        # Find any gradle-wrapper jar
        WRAPPER_JAR=$(find gradle-8.0.2 -name "gradle-wrapper*.jar" | head -1)
        if [ -n "$WRAPPER_JAR" ]; then
            cp "$WRAPPER_JAR" gradle/wrapper/gradle-wrapper.jar
        else
            print_error "Could not find gradle-wrapper.jar in distribution"
            exit 1
        fi
    fi

    # Clean up
    rm -rf gradle-8.0.2 gradle-temp.zip
    print_status "Wrapper JAR installed"
fi

# Create gradle-wrapper.properties
echo "Creating gradle-wrapper.properties..."
cat > gradle/wrapper/gradle-wrapper.properties << 'EOF'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.0.2-all.zip
networkTimeout=30000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
EOF
print_status "gradle-wrapper.properties created"

# Create gradlew if missing
if [ ! -f "gradlew" ]; then
    echo "Creating gradlew script..."

    # Download the official gradlew script
    wget -q https://raw.githubusercontent.com/gradle/gradle/v8.0.2/gradlew -O gradlew

    if [ ! -f "gradlew" ]; then
        # Fallback: Create a minimal gradlew script
        cat > gradlew << 'EOF'
#!/usr/bin/env sh

##############################################################################
##
##  Gradle start up script for UN*X
##
##############################################################################

# Attempt to set APP_HOME
# Resolve links: $0 may be a link
PRG="$0"
# Need this for relative symlinks.
while [ -h "$PRG" ] ; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=`dirname "$PRG"`"/$link"
    fi
done
SAVED="`pwd`"
cd "`dirname \"$PRG\"`/" >/dev/null
APP_HOME="`pwd -P`"
cd "$SAVED" >/dev/null

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD="maximum"

# OS specific support (must be 'true' or 'false').
cygwin=false
msys=false
darwin=false
nonstop=false
case "`uname`" in
  CYGWIN* )
    cygwin=true
    ;;
  Darwin* )
    darwin=true
    ;;
  MINGW* )
    msys=true
    ;;
  NONSTOP* )
    nonstop=true
    ;;
esac

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # IBM's JDK on AIX uses strange locations for the executables
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME"
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH."
fi

# Increase the maximum file descriptors if we can.
if [ "$cygwin" = "false" -a "$darwin" = "false" -a "$nonstop" = "false" ] ; then
    MAX_FD_LIMIT=`ulimit -H -n`
    if [ $? -eq 0 ] ; then
        if [ "$MAX_F