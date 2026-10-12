package com.tika.paycard.ui

import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tika.paycard.R
import com.tika.paycard.data.Account
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h320dp-land")
class ShortScreenLayoutTest {

    @Test
    fun `横屏大字号主界面可以滚动到卡片列表`() {
        val root = inflate(R.layout.activity_main)
        val list = root.findViewById<RecyclerView>(R.id.accountList)
        list.layoutManager = LinearLayoutManager(root.context)
        list.adapter = AccountAdapter({}, {}).apply { submit(listOf(Account("first", "9")), 0) }

        measure(root)

        assertTrue(list.measuredHeight > 0)
        assertTrue(root.findViewById<NestedScrollView>(R.id.mainContent).canScrollVertically(1))
    }

    @Test
    fun `横屏付款页二维码保持正方形且按钮可以滚动访问`() {
        val root = inflate(R.layout.activity_pay)

        measure(root)

        val block = root.findViewById<View>(R.id.payQrBlock)
        assertTrue(block.measuredWidth > 0)
        assertEquals(block.measuredWidth, block.measuredHeight)
        assertTrue(root.findViewById<View>(R.id.payRefresh).measuredHeight > 0)
        assertTrue(root.findViewById<NestedScrollView>(R.id.payContent).canScrollVertically(1))
    }

    private fun inflate(layout: Int): View {
        val app = RuntimeEnvironment.getApplication()
        val config = Configuration(app.resources.configuration).apply { fontScale = 1.6f }
        val context = ContextThemeWrapper(app.createConfigurationContext(config), R.style.Theme_PayCard)
        return LayoutInflater.from(context).inflate(layout, null)
    }

    private fun measure(view: View) {
        val density = view.resources.displayMetrics.density
        val width = (640 * density).toInt()
        val height = (320 * density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
    }
}
