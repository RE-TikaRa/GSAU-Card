package com.tika.paycard.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 选中索引的纯逻辑测试。多卡切换/删除时索引算错是修 bug 高发区,
 * 这里不碰 SharedPreferences,只锁住 clampIndex/nextIndex/indexAfterRemoval 的契约。
 */
class AccountStoreIndexTest {

    @Test
    fun `clampIndex 空列表返回 -1`() {
        assertEquals(-1, AccountStore.clampIndex(0, 0))
        assertEquals(-1, AccountStore.clampIndex(3, 0))
    }

    @Test
    fun `clampIndex 越界夹到合法范围`() {
        assertEquals(2, AccountStore.clampIndex(5, 3))
        assertEquals(0, AccountStore.clampIndex(-1, 3))
        assertEquals(1, AccountStore.clampIndex(1, 3))
    }

    @Test
    fun `nextIndex 环形前进`() {
        assertEquals(1, AccountStore.nextIndex(0, 3))
        assertEquals(2, AccountStore.nextIndex(1, 3))
        assertEquals(0, AccountStore.nextIndex(2, 3))
    }

    @Test
    fun `nextIndex 单卡停在原地`() {
        assertEquals(0, AccountStore.nextIndex(0, 1))
    }

    @Test
    fun `nextIndex 空列表返回 -1`() {
        assertEquals(-1, AccountStore.nextIndex(0, 0))
    }

    @Test
    fun `删当前卡之前的卡 选中索引前移`() {
        // 列表 [A,B,C,D] 选中 C(2),删 A(0),删后 [B,C,D] C 变到 1
        assertEquals(1, AccountStore.indexAfterRemoval(current = 2, removed = 0, sizeAfter = 3))
    }

    @Test
    fun `删当前卡之后的卡 选中索引不动`() {
        // 列表 [A,B,C,D] 选中 B(1),删 D(3),删后 [A,B,C] B 仍是 1
        assertEquals(1, AccountStore.indexAfterRemoval(current = 1, removed = 3, sizeAfter = 3))
    }

    @Test
    fun `删当前选中的卡 索引夹到新末尾`() {
        // 列表 [A,B,C] 选中 C(2),删 C(2),删后 [A,B] 夹到 1
        assertEquals(1, AccountStore.indexAfterRemoval(current = 2, removed = 2, sizeAfter = 2))
    }

    @Test
    fun `删到空列表 索引归零`() {
        assertEquals(0, AccountStore.indexAfterRemoval(current = 0, removed = 0, sizeAfter = 0))
    }
}
