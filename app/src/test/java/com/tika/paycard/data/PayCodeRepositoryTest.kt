package com.tika.paycard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 付款码页面解析的冒烟测试。这是全 App 最依赖外部页面结构的环节,
 * 学校页面一旦改版这里最先失效,用真实结构的样本锁住解析契约。
 */
class PayCodeRepositoryTest {

    private val repo = PayCodeRepository()

    private fun html(code: String = HEX_CODE, bdb: String = "郎振杰：1073325020407 余额：16.43元") =
        """
        <html><body>
        <input type="hidden" id="code" value="$code" />
        <p class="bdb">$bdb</p>
        </body></html>
        """.trimIndent()

    @Test
    fun `完整页面解析出付款码与账户信息`() {
        val r = repo.parse(html())
        assertTrue(r is PayCodeRepository.Result.Ok)
        r as PayCodeRepository.Result.Ok
        assertEquals(HEX_CODE, r.code)
        assertEquals("郎振杰", r.name)
        assertEquals("1073325020407", r.cardNo)
        assertEquals("16.43元", r.balance)
    }

    @Test
    fun `缺少 code 字段判为凭证失效`() {
        val noCode = "<html><body><p class=\"bdb\">郎振杰：1073325020407 余额：16.43元</p></body></html>"
        assertTrue(repo.parse(noCode) is PayCodeRepository.Result.Invalid)
    }

    @Test
    fun `没有账户信息时仍返回付款码`() {
        val onlyCode = "<html><body><input id=\"code\" value=\"$HEX_CODE\" /></body></html>"
        val r = repo.parse(onlyCode)
        assertTrue(r is PayCodeRepository.Result.Ok)
        r as PayCodeRepository.Result.Ok
        assertEquals(HEX_CODE, r.code)
        assertEquals("", r.name)
        assertEquals("", r.balance)
    }

    @Test
    fun `英文冒号与缺余额也能提取姓名卡号`() {
        val r = repo.parse(html(bdb = "张三:1073325020408"))
        assertTrue(r is PayCodeRepository.Result.Ok)
        r as PayCodeRepository.Result.Ok
        assertEquals("张三", r.name)
        assertEquals("1073325020408", r.cardNo)
        assertEquals("", r.balance)
    }

    companion object {
        private const val HEX_CODE = "0a1b2c3d4e5f60718293a4b5c6d7e8f9"
    }
}
