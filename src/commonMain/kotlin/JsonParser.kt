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

@Throws(JsonParseException::class)
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

/**
 * Unescape correctly escaped JSON string content [value].
 */
internal fun jsonUnescape(value: CharSequence): CharSequence {
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
    constructor(cause: Throwable) : super(cause)
}