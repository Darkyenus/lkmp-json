package com.darkyen.json

class CharSequenceView(
    private val parent: CharSequence,
    private val offset: Int,
    override val length: Int
): CharSequence {
    override fun get(index: Int): Char = if (index in indices) parent[offset + index] else throw IndexOutOfBoundsException("$index !in [0, $length)")
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        if (startIndex !in indices || endIndex !in indices || startIndex > endIndex) throw IndexOutOfBoundsException("$startIndex, $endIndex !in [0, $length)")
        if (startIndex == endIndex) return ""
        return CharSequenceView(parent, offset + startIndex, endIndex - startIndex)
    }
    override fun equals(other: Any?): Boolean {
        if (other !is CharSequence) return false
        return this.contentEquals(other)
    }
    override fun hashCode(): Int {
        var h = 0
        for (i in offset until offset + length) {
            h = 31 * h + parent[i].code
        }
        return h
    }
    override fun toString(): String {
        return parent.substring(offset, offset + length)
    }
}

sealed class JsonValue : Iterable<JsonValue> {

    sealed class Collection: JsonValue()

    class Object(val fields: List<Pair<kotlin.String, JsonValue>>): Collection() {
        constructor(vararg values: Pair<kotlin.String, JsonValue>): this(values.toList())
        constructor(values: Map<kotlin.String, JsonValue>): this(values.toList())

        override fun asBoolean(): Boolean = fields.isNotEmpty()

        override val size: Int
            get() = fields.size

        override fun get(index: Int): JsonValue {
            return fields[index].second
        }

        override fun get(key: kotlin.String): JsonValue? {
            return fields.find { it.first == key }?.second
        }

        override fun appendJson(out: Appendable, indent: Int) {
            if (indent < 0) {
                // Compact format
                out.append('{')
                for ((i, field) in fields.withIndex()) {
                    if (i != 0) {
                        out.append(',')
                    }
                    out.appendJsonString(field.first)
                    out.append(":")
                    field.second.appendJson(out, indent)
                }
                out.append('}')
            } else {
                out.append('{')
                if (fields.isEmpty()) {
                    out.append('}')
                } else {
                    val maxFieldLen = fields.maxOf { it.first.length }
                    val valueIndent = indent + 1
                    for ((i, value) in fields.withIndex()) {
                        if (i != 0) {
                            out.append(',')
                        }
                        out.append('\n').appendIndent(valueIndent)
                        repeat(maxFieldLen - value.first.length) {
                            out.append(' ')
                        }
                        out.appendJsonString(value.first).append(": ")
                        value.second.appendJson(out, valueIndent)
                    }
                    out.append('\n').appendIndent(indent).append('}')
                }
            }
        }

        override fun iterator(): Iterator<JsonValue> = object : Iterator<JsonValue> {
            val iterator = fields.iterator()
            override fun hasNext(): Boolean = iterator.hasNext()
            override fun next(): JsonValue = iterator.next().second
        }

        override fun equals(other: Any?): Boolean {
            if (other !is Object) return false
            return fields == other.fields
        }

        override fun hashCode(): Int {
            var h = 0
            for ((name, value) in fields) {
                h = (31 * h + name.hashCode()) * 31 + value.hashCode()
            }
            return h
        }
    }

    class Array(val values: List<JsonValue>) : JsonValue() {
        constructor(vararg values: JsonValue): this(values.toList())

        override fun asBoolean(): Boolean = values.isNotEmpty()

        override val size: Int
            get() = values.size

        override fun get(index: Int): JsonValue = values[index]

        override fun appendJson(out: Appendable, indent: Int) {
            if (indent < 0) {
                // Compact format
                out.append('[')
                for ((i, value) in values.withIndex()) {
                    if (i != 0) {
                        out.append(',')
                    }
                    value.appendJson(out, indent)
                }
                out.append(']')
            } else {
                out.append('[')
                if (values.isEmpty()) {
                    out.append(']')
                } else {
                    val valueIndent = indent + 1
                    for ((i, value) in values.withIndex()) {
                        if (i != 0) {
                            out.append(',')
                        }
                        out.append('\n').appendIndent(valueIndent)
                        value.appendJson(out, valueIndent)
                    }
                    out.append('\n').appendIndent(indent).append(']')
                }
            }
        }

        override fun iterator(): Iterator<JsonValue> = values.iterator()

        override fun equals(other: Any?): Boolean {
            if (other !is Array) return false
            return values == other.values
        }

        override fun hashCode(): Int {
            var h = 0
            for (value in values) {
                h = 31 * h + value.hashCode()
            }
            return h
        }
    }

    /**
     * Represents JSON string literal
     */
    class String internal constructor(
        /** Value with necessary parts correctly escaped */
        private var escapedValue: CharSequence?,
        /** The actual value */
        private var unescapedValue: CharSequence?,
    ): JsonValue() {

        val value: kotlin.String
            get() {
                var uv = unescapedValue
                if (uv == null) {
                    uv = jsonUnescape(escapedValue!!).toString()
                    unescapedValue = uv
                } else if (uv !is kotlin.String) {
                    uv = uv.toString()
                }
                return uv
            }

        constructor(value: CharSequence): this(null, value)

        override fun stringValue(): kotlin.String = value

        override fun asBoolean(): Boolean = value.contentEquals("true", ignoreCase = true)
        override fun asInt(): Int = value.trim().toIntOrNull() ?: 0 // Int and Long can't have spaces around, Float and Double can, for some reason
        override fun asLong(): Long = value.trim().toLongOrNull() ?: 0L
        override fun asFloat(): Float = value.toFloatOrNull() ?: 0f
        override fun asDouble(): Double = value.toDoubleOrNull() ?: 0.0
        override fun asString(): kotlin.String = value

        override fun appendJson(out: Appendable, indent: Int) {
            if (escapedValue != null) {
                out.append('"').append(escapedValue).append('"')
            } else {
                out.appendJsonString(unescapedValue!!)
            }
        }

        override fun equals(other: Any?): Boolean {
            if (other !is String) return false
            return value == other.value
        }

        override fun hashCode(): Int {
            return value.hashCode()
        }
    }

    /**
     * Since Json does not specify the permissible range of values,
     * the value is stored simply as string and parsed on demand.
     */
    class Number internal constructor(
        internal var textValueCache: CharSequence?,
        internal var numericValueCache: kotlin.Number?,
    ): JsonValue() {

        constructor(value: kotlin.Number): this(null, value)

        /**
         * The value as [Int], [Long] or [Double].
         * If you expect the value to not be representable by these types precisely,
         * parse it yourself from [textValue].
         */
        val value: kotlin.Number
            get() {
                var v = numericValueCache
                if (v == null) {
                    val t = textValueCache!!.toString()
                    v = t.toIntOrNull() ?: t.toLongOrNull() ?: t.toDouble()
                    this.numericValueCache = v
                }
                return v
            }

        val textValue: kotlin.String
            get() {
                var v = textValueCache
                if (v == null) {
                    v = numericValueCache!!.toString()
                    textValueCache = v
                } else if (v !is kotlin.String) {
                    v = v.toString()
                    textValueCache = v
                }
                return v
            }

        override fun intValue(): Int =       value.toInt()
        override fun longValue(): Long =     value.toLong()
        override fun floatValue(): Float =   value.toFloat()
        override fun doubleValue(): Double = value.toDouble()
        override fun stringValue(): kotlin.String = textValue

        override fun asBoolean(): Boolean {
            val text = textValueCache
            if (text != null) {
                // If not zero (=contains non-zero before exponent), then true
                for (c in text) {
                    if (c in '1'..'9') return true
                    if (c == 'e' || c == 'E') break
                }
                return false
            }
            return value != 0
        }

        override fun appendJson(out: Appendable, indent: Int) {
            out.append(textValueCache ?: numericValueCache.toString())
        }

        override fun equals(other: Any?): Boolean {
            if (other !is Number) return false
            val v = value
            val ov = other.value
            if (v is Int && ov is Int) return v == ov
            if (v is Long && ov is Long) return v == ov
            return value.toDouble() == other.value.toDouble()
        }

        override fun hashCode(): Int = value.hashCode()
    }

    object Null: JsonValue() {
        override fun appendJson(out: Appendable, indent: Int) {
            out.append("null")
        }
    }

    sealed class Bool : JsonValue()
    object True : Bool() {
        override fun booleanValue(): Boolean = true
        override fun asBoolean(): Boolean = true
        override fun asInt(): Int = 1
        override fun asLong(): Long = 1L
        override fun asFloat(): Float = 1f
        override fun asDouble(): Double = 1.0
        override fun asString(): kotlin.String = "true"
        override fun appendJson(out: Appendable, indent: Int) {
            out.append("true")
        }
    }
    object False : Bool() {
        override fun booleanValue(): Boolean = false
        override fun asString(): kotlin.String = "false"
        override fun appendJson(out: Appendable, indent: Int) {
            out.append("false")
        }
    }

    /** If this value exactly represents a boolean, return it, throw [JsonValueException] otherwise. */
    open fun booleanValue(): Boolean = throw JsonValueException("$this does not represent a boolean")
    /** If this value exactly represents an int, return it, throw [JsonValueException] otherwise. */
    open fun intValue(): Int = throw JsonValueException("$this does not represent an int")
    /** If this value exactly represents a long, return it, throw [JsonValueException] otherwise. */
    open fun longValue(): Long = throw JsonValueException("$this does not represent a long")
    /** If this value exactly represents a float, return it, throw [JsonValueException] otherwise. */
    open fun floatValue(): Float = throw JsonValueException("$this does not represent a float")
    /** If this value exactly represents a double, return it, throw [JsonValueException] otherwise. */
    open fun doubleValue(): Double = throw JsonValueException("$this does not represent a double")
    /** If this value exactly represents a string, return it, throw [JsonValueException] otherwise. */
    open fun stringValue(): kotlin.String = throw JsonValueException("$this does not represent a string")

    /** If this value exactly represents a boolean, return it, throw [JsonValueException] otherwise. */
    open fun nullableBooleanValue(): Boolean? = if (this == Null) null else booleanValue()
    /** If this value exactly represents an int, return it, throw [JsonValueException] otherwise. */
    open fun nullableIntValue(): Int? = if (this == Null) null else intValue()
    /** If this value exactly represents a long, return it, throw [JsonValueException] otherwise. */
    open fun nullableLongValue(): Long? = if (this == Null) null else longValue()
    /** If this value exactly represents a float, return it, throw [JsonValueException] otherwise. */
    open fun nullableFloatValue(): Float? = if (this == Null) null else floatValue()
    /** If this value exactly represents a double, return it, throw [JsonValueException] otherwise. */
    open fun nullableDoubleValue(): Double? = if (this == Null) null else doubleValue()
    /** If this value exactly represents a string, return it, throw [JsonValueException] otherwise. */
    open fun nullableStringValue(): kotlin.String? = if (this == Null) null else stringValue()

    /** Attempt to convert the value into a boolean. Never throws. */
    open fun asBoolean(): Boolean = try { booleanValue() } catch (e: JsonValueException) { false }
    /** Attempt to convert the value into an int. Never throws. */
    open fun asInt(): Int = try { intValue() } catch (e: JsonValueException) { 0 }
    /** Attempt to convert the value into a long. Never throws. */
    open fun asLong(): Long = try { longValue() } catch (e: JsonValueException) { 0L }
    /** Attempt to convert the value into a float. Never throws. */
    open fun asFloat(): Float = try { floatValue() } catch (e: JsonValueException) { 0f }
    /** Attempt to convert the value into a double. Never throws. */
    open fun asDouble(): Double = try { doubleValue() } catch (e: JsonValueException) { 0.0 }
    /** Attempt to convert the value into a string. Never throws. */
    open fun asString(): kotlin.String = try { stringValue() } catch (e: JsonValueException) { "" }

    /** Return the size of the collection ([Object] or [Array]) or 0 if not a collection. */
    open val size: Int
        get() = 0

    /** Get the [index]'th value of [Object] or [Array].
     * @throw JsonValueException when not a collection
     * @throw IndexOutOfBoundsException when the index is out of bounds */
    open operator fun get(index: Int): JsonValue = throw JsonValueException("Not a collection")
    /** Get the first value with key equal to [key] of [Object] or null when no such key exists or not an [Object]. */
    open operator fun get(key: kotlin.String): JsonValue? = null

    override fun iterator(): Iterator<JsonValue> = emptyList<JsonValue>().iterator()

    abstract fun appendJson(out: Appendable, indent: Int)

    override fun toString(): kotlin.String {
        return buildString {
            appendJson(this, -1)
        }
    }

    fun toPrettyString(): kotlin.String {
        return buildString {
            appendJson(this, 0)
        }
    }
}

private const val HEX_DIGITS = "0123456789ABCDEF"

/** Thrown when the [JsonValue] does not represent the expected type. */
class JsonValueException : RuntimeException {
    constructor(message: String): super(message)
    constructor(cause: Throwable) : super(cause)
}

internal fun Appendable.appendIndent(indent: Int):Appendable {
    repeat(indent) {
        append("  ")
    }
    return this
}

fun Appendable.appendJsonString(value: CharSequence):Appendable {
    append('"')
    for (c in value) {
        when (c) {
            '\b' -> append("\\b")
            FORM_FEED -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            in 0.toChar()..0x1F.toChar() -> {
                // Needs special encoding
                append("\\u")
                    .append(HEX_DIGITS[(c.code shr 12) and 0xF])
                    .append(HEX_DIGITS[(c.code shr 8) and 0xF])
                    .append(HEX_DIGITS[(c.code shr 4) and 0xF])
                    .append(HEX_DIGITS[(c.code shr 0) and 0xF])
            }
            else -> append(c)
        }
    }
    return append('"')
}

/**
 * Like [appendJsonString] but also escapes all codepoints that are represented by UTF-16 surrogate pairs.
 */
fun Appendable.appendJsonStringStrict(value: CharSequence):Appendable {
    append('"')
    for (c in value) {
        when (c) {
            '\b' -> append("\\b")
            FORM_FEED -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            in 0.toChar()..0x1F.toChar(),
            in 0xD800.toChar()..0xDFFF.toChar() -> {
                // Needs special encoding
                append("\\u")
                    .append(HEX_DIGITS[(c.code shr 12) and 0xF])
                    .append(HEX_DIGITS[(c.code shr 8) and 0xF])
                    .append(HEX_DIGITS[(c.code shr 4) and 0xF])
                    .append(HEX_DIGITS[(c.code shr 0) and 0xF])
            }
            else -> append(c)
        }
    }
    return append('"')
}

internal const val FORM_FEED:Char = 0x0C.toChar()