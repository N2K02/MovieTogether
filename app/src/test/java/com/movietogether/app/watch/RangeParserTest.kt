package com.movietogether.app.watch

import org.junit.Assert.*
import org.junit.Test

class RangeParserTest {
    @Test fun fullFile() = assertEquals(ByteRange(0, 99), RangeParser.parse(null, 100))
    @Test fun forwardRange() = assertEquals(ByteRange(5, 9), RangeParser.parse("bytes=5-9", 100))
    @Test fun openRange() = assertEquals(ByteRange(90, 99), RangeParser.parse("bytes=90-", 100))
    @Test fun suffixRange() = assertEquals(ByteRange(97, 99), RangeParser.parse("bytes=-3", 100))
    @Test fun clippedRange() = assertEquals(ByteRange(90, 99), RangeParser.parse("bytes=90-400", 100))
    @Test fun invalidRange() {
        assertNull(RangeParser.parse("bytes=50-40", 100))
        assertNull(RangeParser.parse("bytes=100-", 100))
        assertNull(RangeParser.parse("bytes=0-1,5-9", 100))
        assertNull(RangeParser.parse("bytes=-0", 100))
        assertNull(RangeParser.parse("bytes=0-", 0))
    }
    @Test fun length() = assertEquals(30L, ByteRange(10, 39).length)
}
