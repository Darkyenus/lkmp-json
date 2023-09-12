import com.darkyen.json.CharSequenceView
import kotlin.test.DefaultAsserter.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CharSequenceViewTest {
    @Test
    fun test() {
        val root = "9990123499"
        val sub = CharSequenceView(root, 3, 5)
        assertEquals("01234", sub.toString())
        assertTrue(sub.equals("01234"))
        assertFalse(sub.equals("012345"))
        assertFalse(sub.equals(1))
        assertEquals("01234".hashCode(), sub.hashCode())
        assertEquals('0', sub[0])
        assertEquals('1', sub[1])
        assertEquals('2', sub[2])
        assertEquals('4', sub[4])
        assertFailsWith(IndexOutOfBoundsException::class) {
            sub[-1]
        }
        assertFailsWith(IndexOutOfBoundsException::class) {
            sub[5]
        }
        assertEquals("12", sub.subSequence(1, 3).toString())
        assertEquals("", sub.subSequence(1, 1))

        assertFailsWith(IndexOutOfBoundsException::class) { sub.subSequence(-1, 2) }
        assertFailsWith(IndexOutOfBoundsException::class) { sub.subSequence(0, 99) }
        assertFailsWith(IndexOutOfBoundsException::class) { sub.subSequence(99, 100) }
        assertFailsWith(IndexOutOfBoundsException::class) { sub.subSequence(99, 3) }
        assertFailsWith(IndexOutOfBoundsException::class) { sub.subSequence(0, -5) }
        assertFailsWith(IndexOutOfBoundsException::class) { sub.subSequence(3, 2) }
    }
}