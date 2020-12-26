package org.nervos.gw

import org.junit.Assert
import org.junit.Test
import org.nervos.gw.utils.HexUtil

class HexUtilTest {

    @Test
    fun testU32LittleEndian() {
        Assert.assertEquals("03000000", HexUtil.u32LittleEndian(3))
        Assert.assertEquals("00040000", HexUtil.u32LittleEndian(1024))
        Assert.assertEquals("01000100", HexUtil.u32LittleEndian(65537))
    }
}