package com.darkyen.json

/** Expecting any value to start now */
private const val STATE_EXPECT_VALUE = 0
/** Parsed "n" of null, expecting "ull" */
private const val STATE_N_ULL = 1
/** Parsed "nu" of null, expecting "ll" */
private const val STATE_NU_LL = 2
/** Parsed "nul" of null, expecting "l" */
private const val STATE_NUL_L = 3
/** Parsed "t" of true, expecting "rue" */
private const val STATE_T_RUE = 4
/** Parsed "tr" of true, expecting "ue" */
private const val STATE_TR_UE = 5
/** Parsed "tru" of true, expecting "e" */
private const val STATE_TRU_E = 6
/** Parsed "f" of false, expecting "alse" */
private const val STATE_F_ALSE = 7
/** Parsed "fa" of false, expecting "lse" */
private const val STATE_FA_LSE = 8
/** Parsed "fal" of false, expecting "se" */
private const val STATE_FAL_SE = 9
/** Parsed "fals" of false, expecting "e" */
private const val STATE_FALS_E = 10
/** Parsed opening quote of a string and possibly some characters. Expecting closing quote or more characters. */
private const val STATE_STRING = 11
/** In [STATE_STRING] but the next character was escaped. */
private const val STATE_STRING_ESCAPE = 12
private const val STATE_STRING_ESCAPE_U_XXXX = 13
private const val STATE_STRING_ESCAPE_UX_XXX = 14
private const val STATE_STRING_ESCAPE_UXX_XX = 15
private const val STATE_STRING_ESCAPE_UXXX_X = 16
/** Expecting string value to use as a field name */
private const val STATE_EXPECT_KEY_STRING = 17
private const val STATE_END_OF_VALUE = 18
private const val STATE_END_OF_JSON = 19
private const val STATE_EXPECT_SEMICOLON = 20
private const val STATE_EXPECT_OBJECT_COMMA = 21
private const val STATE_EXPECT_LIST_COMMA = 22

private const val STATE_NUMBER_AFTER_SIGN = 23
private const val STATE_NUMBER_AFTER_ZERO = 24
private const val STATE_NUMBER_AFTER_DIGIT = 25
private const val STATE_NUMBER_AFTER_DECIMAL = 26
private const val STATE_NUMBER_AFTER_DECIMAL_DIGIT = 27
private const val STATE_NUMBER_AFTER_E = 28
private const val STATE_NUMBER_AFTER_E_SIGN = 29
private const val STATE_NUMBER_AFTER_E_DIGIT = 30
private const val STATE_NUMBER_END = 31

private const val STATE_EXPECT_KEY_STRING_OR_END_OF_OBJECT = 32
private const val STATE_EXPECT_VALUE_OR_END_OF_ARRAY = 33

private fun isNumberState(state: Int): Boolean = state in STATE_NUMBER_AFTER_SIGN..STATE_NUMBER_AFTER_E_DIGIT

fun parseJson(jsonString: CharSequence): Result<JsonValue> = runCatching {
    var state: Int = STATE_EXPECT_VALUE
    // Where does plain string content begin
    var stringOrNumberBegin = -1
    var stringHasEscapes = false
    var stringIsKey = false
    val stack = ArrayList<Any>()
    var result: JsonValue? = null

    // Parsing states
    val jsonStringLength = jsonString.length
    for (i in 0..jsonStringLength) {
        val c = if (i >= jsonStringLength) 0.toChar() else {
            val c = jsonString[i]
            if (c == 0.toChar()) throw JsonParseException("Unexpected 0x0 at $i")
            c
        }

        // Parse number first
        if (isNumberState(state)) {
            state = when (state) {
                STATE_NUMBER_AFTER_SIGN -> when (c) {
                    '0' -> STATE_NUMBER_AFTER_ZERO
                    in '1'..'9' -> STATE_NUMBER_AFTER_DIGIT
                    else -> throw JsonParseException(i, "digit", c)
                }
                STATE_NUMBER_AFTER_ZERO -> when (c) {
                    '.' -> STATE_NUMBER_AFTER_DECIMAL
                    'e', 'E' -> STATE_NUMBER_AFTER_E
                    else -> STATE_NUMBER_END
                }
                STATE_NUMBER_AFTER_DIGIT -> when (c) {
                    in '0'..'9' -> STATE_NUMBER_AFTER_DIGIT
                    '.' -> STATE_NUMBER_AFTER_DECIMAL
                    'e', 'E' -> STATE_NUMBER_AFTER_E
                    else -> STATE_NUMBER_END
                }
                STATE_NUMBER_AFTER_DECIMAL -> when (c) {
                    in '0'..'9' -> STATE_NUMBER_AFTER_DECIMAL_DIGIT
                    else -> throw JsonParseException(i, "decimal digit", c)
                }
                STATE_NUMBER_AFTER_DECIMAL_DIGIT -> when (c) {
                    in '0'..'9' -> STATE_NUMBER_AFTER_DECIMAL_DIGIT
                    'e', 'E' -> STATE_NUMBER_AFTER_E
                    else -> STATE_NUMBER_END
                }
                STATE_NUMBER_AFTER_E -> when (c) {
                    '+', '-' -> STATE_NUMBER_AFTER_E_SIGN
                    in '0'..'9' -> STATE_NUMBER_AFTER_E_DIGIT
                    else -> throw JsonParseException(i, "exponent", c)
                }
                STATE_NUMBER_AFTER_E_SIGN -> when (c) {
                    in '0'..'9' -> STATE_NUMBER_AFTER_E_DIGIT
                    else -> throw JsonParseException(i, "exponent digit", c)
                }
                STATE_NUMBER_AFTER_E_DIGIT -> when (c) {
                    in '0'..'9' -> STATE_NUMBER_AFTER_E_DIGIT
                    else -> STATE_NUMBER_END
                }
                else -> error("expected number state, got $state")
            }

            if (state == STATE_NUMBER_END) {
                // The number ends here either way
                val number = JsonValue.Number(CharSequenceView(jsonString, stringOrNumberBegin, i - stringOrNumberBegin), null)
                state = if (stack.isEmpty()) {
                    // Freestanding value, check rest and return
                    result = number
                    STATE_END_OF_JSON
                } else {
                    val stackTop = stack.last()
                    if (stackTop is String) {
                        stack.removeLast()
                        ((stack.last() as JsonValue.Object).fields as ArrayList<Pair<String, JsonValue>>).add(stackTop to number)
                        STATE_EXPECT_OBJECT_COMMA
                    } else {
                        ((stackTop as JsonValue.Array).values as ArrayList<JsonValue>).add(number)
                        STATE_EXPECT_LIST_COMMA
                    }
                }
            } else {
                // The character was a part of a number, don't reinterpret it
                continue
            }
        }

        var resultValue: JsonValue? = null
        state = when (state) {
            STATE_EXPECT_VALUE, STATE_EXPECT_VALUE_OR_END_OF_ARRAY -> when (c) {
                ' ', '\n', '\r', '\t' -> state// Whitespace, ignore
                'n' -> STATE_N_ULL
                't' -> STATE_T_RUE
                'f' -> STATE_F_ALSE
                '"' -> {
                    stringOrNumberBegin = i + 1
                    stringHasEscapes = false
                    stringIsKey = false
                    STATE_STRING
                }
                '-' -> {
                    stringOrNumberBegin = i
                    STATE_NUMBER_AFTER_SIGN
                }
                '0' -> {
                    stringOrNumberBegin = i
                    STATE_NUMBER_AFTER_ZERO
                }
                in '1'..'9' -> {
                    stringOrNumberBegin = i
                    STATE_NUMBER_AFTER_DIGIT
                }
                '{' -> {
                    stack.add(JsonValue.Object(ArrayList()))
                    STATE_EXPECT_KEY_STRING_OR_END_OF_OBJECT
                }
                '[' -> {
                    stack.add(JsonValue.Array(ArrayList()))
                    STATE_EXPECT_VALUE_OR_END_OF_ARRAY
                }
                ']' -> if (state == STATE_EXPECT_VALUE_OR_END_OF_ARRAY) {
                    resultValue = stack.removeLast() as JsonValue
                    STATE_END_OF_VALUE
                } else {
                    throw JsonParseException(i, "JSON Value", c)
                }
                else -> throw JsonParseException(i, "JSON Value", c)
            }
            STATE_EXPECT_KEY_STRING, STATE_EXPECT_KEY_STRING_OR_END_OF_OBJECT -> when (c) {
                ' ', '\n', '\r', '\t' -> state// Whitespace, ignore
                '"' -> {
                    stringOrNumberBegin = i + 1
                    stringHasEscapes = false
                    stringIsKey = true
                    STATE_STRING
                }
                '}' -> if (state == STATE_EXPECT_KEY_STRING_OR_END_OF_OBJECT) {
                    // Object end
                    resultValue = stack.removeLast() as JsonValue
                    STATE_END_OF_VALUE
                } else throw JsonParseException(i, "field name", c)
                else -> throw JsonParseException(i, "field name", c)
            }
            STATE_EXPECT_SEMICOLON -> when (c) {
                ' ', '\n', '\r', '\t' -> state// Whitespace, ignore
                ':' -> STATE_EXPECT_VALUE
                else -> throw JsonParseException(i, ":", c)
            }
            STATE_EXPECT_OBJECT_COMMA -> when (c) {
                ' ', '\n', '\r', '\t' -> state// Whitespace, ignore
                ',' -> STATE_EXPECT_KEY_STRING
                '}' -> {
                    // Object end
                    resultValue = stack.removeLast() as JsonValue
                    STATE_END_OF_VALUE
                }
                else -> throw JsonParseException(i, ", or end of object", c)
            }
            STATE_EXPECT_LIST_COMMA -> when (c) {
                ' ', '\n', '\r', '\t' -> state// Whitespace, ignore
                ',' -> STATE_EXPECT_VALUE
                ']' -> {
                    // Array end
                    resultValue = stack.removeLast() as JsonValue
                    STATE_END_OF_VALUE
                }
                else -> throw JsonParseException(i, ", or end of array", c)
            }
            STATE_END_OF_JSON -> when (c) {
                ' ', '\n', '\r', '\t', 0.toChar() -> state// Whitespace, ignore
                else -> throw JsonParseException(i, "end of JSON", c)
            }
            STATE_N_ULL -> when (c) {
                'u' -> STATE_NU_LL
                else -> throw JsonParseException(i, "n|ull", c)
            }
            STATE_NU_LL -> when (c) {
                'l' -> STATE_NUL_L
                else -> throw JsonParseException(i, "nu|ll", c)
            }
            STATE_NUL_L -> when (c) {
                'l' -> {
                    resultValue = JsonValue.Null
                    STATE_END_OF_VALUE
                }
                else -> throw JsonParseException(i, "nul|l", c)
            }
            STATE_T_RUE -> when (c) {
                'r' -> STATE_TR_UE
                else -> throw JsonParseException(i, "t|rue", c)
            }
            STATE_TR_UE -> when (c) {
                'u' -> STATE_TRU_E
                else -> throw JsonParseException(i, "tr|ue", c)
            }
            STATE_TRU_E -> when (c) {
                'e' -> {
                    resultValue = JsonValue.True
                    STATE_END_OF_VALUE
                }
                else -> throw JsonParseException(i, "tru|e", c)
            }
            STATE_F_ALSE -> when (c) {
                'a' -> STATE_FA_LSE
                else -> throw JsonParseException(i, "f|alse", c)
            }
            STATE_FA_LSE -> when (c) {
                'l' -> STATE_FAL_SE
                else -> throw JsonParseException(i, "fa|lse", c)
            }
            STATE_FAL_SE -> when (c) {
                's' -> STATE_FALS_E
                else -> throw JsonParseException(i, "fal|se", c)
            }
            STATE_FALS_E -> when (c) {
                'e' -> {
                    resultValue = JsonValue.False
                    STATE_END_OF_VALUE
                }
                else -> throw JsonParseException(i, "fals|e", c)
            }
            STATE_STRING -> when (c) {
                '"' -> {
                    // String end
                    val content = CharSequenceView(jsonString, stringOrNumberBegin, i - stringOrNumberBegin)
                    if (stringIsKey) {
                        stack.add((if (stringHasEscapes) jsonUnescape(content) else content).toString())
                        STATE_EXPECT_SEMICOLON
                    } else {
                        resultValue = JsonValue.String(content, if (stringHasEscapes) null else content)
                        STATE_END_OF_VALUE
                    }
                }
                '\\' -> {
                    // Escaping
                    stringHasEscapes = true
                    STATE_STRING_ESCAPE
                }
                in 0.toChar()..0x1F.toChar() -> throw JsonParseException(i, "string char", c)
                else -> STATE_STRING
            }
            STATE_STRING_ESCAPE -> when (c) {
                '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> {
                    STATE_STRING
                }
                'u' -> {
                    STATE_STRING_ESCAPE_U_XXXX
                }
                else -> throw JsonParseException(i, "valid JSON escape", c)
            }
            STATE_STRING_ESCAPE_U_XXXX -> when (c) {
                in '0'..'9', in 'a'..'f', in 'A'..'F' -> STATE_STRING_ESCAPE_UX_XXX
                else -> throw JsonParseException(i, "hex digit", c)
            }
            STATE_STRING_ESCAPE_UX_XXX -> when (c) {
                in '0'..'9', in 'a'..'f', in 'A'..'F' -> STATE_STRING_ESCAPE_UXX_XX
                else -> throw JsonParseException(i, "hex digit", c)
            }
            STATE_STRING_ESCAPE_UXX_XX -> when (c) {
                in '0'..'9', in 'a'..'f', in 'A'..'F' -> STATE_STRING_ESCAPE_UXXX_X
                else -> throw JsonParseException(i, "hex digit", c)
            }
            STATE_STRING_ESCAPE_UXXX_X -> when (c) {
                in '0'..'9', in 'a'..'f', in 'A'..'F' -> STATE_STRING
                else -> throw JsonParseException(i, "hex digit", c)
            }
            else -> error("Unexpected state $state")
        }

        if (resultValue != null) {
            if (state != STATE_END_OF_VALUE) error("state must be END_OF_VALUE")
            state = if (stack.isEmpty()) {
                // Freestanding value, check rest and return
                result = resultValue
                STATE_END_OF_JSON
            } else {
                val stackTop = stack.last()
                if (stackTop is String) {
                    stack.removeLast()
                    ((stack.last() as JsonValue.Object).fields as ArrayList<Pair<String, JsonValue>>).add(stackTop to resultValue)
                    STATE_EXPECT_OBJECT_COMMA
                } else {
                    ((stackTop as JsonValue.Array).values as ArrayList<JsonValue>).add(resultValue)
                    STATE_EXPECT_LIST_COMMA
                }
            }
        }
    }

    if (state != STATE_END_OF_JSON || result == null) {
        error("Unexpected end state ($state): $result")
    }
    result
}

class JsonTokens(val source: CharSequence) {
    /**
     * Packed, from most significant bit down:
     * 4 bits: ordinal of [JsonTokenType]
     * 28 bits: index into the json
     */
    private var tokenTypesAndOffsets = IntArray(64)// Packed  8bits
    var tokenCount: Int = 0
        private set
    var errorMessage: String? = null
        private set

    fun tokenType(tokenIndex: Int): JsonTokenType {
        return JsonTokenType.VALUES[tokenTypesAndOffsets[tokenIndex] ushr 28]
    }

    fun tokenCharPosition(tokenIndex: Int): Int {
        return tokenTypesAndOffsets[tokenIndex] and 0x0FFF_FFFF
    }

    /**
     * Length of the token in characters, as amount of chars to advance by to get to the first character after the token.
     * For BEGIN-END token pairs, this advances after the END token. END tokens are always 1 character long.
     */
    fun tokenCharLength(tokenIndex: Int): Int {
        return when (tokenType(tokenIndex)) {
            JsonTokenType.NULL -> 4
            JsonTokenType.TRUE -> 4
            JsonTokenType.FALSE -> 5
            JsonTokenType.NUMBER_BEGIN -> {
                if (tokenType(tokenIndex + 1) != JsonTokenType.NUMBER_END) 0
                else tokenCharPosition(tokenIndex + 1) + 1 - tokenCharPosition(tokenIndex)
            }
            JsonTokenType.STRING_BEGIN -> {
                if (tokenType(tokenIndex + 1) != JsonTokenType.STRING_END) 0
                else tokenCharPosition(tokenIndex + 1) + 1 - tokenCharPosition(tokenIndex)
            }
            JsonTokenType.NAME_BEGIN -> {
                if (tokenType(tokenIndex + 1) != JsonTokenType.NAME_END) 0
                else tokenCharPosition(tokenIndex + 1) + 1 - tokenCharPosition(tokenIndex)
            }
            JsonTokenType.OBJECT_BEGIN,
            JsonTokenType.OBJECT_END,
            JsonTokenType.ARRAY_BEGIN,
            JsonTokenType.ARRAY_END -> 1
            JsonTokenType.NUMBER_END,
            JsonTokenType.STRING_END,
            JsonTokenType.NAME_END -> 1
        }
    }

    fun addToken(position: Int, type: JsonTokenType) {
        val i = tokenCount++
        if (i >= tokenTypesAndOffsets.size) {
            tokenTypesAndOffsets = tokenTypesAndOffsets.copyOf(tokenTypesAndOffsets.size * 2)
        }
        tokenTypesAndOffsets[i] = (type.ordinal shl 28) or position
    }

    fun setError(message: String): JsonTokens {
        errorMessage = message
        return this
    }

    fun setError(position: Int, expected: String, got: Char): JsonTokens {
        errorMessage = "Expected $expected at index $position, got '${if (got == 0.toChar()) "EOF" else if (got <= 0x1F.toChar()) "0x"+got.code.toString(16) else got}'"
        return this
    }

    /**
     * The length of the JSON value that starts at [tokenIndex] in tokens.
     * Since END tokens don't start any JSON value, they return 0.
     */
    fun valueTokenLength(tokenIndex: Int): Int {
        return when (tokenType(tokenIndex)) {
            JsonTokenType.NULL,
            JsonTokenType.TRUE,
            JsonTokenType.FALSE -> 1
            JsonTokenType.NUMBER_BEGIN -> 2
            JsonTokenType.NUMBER_END -> 0
            JsonTokenType.STRING_BEGIN -> 2
            JsonTokenType.STRING_END -> 0
            JsonTokenType.NAME_BEGIN -> 2
            JsonTokenType.NAME_END -> 0
            JsonTokenType.OBJECT_BEGIN -> {
                val endTokenIndex = forEachObjectField(tokenIndex) { _, _ -> }
                1 + endTokenIndex - tokenIndex
            }
            JsonTokenType.OBJECT_END -> 0
            JsonTokenType.ARRAY_BEGIN -> {
                val endTokenIndex = forEachArrayElement(tokenIndex) { _ -> }
                1 + endTokenIndex - tokenIndex
            }
            JsonTokenType.ARRAY_END -> 0
        }
    }

    /** Return the value of string or name that begins at given [tokenIndex] */
    fun stringValue(tokenIndex: Int): String {
        val begin = tokenCharPosition(tokenIndex) + 1
        val end = tokenCharPosition(tokenIndex + 1)
        return jsonUnescape(CharSequenceView(source, begin, end - begin))
    }

    /** Return the value of number that begins at given [tokenIndex] */
    fun numberValue(tokenIndex: Int): Double {
        val begin = tokenCharPosition(tokenIndex)
        val end = tokenCharPosition(tokenIndex + 1) + 1
        return source.substring(begin, end).toDouble()
    }

    /** Given an object starting at [tokenIndexOfObjectBegin], return true if is has no fields */
    fun isObjectEmpty(tokenIndexOfObjectBegin: Int): Boolean {
        return tokenType(tokenIndexOfObjectBegin + 1) == JsonTokenType.OBJECT_END
    }

    /** Given an array starting at [tokenIndexOfArrayBegin], return true if is has no elements */
    fun isArrayEmpty(tokenIndexOfArrayBegin: Int): Boolean {
        return tokenType(tokenIndexOfArrayBegin + 1) == JsonTokenType.ARRAY_END
    }

    /**
     * Return the value that starts at [tokenIndex] as [JsonValue].
     */
    fun jsonValueAt(tokenIndex: Int): JsonValue {
        return when (val type = tokenType(tokenIndex)) {
            JsonTokenType.NULL -> JsonValue.Null
            JsonTokenType.TRUE -> JsonValue.True
            JsonTokenType.FALSE -> JsonValue.False
            JsonTokenType.NUMBER_BEGIN -> {
                val startPos = tokenCharPosition(tokenIndex)
                val endPos = tokenCharPosition(tokenIndex + 1) + 1
                JsonValue.Number(CharSequenceView(source, startPos, endPos - startPos), null)
            }
            JsonTokenType.STRING_BEGIN -> {
                val startPos = tokenCharPosition(tokenIndex) + 1
                val endPos = tokenCharPosition(tokenIndex + 1)
                JsonValue.String(CharSequenceView(source, startPos, endPos - startPos), null)
            }
            JsonTokenType.OBJECT_BEGIN -> {
                val objectValues = ArrayList<Pair<String, JsonValue>>()
                forEachObjectField(tokenIndex) { nameTokenIndex, valueTokenIndex ->
                    val name = stringValue(nameTokenIndex)
                    val value = jsonValueAt(valueTokenIndex)
                    objectValues.add(name to value)
                }
                JsonValue.Object(objectValues)
            }
            JsonTokenType.ARRAY_BEGIN -> {
                val arrayValues = ArrayList<JsonValue>()
                forEachArrayElement(tokenIndex) { valueTokenIndex ->
                    arrayValues.add(jsonValueAt(valueTokenIndex))
                }
                JsonValue.Array(arrayValues)
            }
            else -> throw JsonValueException("Type $type does not start a json value")
        }
    }

    /**
     * Return the value that starts at [tokenIndex] as valid JSON.
     */
    fun jsonValueStringAt(tokenIndex: Int): String {
        return when (val type = tokenType(tokenIndex)) {
            JsonTokenType.NULL -> "null"
            JsonTokenType.TRUE -> "true"
            JsonTokenType.FALSE -> "false"
            JsonTokenType.NUMBER_BEGIN,
            JsonTokenType.STRING_BEGIN,
            JsonTokenType.NAME_BEGIN,
            JsonTokenType.OBJECT_BEGIN,
            JsonTokenType.ARRAY_BEGIN -> {
                val startPos = tokenCharPosition(tokenIndex)
                val endToken = tokenIndex + valueTokenLength(tokenIndex) - 1
                val endPos = tokenCharPosition(endToken) + 1
                return source.substring(startPos, endPos)
            }
            else -> throw JsonValueException("Type $type does not start a json value")
        }
    }

    /**
     * Return [JsonTokens] that contain just the value at the given tokenIndex.
     * May return this.
     */
    fun jsonValueTokensAt(tokenIndex: Int): JsonTokens {
        if (tokenIndex == 0 && errorMessage == null) return this
        return when (val type = tokenType(tokenIndex)) {
            JsonTokenType.NULL -> JsonTokens("null").apply { addToken(0, JsonTokenType.NULL) }
            JsonTokenType.TRUE -> JsonTokens("true").apply { addToken(0, JsonTokenType.TRUE) }
            JsonTokenType.FALSE -> JsonTokens("false").apply { addToken(0, JsonTokenType.FALSE) }
            JsonTokenType.NUMBER_BEGIN,
            JsonTokenType.STRING_BEGIN,
            JsonTokenType.NAME_BEGIN,
            JsonTokenType.OBJECT_BEGIN,
            JsonTokenType.ARRAY_BEGIN -> {
                val startPos = tokenCharPosition(tokenIndex)
                val endToken = tokenIndex + valueTokenLength(tokenIndex) - 1
                val endPos = tokenCharPosition(endToken) + 1
                val tokens = JsonTokens(source.subSequence(startPos, endPos))
                for (t in tokenIndex .. endToken) {
                    tokens.addToken(tokenCharPosition(t) - startPos, tokenType(t))
                }
                tokens
            }
            else -> throw JsonValueException("Type $type does not start a json value")
        }
    }

    /**
     * Given an array that starts at [tokenIndexOfArrayStart], return the index at which the array's element at index [valueIndex]
     * begins, or -1 if there is no such element
     */
    fun tokenIndexOfArrayElement(tokenIndexOfArrayStart: Int, valueIndex: Int): Int {
        forEachArrayElementIndexed(tokenIndexOfArrayStart) { value, elementTokenIndex ->
            if (value == valueIndex) return elementTokenIndex
        }
        return -1
    }

    /**
     * Given an object that starts at [tokenIndexOfObjectStart], return the index at which the object's field name at index [fieldIndex]
     * begins, or -1 if there is no such element
     */
    fun tokenIndexOfObjectFieldName(tokenIndexOfObjectStart: Int, fieldIndex: Int): Int {
        forEachObjectFieldIndexed(tokenIndexOfObjectStart) { field, nameIndex, _ ->
            if (field == fieldIndex) return nameIndex
        }
        return -1
    }

    /**
     * Given an object that starts at [tokenIndexOfObjectStart], return the index at which the object's field value at index [fieldIndex]
     * begins, or -1 if there is no such element
     */
    fun tokenIndexOfObjectFieldValue(tokenIndexOfObjectStart: Int, fieldIndex: Int): Int {
        forEachObjectFieldIndexed(tokenIndexOfObjectStart) { field, _, valueIndex ->
            if (field == fieldIndex) return valueIndex
        }
        return -1
    }

    /**
     * Given an array that starts at [tokenIndexOfArrayStart], return the amount of elements it has
     */
    fun arrayElementCount(tokenIndexOfArrayStart: Int): Int {
        return forEachArrayElementIndexed(tokenIndexOfArrayStart) { _, _ -> }
    }

    /**
     * Given an object that starts at [tokenIndexOfObjectStart], return the amount of fields it has
     */
    fun objectFieldCount(tokenIndexOfObjectStart: Int): Int {
        return forEachObjectFieldIndexed(tokenIndexOfObjectStart) { _, _, _ -> }
    }


    /**
     * Given an array that starts at [tokenIndexOfArrayStart], iterate its values - [handleValue] is called
     * with index of each element value.
     * @return the index of the end token
     */
    inline fun forEachArrayElement(tokenIndexOfArrayStart: Int, handleValue: (valueTokenIndex: Int) -> Unit): Int {
        var endTokenIndex = tokenIndexOfArrayStart + 1
        while (tokenType(endTokenIndex) != JsonTokenType.ARRAY_END) {
            handleValue(endTokenIndex)
            endTokenIndex += valueTokenLength(endTokenIndex)// Skip value
        }
        return endTokenIndex
    }

    /**
     * Variant of [forEachArrayElement] that also reports the index of the value.
     * @returns the size of the array (amount of elements)
     */
    inline fun forEachArrayElementIndexed(tokenIndexOfArrayStart: Int, handleValue: (elementIndex: Int, elementTokenIndex: Int) -> Unit): Int {
        var elementIndex = 0
        var endTokenIndex = tokenIndexOfArrayStart + 1
        while (tokenType(endTokenIndex) != JsonTokenType.ARRAY_END) {
            handleValue(elementIndex, endTokenIndex)
            elementIndex += 1
            endTokenIndex += valueTokenLength(endTokenIndex)// Skip value
        }
        return elementIndex
    }

    /**
     * Given an object that starts at [tokenIndexOfObjectStart], iterate its values - [handleField] is called
     * with index of each field name and value tokens.
     * @return the index of the end token
     */
    inline fun forEachObjectField(tokenIndexOfObjectStart: Int, handleField: (nameTokenIndex: Int, valueTokenIndex: Int) -> Unit): Int {
        var endTokenIndex = tokenIndexOfObjectStart + 1
        while (tokenType(endTokenIndex) != JsonTokenType.OBJECT_END) {
            handleField(endTokenIndex, endTokenIndex + 2)
            endTokenIndex += 2 + valueTokenLength(endTokenIndex + 2)// Skip value
        }
        return endTokenIndex
    }

    /**
     * Variant of [forEachObjectField] that also reports the index of the field.
     * @returns the size of the object (amount of fields)
     */
    inline fun forEachObjectFieldIndexed(tokenIndexOfObjectStart: Int, handleField: (fieldIndex: Int, nameTokenIndex: Int, valueTokenIndex: Int) -> Unit): Int {
        var fieldIndex = 0
        var endTokenIndex = tokenIndexOfObjectStart + 1
        while (tokenType(endTokenIndex) != JsonTokenType.OBJECT_END) {
            handleField(fieldIndex, endTokenIndex, endTokenIndex + 2)
            fieldIndex += 1
            endTokenIndex += 2 + valueTokenLength(endTokenIndex + 2)// Skip value
        }
        return fieldIndex
    }
}

enum class JsonTokenType {
    /** position: index of first 'n' */
    NULL,//0
    /** position: index of first 't' */
    TRUE,//1
    /** position: index of first 'f' */
    FALSE,//2
    /** position: index of the first number character */
    NUMBER_BEGIN,//3
    /** position: index of the last number character */
    NUMBER_END,//4
    /** position: index of first " */
    STRING_BEGIN,//5
    /** position: index of last " */
    STRING_END,//6
    /** String in position of object field name. position: index of first " */
    NAME_BEGIN,//7
    /** position: index of last " */
    NAME_END,//8
    /** position: index of { */
    OBJECT_BEGIN,//9
    /** position: index of } */
    OBJECT_END,//10
    /** position: index of [ */
    ARRAY_BEGIN,//11
    /** position: index of ] */
    ARRAY_END;//12
    // due to the way it is packed in JsonTokens, max index can be 15
    companion object {
        val VALUES = values()
    }
}

fun tokenizeJson(jsonString: CharSequence): JsonTokens {
    var state: Int = STATE_EXPECT_VALUE
    var stringIsKey = false

    var depth = 0
    val isDepthForObject = BitArray(10)
    val tokens = JsonTokens(jsonString)

    // Parsing states
    val jsonStringLength = jsonString.length
    for (i in 0..jsonStringLength) {
        val c = if (i >= jsonStringLength) 0.toChar() else {
            val c = jsonString[i]
            if (c == 0.toChar()) return tokens.setError("Unexpected 0x0 at $i")
            c
        }

        // Parse number first
        if (isNumberState(state)) {
            state = when (state) {
                STATE_NUMBER_AFTER_SIGN -> when (c) {
                    '0' -> STATE_NUMBER_AFTER_ZERO
                    in '1'..'9' -> STATE_NUMBER_AFTER_DIGIT
                    else -> return tokens.setError(i, "digit", c)
                }
                STATE_NUMBER_AFTER_ZERO -> when (c) {
                    '.' -> STATE_NUMBER_AFTER_DECIMAL
                    'e', 'E' -> STATE_NUMBER_AFTER_E
                    else -> STATE_NUMBER_END
                }
                STATE_NUMBER_AFTER_DIGIT -> when (c) {
                    in '0'..'9' -> STATE_NUMBER_AFTER_DIGIT
                    '.' -> STATE_NUMBER_AFTER_DECIMAL
                    'e', 'E' -> STATE_NUMBER_AFTER_E
                    else -> STATE_NUMBER_END
                }
                STATE_NUMBER_AFTER_DECIMAL -> when (c) {
                    in '0'..'9' -> STATE_NUMBER_AFTER_DECIMAL_DIGIT
                    else -> return tokens.setError(i, "decimal digit", c)
                }
                STATE_NUMBER_AFTER_DECIMAL_DIGIT -> when (c) {
                    in '0'..'9' -> STATE_NUMBER_AFTER_DECIMAL_DIGIT
                    'e', 'E' -> STATE_NUMBER_AFTER_E
                    else -> STATE_NUMBER_END
                }
                STATE_NUMBER_AFTER_E -> when (c) {
                    '+', '-' -> STATE_NUMBER_AFTER_E_SIGN
                    in '0'..'9' -> STATE_NUMBER_AFTER_E_DIGIT
                    else -> return tokens.setError(i, "exponent", c)
                }
                STATE_NUMBER_AFTER_E_SIGN -> when (c) {
                    in '0'..'9' -> STATE_NUMBER_AFTER_E_DIGIT
                    else -> return tokens.setError(i, "exponent digit", c)
                }
                STATE_NUMBER_AFTER_E_DIGIT -> when (c) {
                    in '0'..'9' -> STATE_NUMBER_AFTER_E_DIGIT
                    else -> STATE_NUMBER_END
                }
                else -> error("expected number state, got $state")
            }

            if (state == STATE_NUMBER_END) {
                // The number ends here either way
                tokens.addToken(i - 1, JsonTokenType.NUMBER_END)

                state = if (depth <= 0) {
                    // Freestanding value, check rest and return
                    STATE_END_OF_JSON
                } else if (isDepthForObject[depth - 1]) {
                    STATE_EXPECT_OBJECT_COMMA
                } else {
                    STATE_EXPECT_LIST_COMMA
                }
            } else {
                // The character was a part of a number, don't reinterpret it
                continue
            }
        }

        state = when (state) {
            STATE_EXPECT_VALUE, STATE_EXPECT_VALUE_OR_END_OF_ARRAY -> when (c) {
                ' ', '\n', '\r', '\t' -> state// Whitespace, ignore
                'n' -> STATE_N_ULL
                't' -> STATE_T_RUE
                'f' -> STATE_F_ALSE
                '"' -> {
                    stringIsKey = false
                    tokens.addToken(i, JsonTokenType.STRING_BEGIN)
                    STATE_STRING
                }
                '-' -> {
                    tokens.addToken(i, JsonTokenType.NUMBER_BEGIN)
                    STATE_NUMBER_AFTER_SIGN
                }
                '0' -> {
                    tokens.addToken(i, JsonTokenType.NUMBER_BEGIN)
                    STATE_NUMBER_AFTER_ZERO
                }
                in '1'..'9' -> {
                    tokens.addToken(i, JsonTokenType.NUMBER_BEGIN)
                    STATE_NUMBER_AFTER_DIGIT
                }
                '{' -> {
                    isDepthForObject[depth++] = true
                    tokens.addToken(i, JsonTokenType.OBJECT_BEGIN)
                    STATE_EXPECT_KEY_STRING_OR_END_OF_OBJECT
                }
                '[' -> {
                    isDepthForObject[depth++] = false
                    tokens.addToken(i, JsonTokenType.ARRAY_BEGIN)
                    STATE_EXPECT_VALUE_OR_END_OF_ARRAY
                }
                ']' -> if (state == STATE_EXPECT_VALUE_OR_END_OF_ARRAY) {
                    depth--
                    tokens.addToken(i, JsonTokenType.ARRAY_END)
                    STATE_END_OF_VALUE
                } else {
                    return tokens.setError(i, "JSON Value", c)
                }
                else -> return tokens.setError(i, "JSON Value", c)
            }
            STATE_EXPECT_KEY_STRING, STATE_EXPECT_KEY_STRING_OR_END_OF_OBJECT -> when (c) {
                ' ', '\n', '\r', '\t' -> state// Whitespace, ignore
                '"' -> {
                    tokens.addToken(i, JsonTokenType.NAME_BEGIN)
                    stringIsKey = true
                    STATE_STRING
                }
                '}' -> if (state == STATE_EXPECT_KEY_STRING_OR_END_OF_OBJECT) {
                    // Object end
                    depth--
                    tokens.addToken(i, JsonTokenType.OBJECT_END)
                    STATE_END_OF_VALUE
                } else return tokens.setError(i, "field name", c)
                else -> return tokens.setError(i, "field name", c)
            }
            STATE_EXPECT_SEMICOLON -> when (c) {
                ' ', '\n', '\r', '\t' -> state// Whitespace, ignore
                ':' -> STATE_EXPECT_VALUE
                else -> return tokens.setError(i, ":", c)
            }
            STATE_EXPECT_OBJECT_COMMA -> when (c) {
                ' ', '\n', '\r', '\t' -> state// Whitespace, ignore
                ',' -> STATE_EXPECT_KEY_STRING
                '}' -> {
                    // Object end
                    depth--
                    tokens.addToken(i, JsonTokenType.OBJECT_END)
                    STATE_END_OF_VALUE
                }
                else -> return tokens.setError(i, ", or end of object", c)
            }
            STATE_EXPECT_LIST_COMMA -> when (c) {
                ' ', '\n', '\r', '\t' -> state// Whitespace, ignore
                ',' -> STATE_EXPECT_VALUE
                ']' -> {
                    // Array end
                    depth--
                    tokens.addToken(i, JsonTokenType.ARRAY_END)
                    STATE_END_OF_VALUE
                }
                else -> return tokens.setError(i, ", or end of array", c)
            }
            STATE_END_OF_JSON -> when (c) {
                ' ', '\n', '\r', '\t', 0.toChar() -> state// Whitespace, ignore
                else -> return tokens.setError(i, "end of JSON", c)
            }
            STATE_N_ULL -> when (c) {
                'u' -> STATE_NU_LL
                else -> return tokens.setError(i, "n|ull", c)
            }
            STATE_NU_LL -> when (c) {
                'l' -> STATE_NUL_L
                else -> return tokens.setError(i, "nu|ll", c)
            }
            STATE_NUL_L -> when (c) {
                'l' -> {
                    tokens.addToken(i - 3, JsonTokenType.NULL)
                    STATE_END_OF_VALUE
                }
                else -> return tokens.setError(i, "nul|l", c)
            }
            STATE_T_RUE -> when (c) {
                'r' -> STATE_TR_UE
                else -> return tokens.setError(i, "t|rue", c)
            }
            STATE_TR_UE -> when (c) {
                'u' -> STATE_TRU_E
                else -> return tokens.setError(i, "tr|ue", c)
            }
            STATE_TRU_E -> when (c) {
                'e' -> {
                    tokens.addToken(i - 3, JsonTokenType.TRUE)
                    STATE_END_OF_VALUE
                }
                else -> return tokens.setError(i, "tru|e", c)
            }
            STATE_F_ALSE -> when (c) {
                'a' -> STATE_FA_LSE
                else -> return tokens.setError(i, "f|alse", c)
            }
            STATE_FA_LSE -> when (c) {
                'l' -> STATE_FAL_SE
                else -> return tokens.setError(i, "fa|lse", c)
            }
            STATE_FAL_SE -> when (c) {
                's' -> STATE_FALS_E
                else -> return tokens.setError(i, "fal|se", c)
            }
            STATE_FALS_E -> when (c) {
                'e' -> {
                    tokens.addToken(i - 4, JsonTokenType.FALSE)
                    STATE_END_OF_VALUE
                }
                else -> return tokens.setError(i, "fals|e", c)
            }
            STATE_STRING -> when (c) {
                '"' -> {
                    // String end
                    if (stringIsKey) {
                        tokens.addToken(i, JsonTokenType.NAME_END)
                        STATE_EXPECT_SEMICOLON
                    } else {
                        tokens.addToken(i, JsonTokenType.STRING_END)
                        STATE_END_OF_VALUE
                    }
                }
                '\\' -> {
                    // Escaping
                    STATE_STRING_ESCAPE
                }
                in 0.toChar()..0x1F.toChar() -> return tokens.setError(i, "string char", c)
                else -> STATE_STRING
            }
            STATE_STRING_ESCAPE -> when (c) {
                '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> {
                    STATE_STRING
                }
                'u' -> {
                    STATE_STRING_ESCAPE_U_XXXX
                }
                else -> return tokens.setError(i, "valid JSON escape", c)
            }
            STATE_STRING_ESCAPE_U_XXXX -> when (c) {
                in '0'..'9', in 'a'..'f', in 'A'..'F' -> STATE_STRING_ESCAPE_UX_XXX
                else -> return tokens.setError(i, "hex digit", c)
            }
            STATE_STRING_ESCAPE_UX_XXX -> when (c) {
                in '0'..'9', in 'a'..'f', in 'A'..'F' -> STATE_STRING_ESCAPE_UXX_XX
                else -> return tokens.setError(i, "hex digit", c)
            }
            STATE_STRING_ESCAPE_UXX_XX -> when (c) {
                in '0'..'9', in 'a'..'f', in 'A'..'F' -> STATE_STRING_ESCAPE_UXXX_X
                else -> return tokens.setError(i, "hex digit", c)
            }
            STATE_STRING_ESCAPE_UXXX_X -> when (c) {
                in '0'..'9', in 'a'..'f', in 'A'..'F' -> STATE_STRING
                else -> return tokens.setError(i, "hex digit", c)
            }
            else -> error("Unexpected state $state")
        }

        if (state == STATE_END_OF_VALUE) {
            state = if (depth <= 0) {
                // Freestanding value, check rest and return
                STATE_END_OF_JSON
            } else if (isDepthForObject[depth - 1]) {
                STATE_EXPECT_OBJECT_COMMA
            } else {
                STATE_EXPECT_LIST_COMMA
            }
        }
    }

    if (state != STATE_END_OF_JSON) {
        error("Unexpected end state ($state)")
    }

    return tokens
}

/**
 * Unescape correctly escaped JSON string content [value].
 */
fun jsonUnescape(value: CharSequence): String {
    val length = value.length
    return buildString(length) {
        var i = 0
        while (i < length) {
            when (val c = value[i++]) {
                '\\' -> when (val e = value[i++]) {// Escape
                    '"', '/', '\\' -> append(e)
                    'b' -> append('\b')
                    'f' -> append(FORM_FEED)
                    'n' -> append('\n')
                    'r' -> append('\r')
                    't' -> append('\t')
                    'u' -> {
                        var codepoint = 0
                        repeat(4) {
                            val d = value[i++].digitToInt(16)
                            codepoint = (codepoint shl 4) or (d and 0xF)
                        }
                        append(codepoint.toChar())
                    }
                }
                else -> append(c)
            }
        }
    }
}

/** Thrown when the [CharSequence] does not represent a valid JSON. */
class JsonParseException : Exception {
    constructor(index: Int, expected: String, got: Char) : this("Expected $expected at index $index, got '${if (got == 0.toChar()) "EOF" else if (got <= 0x1F.toChar()) "0x"+got.code.toString(16) else got}'")
    constructor(message: String): super(message)
}