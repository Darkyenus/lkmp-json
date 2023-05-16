package com.darkyen.json

class BitArray(initialLength:Int) {

    // Using int instead of long for performance in JS
    private var values = IntArray(((initialLength + 31) / 32).coerceAtLeast(8))

    operator fun get(index: Int): Boolean {
        if (index < 0) throw IndexOutOfBoundsException("negative index $index")
        val wordIndex = index ushr 5
        val values = values
        if (wordIndex >= values.size) return false
        val bitIndex = index and 31
        val word = values[wordIndex]
        val bit = 1 shl bitIndex
        return (word and bit) != 0
    }

    operator fun set(index: Int, value: Boolean) {
        if (value) set(index) else clear(index)
    }

    fun set(index: Int) {
        if (index < 0) throw IndexOutOfBoundsException("negative index $index")
        val wordIndex = index ushr 5
        var values = values
        if (wordIndex >= values.size) {
            values = values.copyOf(wordIndex + 8)
            this.values = values
        }
        val bitIndex = index and 31
        values[wordIndex] = values[wordIndex] or (1 shl bitIndex)
    }

    fun clear(index: Int) {
        if (index < 0) throw IndexOutOfBoundsException("negative index $index")
        val wordIndex = index ushr 5
        val values = values
        if (wordIndex >= values.size) {
            return // Already cleared
        }
        val bitIndex = index and 31
        values[wordIndex] = values[wordIndex] and (1 shl bitIndex).inv()
    }
}