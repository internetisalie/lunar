package net.internetisalie.lunar.redis.console

import net.internetisalie.lunar.redis.resp.RespValue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Reply-tree shaping coverage for [RespReplyTreeModel] (design §3.5, TC-CON-1).
 *
 * Pure model test — no fixture. A nested array/map reply shapes into the documented node structure:
 * scalar leaves render inline; arrays/maps become expandable nodes with one child each; map children
 * label as `key = value`.
 */
class TestRespReplyTreeModel {

    /** TC-CON-1: a nested array shapes into 3 children (leaf, expandable pair, expandable map). */
    @Test
    fun testNestedArrayShaping() {
        val reply = RespValue.Array(
            listOf(
                RespValue.Bulk("a".toByteArray(Charsets.UTF_8)),
                RespValue.Array(listOf(RespValue.Integer(1), RespValue.Integer(2))),
                RespValue.Map(
                    listOf(
                        RespValue.Bulk("k".toByteArray(Charsets.UTF_8)) to
                            RespValue.Bulk("v".toByteArray(Charsets.UTF_8)),
                    ),
                ),
            ),
        )

        val root = RespReplyTreeModel.build(reply)
        assertTrue(root.expandable, "root array is expandable")
        assertEquals(3, root.children.size, "root has 3 children")

        val leaf = root.children[0]
        assertFalse(leaf.expandable, "child 0 is a leaf")
        assertEquals("[0] \"a\"", leaf.label)

        val pair = root.children[1]
        assertTrue(pair.expandable, "child 1 is expandable")
        assertEquals(2, pair.children.size, "child 1 has 2 integer leaves")
        assertEquals("[0] 1", pair.children[0].label)
        assertEquals("[1] 2", pair.children[1].label)

        val map = root.children[2]
        assertTrue(map.expandable, "child 2 is expandable")
        assertEquals(1, map.children.size, "child 2 has one entry")
        assertEquals("[0] \"k\" = \"v\"", map.children[0].label)
    }

    /** A scalar reply is a single inline leaf (design §3.5). */
    @Test
    fun testScalarIsInlineLeaf() {
        val root = RespReplyTreeModel.build(RespValue.Integer(42))
        assertFalse(root.expandable)
        assertEquals("42", root.label)
        assertTrue(root.children.isEmpty())
    }

    /** Null bulk / null array render as `(nil)` leaves (design §3.5, §6). */
    @Test
    fun testNilRendering() {
        assertEquals("(nil)", RespReplyTreeModel.build(RespValue.Bulk(null)).label)
        assertEquals("(nil)", RespReplyTreeModel.build(RespValue.Array(null)).label)
        assertEquals("(nil)", RespReplyTreeModel.build(RespValue.Null).label)
    }

    /** A non-UTF-8 bulk payload previews as `<binary N bytes>` (design §3.5). */
    @Test
    fun testBinaryBulkPreview() {
        val binary = RespValue.Bulk(byteArrayOf(0x00, 0x01, 0x02))
        assertEquals("<binary 3 bytes>", RespReplyTreeModel.build(binary).label)
    }
}
