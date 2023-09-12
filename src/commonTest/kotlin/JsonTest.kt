import com.darkyen.json.CharSequenceView
import com.darkyen.json.JsonParseException
import com.darkyen.json.JsonTokenType
import com.darkyen.json.JsonTokens
import com.darkyen.json.JsonValue
import com.darkyen.json.appendJsonString
import com.darkyen.json.appendJsonStringStrict
import com.darkyen.json.parseJson
import com.darkyen.json.tokenizeJson
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

class JsonTest {

    // https://www.ecma-international.org/wp-content/uploads/ECMA-404_2nd_edition_december_2017.pdf

    private fun testIterateJsonTokens(tokens: JsonTokens, token: Int) {
        val tt = tokens.tokenType(token)
        if (tt != JsonTokenType.NAME_BEGIN) {
            assertEquals(tokens.jsonValueAt(token), tokens.jsonValueTokensAt(token).jsonValueAt(0))
        }
        when (tt) {
            JsonTokenType.NULL -> {
                assertEquals(JsonValue.Null, tokens.jsonValueAt(token))
                assertEquals("null", tokens.jsonValueStringAt(token))
                assertEquals(JsonValue.Null, tokens.jsonValueTokensAt(token).jsonValueAt(0))
                assertEquals(4, tokens.tokenCharLength(token))
            }
            JsonTokenType.TRUE -> {
                assertEquals(JsonValue.True, tokens.jsonValueAt(token))
                assertEquals("true", tokens.jsonValueStringAt(token))
                assertEquals(JsonValue.True, tokens.jsonValueTokensAt(token).jsonValueAt(0))
                assertEquals(4, tokens.tokenCharLength(token))
            }
            JsonTokenType.FALSE -> {
                assertEquals(JsonValue.False, tokens.jsonValueAt(token))
                assertEquals("false", tokens.jsonValueStringAt(token))
                assertEquals(JsonValue.False, tokens.jsonValueTokensAt(token).jsonValueAt(0))
                assertEquals(5, tokens.tokenCharLength(token))
            }
            JsonTokenType.NUMBER_BEGIN -> {
                val num = tokens.numberValue(token)
                assertEquals(num, tokens.jsonValueStringAt(token).toDouble(), Double.MIN_VALUE)
                assertEquals(num, tokens.jsonValueAt(token).doubleValue(), Double.MIN_VALUE)
                assertEquals(tokens.jsonValueStringAt(token).length, tokens.tokenCharLength(token))
            }
            JsonTokenType.STRING_BEGIN -> {
                val stringValue = tokens.stringValue(token)
                assertEquals(stringValue, tokens.jsonValueAt(token).stringValue())
                assertEquals(tokens.jsonValueStringAt(token).length, tokens.tokenCharLength(token))

                val sb = StringBuilder()
                sb.appendJsonString(stringValue)
                assertEquals(stringValue, tokenizeJson(sb).stringValue(0))

                sb.setLength(0)
                sb.appendJsonStringStrict(stringValue)
                assertEquals(stringValue, tokenizeJson(sb).stringValue(0))
            }
            JsonTokenType.NAME_BEGIN -> {
                assertEquals(tokens.tokenCharLength(token), tokens.jsonValueStringAt(token).length)
                assertEquals(tokens.jsonValueStringAt(token).length, tokens.tokenCharLength(token))
            }
            JsonTokenType.OBJECT_BEGIN -> {
                assertEquals(1, tokens.tokenCharLength(token))
                val len = tokens.objectFieldCount(token)
                assertEquals(len == 0, tokens.isObjectEmpty(token))
                var index = 0
                val endTokenIndex = tokens.forEachObjectField(token) { nameTokenIndex, valueTokenIndex ->
                    assertEquals(nameTokenIndex, tokens.tokenIndexOfObjectFieldName(token, index))
                    assertEquals(valueTokenIndex, tokens.tokenIndexOfObjectFieldValue(token, index))
                    testIterateJsonTokens(tokens, nameTokenIndex)
                    testIterateJsonTokens(tokens, valueTokenIndex)
                    index += 1
                }
                assertEquals(endTokenIndex - token + 1, tokens.valueTokenLength(token))
            }
            JsonTokenType.ARRAY_BEGIN -> {
                assertEquals(1, tokens.tokenCharLength(token))
                val len = tokens.arrayElementCount(token)
                assertEquals(len == 0, tokens.isArrayEmpty(token))
                var index = 0
                val endTokenIndex = tokens.forEachArrayElement(token) { elementTokenIndex ->
                    assertEquals(elementTokenIndex, tokens.tokenIndexOfArrayElement(token, index))
                    testIterateJsonTokens(tokens, elementTokenIndex)
                    index += 1
                }
                assertEquals(endTokenIndex - token + 1, tokens.valueTokenLength(token))
            }
            else -> {
                fail("Unexpected JsonToken type at $token: $tt")
            }
        }
    }

    private fun testJson(json: String, expectedValue: JsonValue) {
        val parsed = parseJson(json).getOrThrow()
        val tokens = tokenizeJson(json)
        tokens.errorMessage?.let { throw JsonParseException(it) }

        testIterateJsonTokens(tokens, 0)

        val parsedFromTokens = tokens.jsonValueAt(0)

        assertEquals(expectedValue, parsed)
        assertEquals(expectedValue, parsedFromTokens)
        assertEquals(expectedValue.hashCode(), parsed.hashCode())
        val roundTrip = parsed.toString()
        assertEquals(json, roundTrip)

        assertEquals(parsedFromTokens, tokenizeJson(parsedFromTokens.toPrettyString()).jsonValueAt(0))
    }

    private fun badJson(json: String) {
        parseJson(json).fold({
            fail("Parsing '$json' should have failed")
        }, {
            if (it !is JsonParseException) {
                fail("Expected JsonParseException, got $it")
            }
        })

        val tokens = tokenizeJson(json)
        assertNotNull(tokens.errorMessage)
    }

    @Test
    fun simple() {
        testJson("true", JsonValue.True)
        testJson("false", JsonValue.False)
        testJson("null", JsonValue.Null)
        testJson("0", JsonValue.Number(0))
        testJson("1", JsonValue.Number(1))
        testJson("1e1", JsonValue.Number(10.0))
        testJson("1E1", JsonValue.Number(10.0))
        testJson("1E+1", JsonValue.Number(10.0))
        testJson("1E-1", JsonValue.Number(0.1))
        testJson("1.0E+1", JsonValue.Number(10.0))
        testJson("1.0E-1", JsonValue.Number(0.1))
        testJson("1.5", JsonValue.Number(1.5))
        testJson("-0", JsonValue.Number(-0))
        testJson("-555", JsonValue.Number(-555))
        testJson("-555.5", JsonValue.Number(-555.5))
        testJson("[]", JsonValue.Array(emptyList()))
        testJson("[true,false]", JsonValue.Array(listOf(JsonValue.True, JsonValue.False)))
        testJson("[0,1,2]", JsonValue.Array(listOf(JsonValue.Number(0), JsonValue.Number(1), JsonValue.Number(2))))
        testJson("{}", JsonValue.Object(emptyList()))
        testJson("{\"\":\"\"}", JsonValue.Object("" to JsonValue.String("")))
        testJson("{\"\":9}", JsonValue.Object("" to JsonValue.Number(9)))
        testJson("{\"foo\":9}", JsonValue.Object("foo" to JsonValue.Number(9)))
        testJson("{\"foo\":9,\"bar\":10}", JsonValue.Object("foo" to JsonValue.Number(9), "bar" to JsonValue.Number(10)))
        testJson("\"\"", JsonValue.String(""))
        testJson("\"hello\"", JsonValue.String("hello"))
        testJson("\"hello\\nworld\"", JsonValue.String("hello\nworld"))
        testJson("\"\\u0000\"", JsonValue.String("\u0000"))
        testJson("\"\\u9999\"", JsonValue.String("\u9999"))
        testJson("\"\\uaaaa\"", JsonValue.String("\uaaaa"))
        testJson("\"\\uffff\"", JsonValue.String("\uffff"))
        testJson("\"\\uAAAA\"", JsonValue.String("\uaaaa"))
        testJson("\"\\uFFFF\"", JsonValue.String("\uffff"))
        testJson("\"\\u2222\"", JsonValue.String("\u2222"))
        testJson("\"\\ubbbb\"", JsonValue.String("\ubbbb"))
        testJson("\"\\uBBBB\"", JsonValue.String("\uBBBB"))
        testJson("\"\\\"\"", JsonValue.String("${0x22.toChar()}"))
        testJson("\"\\/\"", JsonValue.String("${0x2F.toChar()}"))
        testJson("\"\\\\\"", JsonValue.String("${0x5C.toChar()}"))
        testJson("\"\\b\"", JsonValue.String("${0x8.toChar()}"))
        testJson("\"\\f\"", JsonValue.String("${0xC.toChar()}"))
        testJson("\"\\n\"", JsonValue.String("${0xA.toChar()}"))
        testJson("\"\\r\"", JsonValue.String("${0xD.toChar()}"))
        testJson("\"\\t\"", JsonValue.String("${0x9.toChar()}"))
        testJson("""{"a":true,"b":false,"c":null,"d":0,"e":99.99,"f":[],"g":{},"h":[true,null,0],"i":{"ia":1,"ib":["hello"]}}""",
            JsonValue.Object("a" to JsonValue.True, "b" to JsonValue.False, "c" to JsonValue.Null, "d" to JsonValue.Number(0), "e" to JsonValue.Number(99.99),
                "f" to JsonValue.Array(), "g" to JsonValue.Object(), "h" to JsonValue.Array(JsonValue.True, JsonValue.Null, JsonValue.Number(0)), "i" to JsonValue.Object("ia" to JsonValue.Number(1), "ib" to JsonValue.Array(JsonValue.String("hello")))))
        testJson("""[0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70,71,72,73]""",
            JsonValue.Array((0..73).map { JsonValue.Number(it) }))
    }

    @Test
    fun numberParsing() {
        assertEquals(
        5.0,
            tokenizeJson("5").numberValue(0)
        )
        assertEquals(
            5.0,
            tokenizeJson("5.0").numberValue(0)
        )
        assertEquals(
            5.0,
            tokenizeJson("5.0000").numberValue(0)
        )

        assertEquals("5", CharSequenceView("_5_", 1, 1).substring(0, 1))
    }

    @Test
    fun bad() {
        badJson("")
        badJson("[")
        badJson("[1")
        badJson("[1,")
        badJson("{")
        badJson("{1")
        badJson("{1,")
        badJson("-")
        badJson("+0")
        badJson("+1")
        badJson("01")
        badJson("\u0000")
        badJson("0.")
        badJson("1.")
        badJson("1e")
        badJson("1E")
        badJson("1E+")
        badJson("1E-")
        badJson("f")
        badJson("fa")
        badJson("fal")
        badJson("fals")
        badJson("t")
        badJson("tr")
        badJson("tru")
        badJson("n")
        badJson("nu")
        badJson("nul")
        badJson("\"")
        badJson("\"a")
        badJson("\"a\\")
        badJson("\"a\\u")
        badJson("\"a\\u1")
        badJson("\"a\\u1F")
        badJson("\"a\\u1FD")
        badJson("\"a\\u1FDF")
    }

    @Test
    fun generatedStrings() {
        val random = Random(1234)
        repeat(100) {
            val json = random.generateJsonString(false)
            val parsed = parseJson(json).getOrThrow()
            if (parsed !is JsonValue.String) fail("Expected a string")
            val roundTrip = parsed.toString()
            assertEquals(json, roundTrip)
        }
    }

    @Test
    fun generatedNumbers() {
        val random = Random(1234)
        repeat(100) {
            val json = random.generateJsonNumber(false)
            val parsed = parseJson(json).getOrThrow()
            if (parsed !is JsonValue.Number) fail("Expected a number")
            val roundTrip = parsed.toString()
            assertEquals(json, roundTrip)
        }
    }

    @Test
    fun generated() {
        val random = Random(1234)
        repeat(1000) { iteration ->
            val json = random.generateJsonValue(3, 0)
            val parsed = parseJson(json).fold({it}, { f ->
                fail("($iteration) Failed to parse '$json': $f")
            })
            val roundTrip = parsed.toString()
            assertEquals(json, roundTrip)
        }
    }


    @Test
    fun generatedWhitespace() {
        val random = Random(1234)
        repeat(100) { iteration ->
            val json = random.generateJsonValue(3, 1)
            val parsed = parseJson(json).fold({it}, { f ->
                fail("($iteration) Failed to parse '$json': $f")
            })
            val roundTrip = parsed.toString()
            assertEquals(json.filterNot { it in WHITESPACE }, roundTrip.filterNot { it in WHITESPACE })
        }
    }

    @Test
    fun apiIsReasonable() {
        val j = parseJson("""
            {
                "status": 200,
                "url": "https://example.com",
                "users": [
                    { "name": "Ada", "age": 50 },
                    { "name": "Charles", "age": 70 }
                ]
            }
        """.trimIndent()).getOrThrow()
        assertEquals(200, j["status"]?.nullableIntValue())
        assertEquals("https://example.com", j["url"]?.nullableStringValue())
        val users = j["users"]
        assertNotNull(users)

        for (user in users.take(1)) {
            assertEquals("Ada", user["name"]!!.asString())
        }
    }
}

const val WHITESPACE = " \t\n\r"

fun Random.generateJsonString(simple: Boolean): String {
    return buildString {// strings
        append('"')
        if (simple) {
            repeat(nextInt(5)) {
                var c = nextInt(' '.code, 0x7F).toChar()
                if (c == '\\' || c == '"') c = '\''
                append(c)
            }
        } else {
            repeat(nextInt(50)) {
                when (nextInt(4)) {
                    0 -> when (nextInt()) {
                        0 -> append("\\b")
                        1 -> append("\\f")
                        2 -> append("\\n")
                        3 -> append("\\r")
                        4 -> append("\\t")
                    }

                    1 -> append("\\u")
                        .append(nextInt(0x10).toString(16))
                        .append(nextInt(0x10).toString(16))
                        .append(nextInt(0x10).toString(16))
                        .append(nextInt(0x10).toString(16))

                    2 -> {
                        // Somewhat normal letters
                        var c = nextInt(' '.code, 0x2E80).toChar()
                        if (c == '\\' || c == '"') c = '\''
                        append(c)
                    }
                    3 -> {
                        // Emoji
                        val codepoint = nextInt(0x1F600, 0x1F650)
                        val leading = 0xD800 + codepoint ushr 10
                        val trailing = 0xDC00 + codepoint and 0b1111111111
                        append(leading).append(trailing)
                    }
                }
            }
        }
        append('"')
    }
}

fun Random.generateJsonNumber(simple: Boolean): String = buildString {
    val len = if (simple) 3 else 20
    if (nextBoolean()) {
        append('-')
    }
    if (nextBoolean()) {
        append('0')
    } else {
        append(nextInt('1'.code, '9'.code + 1).toChar())
        repeat(nextInt(len)) {
            append(nextInt('0'.code, '9'.code + 1).toChar())
        }
    }
    if (nextBoolean()) {
        append('.')
        repeat(1+nextInt(len)) {
            append(nextInt('0'.code, '9'.code + 1).toChar())
        }
    }
    if (nextBoolean()) {
        append(if (nextBoolean()) 'e' else 'E')
        when (nextInt(3)) {
            0 -> append('+')
            1 -> append('-')
        }
        repeat(1+nextInt(len)) {
            append(nextInt('0'.code, '9'.code + 1).toChar())
        }
    }
}

fun Random.generateJsonValue(nesting: Int, maxWhitespace: Int): String = buildString {
    fun appendWS() {
        if (maxWhitespace <= 0) return
        repeat(nextInt(maxWhitespace + 1)) {
            append(WHITESPACE[nextInt(WHITESPACE.length)])
        }
    }

    appendWS()
    when (nextInt(5)) {
        0 -> when (nextInt(3)) {// literals
            0 -> append("null")
            1 -> append("true")
            else -> append("false")
        }
        1 -> append(generateJsonString(true))
        2 -> append(generateJsonNumber(true))
        3 -> {// lists
            appendWS()
            append('[')
            appendWS()
            if (nesting > 0) {
                var comma = false
                repeat(1+nextInt(10)) {
                    if (comma) {
                        appendWS()
                        append(',')
                    }
                    appendWS()
                    append(generateJsonValue(nesting - 1, maxWhitespace))
                    appendWS()
                    comma = true
                }
            }
            appendWS()
            append(']')
            appendWS()
        }
        else/*4*/ -> {// objects
            appendWS()
            append('{')
            appendWS()
            if (nesting > 0) {
                var comma = false
                repeat(1+nextInt(10)) {
                    if (comma) {
                        append(',')
                        appendWS()
                    }
                    append(generateJsonString(true))
                    appendWS()
                    append(':')
                    appendWS()
                    append(generateJsonValue(nesting - 1, maxWhitespace))
                    comma = true
                }
            }
            appendWS()
            append('}')
            appendWS()
        }
    }
    appendWS()
}