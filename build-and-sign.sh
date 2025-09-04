#!/bin/bash

# Micronaut RSQL Predicate - Build and Sign Script
# This script builds all artifacts and signs them for Maven Central upload

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
GPG_KEY_ID="9F19F738897EB199"
ARTIFACT_NAME="micronaut-rsql-predicate"
VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
TARGET_DIR="target"

echo -e "${BLUE}🚀 Micronaut RSQL Predicate - Build and Sign Script${NC}"
echo -e "${BLUE}================================================${NC}"
echo -e "Version: ${YELLOW}${VERSION}${NC}"
echo -e "GPG Key: ${YELLOW}${GPG_KEY_ID}${NC}"
echo ""

# Function to print status
print_status() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

# Check prerequisites
echo -e "${BLUE}🔍 Checking prerequisites...${NC}"

# Check if Maven is available
if ! command -v mvn &> /dev/null; then
    print_error "Maven is not installed or not in PATH"
    exit 1
fi
print_status "Maven is available"

# Check if GPG is available
if ! command -v gpg &> /dev/null; then
    print_error "GPG is not installed or not in PATH"
    exit 1
fi
print_status "GPG is available"

# Check if GPG key exists
if ! gpg --list-secret-keys --keyid-format LONG | grep -q "$GPG_KEY_ID"; then
    print_error "GPG key $GPG_KEY_ID not found"
    print_info "Available keys:"
    gpg --list-secret-keys --keyid-format LONG
    exit 1
fi
print_status "GPG key $GPG_KEY_ID is available"

echo ""

# Clean and build
echo -e "${BLUE}🧹 Cleaning previous build...${NC}"
mvn clean
print_status "Clean completed"

echo -e "${BLUE}🔨 Building artifacts...${NC}"
mvn package
print_status "Build completed"

# Generate effective POM
echo -e "${BLUE}📄 Generating effective POM...${NC}"
mvn help:effective-pom -Doutput="$TARGET_DIR/pom.xml" -q
print_status "Effective POM generated"

# List generated artifacts
echo -e "${BLUE}📦 Generated artifacts:${NC}"
ls -la "$TARGET_DIR"/*.jar "$TARGET_DIR"/pom.xml 2>/dev/null | while read line; do
    echo -e "  ${GREEN}$line${NC}"
done

echo ""

# Sign artifacts
echo -e "${BLUE}🔐 Signing artifacts with GPG...${NC}"

# Function to sign a file
sign_file() {
    local file="$1"
    local signature_file="${file}.asc"
    
    if [[ -f "$file" ]]; then
        echo -e "  Signing: ${YELLOW}$(basename "$file")${NC}"
        gpg --armor --detach-sign --sign-with "$GPG_KEY_ID" "$file"
        if [[ -f "$signature_file" ]]; then
            print_status "Signed: $(basename "$file")"
        else
            print_error "Failed to sign: $(basename "$file")"
            exit 1
        fi
    else
        print_error "File not found: $file"
        exit 1
    fi
}

# Sign all artifacts
sign_file "$TARGET_DIR/$ARTIFACT_NAME-$VERSION.jar"
sign_file "$TARGET_DIR/$ARTIFACT_NAME-$VERSION-sources.jar"
sign_file "$TARGET_DIR/$ARTIFACT_NAME-$VERSION-javadoc.jar"
sign_file "$TARGET_DIR/pom.xml"

echo ""

# List all files ready for upload
echo -e "${BLUE}📋 Files ready for Maven Central upload:${NC}"
echo -e "${BLUE}==========================================${NC}"

upload_files=(
    "$TARGET_DIR/$ARTIFACT_NAME-$VERSION.jar"
    "$TARGET_DIR/$ARTIFACT_NAME-$VERSION.jar.asc"
    "$TARGET_DIR/$ARTIFACT_NAME-$VERSION-sources.jar"
    "$TARGET_DIR/$ARTIFACT_NAME-$VERSION-sources.jar.asc"
    "$TARGET_DIR/$ARTIFACT_NAME-$VERSION-javadoc.jar"
    "$TARGET_DIR/$ARTIFACT_NAME-$VERSION-javadoc.jar.asc"
    "$TARGET_DIR/pom.xml"
    "$TARGET_DIR/pom.xml.asc"
)

for file in "${upload_files[@]}"; do
    if [[ -f "$file" ]]; then
        size=$(ls -lh "$file" | awk '{print $5}')
        echo -e "  ${GREEN}✓${NC} $(basename "$file") (${size})"
    else
        echo -e "  ${RED}✗${NC} $(basename "$file") - MISSING!"
    fi
done

echo ""

# Create Maven Central bundle structure
echo -e "${BLUE}📦 Creating Maven Central bundle...${NC}"

# Create bundle directory structure
BUNDLE_DIR="maven-central-bundle"
GROUP_PATH="com/charleslobo/micronaut/$ARTIFACT_NAME/$VERSION"
BUNDLE_PATH="$BUNDLE_DIR/$GROUP_PATH"

# Clean and create bundle directory
rm -rf "$BUNDLE_DIR"
mkdir -p "$BUNDLE_PATH"

# Copy files to bundle with proper naming (keep versioned names)
cp "$TARGET_DIR/$ARTIFACT_NAME-$VERSION.jar" "$BUNDLE_PATH/$ARTIFACT_NAME-$VERSION.jar"
cp "$TARGET_DIR/$ARTIFACT_NAME-$VERSION.jar.asc" "$BUNDLE_PATH/$ARTIFACT_NAME-$VERSION.jar.asc"
cp "$TARGET_DIR/$ARTIFACT_NAME-$VERSION-sources.jar" "$BUNDLE_PATH/$ARTIFACT_NAME-$VERSION-sources.jar"
cp "$TARGET_DIR/$ARTIFACT_NAME-$VERSION-sources.jar.asc" "$BUNDLE_PATH/$ARTIFACT_NAME-$VERSION-sources.jar.asc"
cp "$TARGET_DIR/$ARTIFACT_NAME-$VERSION-javadoc.jar" "$BUNDLE_PATH/$ARTIFACT_NAME-$VERSION-javadoc.jar"
cp "$TARGET_DIR/$ARTIFACT_NAME-$VERSION-javadoc.jar.asc" "$BUNDLE_PATH/$ARTIFACT_NAME-$VERSION-javadoc.jar.asc"
cp "$TARGET_DIR/pom.xml" "$BUNDLE_PATH/$ARTIFACT_NAME-$VERSION.pom"
cp "$TARGET_DIR/pom.xml.asc" "$BUNDLE_PATH/$ARTIFACT_NAME-$VERSION.pom.asc"

# Generate MD5 and SHA1 checksums for all files
echo -e "${BLUE}🔐 Generating checksums...${NC}"
for file in "$BUNDLE_PATH"/*.{jar,pom}; do
    if [[ -f "$file" ]]; then
        filename=$(basename "$file")
        echo -e "  Generating checksums for: ${YELLOW}$filename${NC}"
        
        # Generate MD5 checksum
        md5sum "$file" | cut -d' ' -f1 > "$file.md5"
        
        # Generate SHA1 checksum
        sha1sum "$file" | cut -d' ' -f1 > "$file.sha1"
    fi
done

# Create zip bundle
BUNDLE_ZIP="$TARGET_DIR/maven-central-bundle-$VERSION.zip"
cd "$BUNDLE_DIR"
zip -r "../$BUNDLE_ZIP" . -q
cd ..

# Clean up bundle directory
rm -rf "$BUNDLE_DIR"

print_status "Maven Central bundle created: $(basename "$BUNDLE_ZIP")"

# Show bundle contents
echo -e "${BLUE}📋 Bundle contents:${NC}"
unzip -l "$BUNDLE_ZIP" | grep -E "\.(jar|xml|asc)$" | while read line; do
    echo -e "  ${GREEN}$line${NC}"
done

echo ""

# Upload instructions
echo -e "${BLUE}🚀 Upload Instructions:${NC}"
echo -e "${BLUE}======================${NC}"
echo -e "1. Go to: ${YELLOW}https://s01.oss.sonatype.org/${NC}"
echo -e "2. Click: ${YELLOW}Staging Upload${NC}"
echo -e "3. Select: ${YELLOW}Bundle Upload${NC}"
echo -e "4. Upload the bundle: ${YELLOW}$BUNDLE_ZIP${NC}"
echo -e "5. Group ID: ${YELLOW}com.charleslobo.micronaut${NC}"
echo -e "6. Artifact ID: ${YELLOW}$ARTIFACT_NAME${NC}"
echo -e "7. Version: ${YELLOW}$VERSION${NC}"
echo ""
echo -e "${BLUE}📁 Alternative - Individual Files:${NC}"
echo -e "If bundle upload doesn't work, you can still upload individual files:"
echo -e "Upload all 8 files from: ${YELLOW}$TARGET_DIR/${NC}"
echo ""

# Check if GPG key is on keyservers
echo -e "${BLUE}🔑 GPG Key Verification:${NC}"
echo -e "${BLUE}=======================${NC}"
print_info "Checking if your GPG key is on public keyservers..."

if gpg --keyserver keyserver.ubuntu.com --search-keys "$GPG_KEY_ID" &>/dev/null; then
    print_status "GPG key is available on keyservers"
else
    print_warning "GPG key not found on keyservers"
    echo -e "  Upload your key with: ${YELLOW}gpg --keyserver keyserver.ubuntu.com --send-keys $GPG_KEY_ID${NC}"
fi

echo ""
echo -e "${GREEN}🎉 Build and signing completed successfully!${NC}"
echo -e "${GREEN}All artifacts are ready for Maven Central upload.${NC}"
