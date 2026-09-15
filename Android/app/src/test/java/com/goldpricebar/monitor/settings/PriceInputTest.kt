package com.goldpricebar.monitor.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriceInputTest {

    @Test
    fun `parses plain and trimmed decimals`() {
        assertEquals(1049.59, PriceInput.parsePrice("1049.59")!!, 0.0001)
        assertEquals(1049.59, PriceInput.parsePrice("  1049.59  ")!!, 0.0001)
        assertEquals(880.0, PriceInput.parsePrice("880")!!, 0.0001)
    }

    @Test
    fun `rejects blank, non numeric, zero and negative values`() {
        assertNull(PriceInput.parsePrice(null))
        assertNull(PriceInput.parsePrice(""))
        assertNull(PriceInput.parsePrice("   "))
        assertNull(PriceInput.parsePrice("abc"))
        assertNull(PriceInput.parsePrice("0"))
        assertNull(PriceInput.parsePrice("-1"))
        assertNull(PriceInput.parsePrice("NaN"))
        assertNull(PriceInput.parsePrice("Infinity"))
    }

    @Test
    fun `formats prices with two decimals`() {
        assertEquals("1049.60", PriceInput.formatPrice(1049.6))
        assertEquals("1050.00", PriceInput.formatPrice(1050.0))
        assertEquals("0.00", PriceInput.formatPrice(Double.NaN))
    }

    @Test
    fun `formats percentages by truncation`() {
        assertEquals("0.40%", PriceInput.formatPercentFromFraction(0.004))
        assertEquals("-0.40%", PriceInput.formatPercentFromFraction(-0.004))
        assertEquals("0.00%", PriceInput.formatPercentFromFraction(Double.NaN))
    }
}
