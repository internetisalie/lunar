package net.internetisalie.lunar.lang

import net.internetisalie.lunar.lang.indexing.DescriptionRecord
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * BUG-408: a separator occurring in a file path must not change a record's arity.
 *
 * `ownerName` was already sanitised; `fileUrl` was interpolated raw, and a tab, `|`, newline or
 * carriage return is legal in a POSIX filename. Such a record split to the wrong arity and the
 * reader dropped it via `if (parts.size != 3) continue` — the file's documentation disappeared from
 * Search Everywhere with no error anywhere.
 *
 * Sanitising the URL is not available: it must round-trip or the file cannot be reopened.
 */
class LuaDescriptionRecordTest {

    private fun roundTrip(record: DescriptionRecord) {
        val parsed = DescriptionRecord.parseAll(record.encode())
        assertEquals("exactly one record for '${record.fileUrl}'", 1, parsed.size)
        assertEquals(record, parsed.single())
    }

    @Test
    fun ordinaryRecordRoundTrips() {
        roundTrip(DescriptionRecord("Vector", "file:///src/vector.lua", 42))
    }

    /** The defect: each of these used to split to 4+ fields and be silently dropped. */
    @Test
    fun separatorsInThePathRoundTrip() {
        roundTrip(DescriptionRecord("Vector", "file:///src/we\tird.lua", 1))
        roundTrip(DescriptionRecord("Vector", "file:///src/pipe|d.lua", 2))
        roundTrip(DescriptionRecord("Vector", "file:///src/new\nline.lua", 3))
        roundTrip(DescriptionRecord("Vector", "file:///src/carriage\rreturn.lua", 4))
    }

    /**
     * The escape must be a bijection. Without escaping `%` first, a path containing the literal
     * text `%09` would decode into a tab — a corruption the naive escape would introduce.
     */
    @Test
    fun percentIsNotAmbiguous() {
        roundTrip(DescriptionRecord("Vector", "file:///src/100%25real.lua", 5))
        roundTrip(DescriptionRecord("Vector", "file:///src/%09not-a-tab.lua", 6))
        roundTrip(DescriptionRecord("Vector", "file:///src/%7C.lua", 7))
    }

    /** A separator in the name is escaped now rather than replaced, so the name survives intact. */
    @Test
    fun separatorsInTheNameRoundTrip() {
        roundTrip(DescriptionRecord("od\td|name", "file:///src/x.lua", 8))
    }

    @Test
    fun multipleRecordsSurviveJoinAndParse() {
        val records = listOf(
            DescriptionRecord("Vector", "file:///src/we\tird.lua", 1),
            DescriptionRecord("Matrix", "file:///src/pipe|d.lua", 2),
        )
        assertEquals(records, DescriptionRecord.parseAll(DescriptionRecord.join(records)))
    }

    /** Values written by an older plugin version are data we do not control — skip, never throw. */
    @Test
    fun malformedRecordsAreSkipped() {
        assertEquals(emptyList<DescriptionRecord>(), DescriptionRecord.parseAll("only\ttwo"))
        assertEquals(emptyList<DescriptionRecord>(), DescriptionRecord.parseAll("a\tb\tnot-a-number"))
    }
}
