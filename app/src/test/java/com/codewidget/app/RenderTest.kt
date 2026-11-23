package com.codewidget.app

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** ウィジェット JSON を実際に描画して PNG を書き出す（見た目の確認用） */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "xxhdpi")
class RenderTest {
    private fun renderFixture(name: String, w: Int, h: Int, dark: Boolean = false) {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val json = javaClass.classLoader!!.getResource("$name.json").readText()
        val r = WidgetRenderer(ctx, dark)
        val bmp = r.render(json, w, h)
        assertEquals((w * 3f).toInt(), bmp.width)
        val out = File("build/render-test").apply { mkdirs() }
        File(out, "$name${if (dark) "_dark" else ""}_${w}x$h.png").outputStream().use {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun weatherSmall() = renderFixture("sample_weather", 170, 170)
    @Test fun memoMedium() = renderFixture("sample_memo", 340, 170)
    @Test fun memoMediumDark() = renderFixture("sample_memo", 340, 170, dark = true)
    @Test fun misc() = renderFixture("t1", 170, 170)

    @Test fun error() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val r = WidgetRenderer(ctx, false)
        val bmp = r.render(r.errorJson("通信エラー: timeout"), 170, 170)
        File("build/render-test/error.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
