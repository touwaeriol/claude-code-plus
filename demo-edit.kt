package com.claudecodeplus.demo

/**
 * Edit Tool Demo
 *
 * The Edit tool is used for precise string replacement in existing files.
 * It is the preferred tool for modifying code.
 */
class EditToolDemo {

    // Example 1: Simple variable rename
    fun simpleRename() {
        val previousName = "foo"
        val currentName = "bar"
        println("Renaming from $previousName to $currentName")
    }

    // Example 2: Modify function implementation
    fun calculateSum(a: Int, b: Int): Int {
        // Implementation completed
        return a + b
    }

    // Example 3: Add new functionality
    fun processData(data: String) {
        // Enhanced with validation and processing
        if (data.isBlank()) {
            println("Warning: Empty data received")
            return
        }
        val processed = data.trim().uppercase()
        println("Processed: $processed")
    }

    // Example 4: Update documentation
    /**
     * Doubles the input value.
     *
     * @param x the integer to be doubled
     * @return the input multiplied by 2
     */
    fun mysteryFunction(x: Int): Int {
        return x * 2
    }

    // Example 5: Fix a bug
    fun findMax(numbers: List<Int>): Int {
        require(numbers.isNotEmpty()) { "List cannot be empty" }
        var max = numbers.first()  // Fixed: Initialize with first element
        for (num in numbers) {
            if (num > max) {
                max = num
            }
        }
        return max
    }
}

// Use cases:
// 1. Rename variables/functions/classes
// 2. Modify implementation logic
// 3. Add/remove code
// 4. Update comments and documentation
// 5. Fix bugs
