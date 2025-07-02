#!/bin/bash

# Script to add @file:Suppress("DEPRECATION") to Kotlin files that don't have it

echo "Adding deprecation suppressions to Kotlin files..."

# Find all .kt files and add suppression if not already present
find . -name "*.kt" -path "*/src/main/*" -exec grep -L "@file:Suppress" {} \; | while read file; do
    # Check if file uses deprecated APIs
    if grep -q "Theme\|UI\|Transaction\|TransactionHistoryItem\|stringRes\|forward\|then2" "$file"; then
        echo "Adding suppression to: $file"
        # Add the suppression at the top of the file
        sed -i '1i@file:Suppress("DEPRECATION")\n' "$file"
    fi
done

echo "Deprecation suppressions added!" 