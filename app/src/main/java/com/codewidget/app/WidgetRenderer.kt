package com.codewidget.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.Base64
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * runtime.js が作ったウィジェットの JSON を、Android の View で組み立てて 1 枚の画像にする。
 * 単位は Scriptable の pt をそのまま dp として扱う。
 */
class WidgetRenderer(private val context: Context, private val dark: Boolean) {
    private val density = context.resources.displayMetrics.density
    private val defaultText = if (dark) Color.WHITE else Color.BLACK
    private val defaultBg = if (dark) 0xFF1C1C1E.toInt() else Color.WHITE
    private var maxWPx = 0
    private var maxHPx = 0

    private fun px(dp: Double): Int = (dp * density).roundToInt()
    private fun pxf(dp: Double): Float = (dp * density).toFloat()

    fun cornerRadiusPx(): Float =
        if (Build.VERSION.SDK_INT >= 31) {
            context.resources.getDimension(android.R.dimen.system_app_widget_background_radius)
        } else 16f * density

    fun render(json: String, wDp: Int, hDp: Int): Bitmap {
        val root = JSONObject(json)
        val wPx = max(1, px(wDp.toDouble()))
        val hPx = max(1, px(hDp.toDouble()))
        maxWPx = wPx; maxHPx = hPx

        val frame = FrameLayout(context)
        val hasBg = root.has("bg") && !root.isNull("bg")
        frame.background = BoxDrawable(
            color = if (hasBg) parseColor(root.optString("bg")) else defaultBg,
            grad = root.optJSONObject("grad"),
            image = root.optJSONObject("bgImg")?.let { decodeImage(it, wPx, hPx) },
            radius = 0f, borderW = 0f, borderC = 0,
        )
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
        }
        applyPadding(content, root.optJSONArray("pad"), 16.0)
        addChildren(content, root.optJSONArray("children"), false, root.optDouble("spacing", 0.0))
        frame.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        frame.measure(
            View.MeasureSpec.makeMeasureSpec(wPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(hPx, View.MeasureSpec.EXACTLY),
        )
        frame.layout(0, 0, wPx, hPx)

        val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val r = cornerRadiusPx()
        val clip = Path().apply { addRoundRect(RectF(0f, 0f, wPx.toFloat(), hPx.toFloat()), r, r, Path.Direction.CW) }
        canvas.clipPath(clip)
        frame.draw(canvas)
        return bmp
    }

    /** エラー表示用のウィジェット JSON */
    fun errorJson(message: String): String {
        fun text(t: String, size: Double, weight: Int, color: String, lines: Int) = JSONObject().apply {
            put("t", "text"); put("text", t); put("color", color); put("lines", lines)
            put("font", JSONObject().put("s", size).put("w", weight))
        }
        return JSONObject().apply {
            put("t", "list")
            put("pad", JSONArray(listOf(12, 12, 12, 12)))
            put("children", JSONArray().apply {
                put(text("⚠️ スクリプトエラー", 13.0, 700, "#FFFF3B30", 1))
                put(JSONObject().put("t", "spacer").put("len", 4))
                put(text(message, 11.0, 400, if (dark) "#FFDDDDDD" else "#FF333333", 8))
            })
        }.toString()
    }

    // ------------------------------------------------------------------ レイアウト
    private fun applyPadding(v: View, pad: JSONArray?, def: Double) {
        if (pad != null && pad.length() == 4) {
            v.setPadding(px(pad.optDouble(1)), px(pad.optDouble(0)), px(pad.optDouble(3)), px(pad.optDouble(2)))
        } else {
            val p = px(def); v.setPadding(p, p, p, p)
        }
    }

    private fun addChildren(parent: LinearLayout, arr: JSONArray?, horizontal: Boolean, spacing: Double) {
        if (arr == null) return
        for (i in 0 until arr.length()) {
            val node = arr.optJSONObject(i) ?: continue
            val (view, lp) = build(node, horizontal)
            if (i > 0 && spacing > 0) {
                if (horizontal) lp.leftMargin += px(spacing) else lp.topMargin += px(spacing)
            }
            parent.addView(view, lp)
        }
    }

    private fun alignGravity(align: String?): Int? = when (align) {
        "center" -> Gravity.CENTER_HORIZONTAL
        "right" -> Gravity.END
        else -> null
    }

    private fun build(n: JSONObject, parentHorizontal: Boolean): Pair<View, LinearLayout.LayoutParams> {
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        return when (n.optString("t")) {
            "spacer" -> {
                val v = View(context)
                val lp = if (n.isNull("len")) {
                    LinearLayout.LayoutParams(0, 0, 1f)
                } else {
                    val l = px(n.optDouble("len", 0.0))
                    if (parentHorizontal) LinearLayout.LayoutParams(l, 0) else LinearLayout.LayoutParams(0, l)
                }
                v to lp
            }
            "text" -> {
                val tv = buildText(n)
                val lp = LinearLayout.LayoutParams(wrap, wrap)
                if (!parentHorizontal) alignGravity(n.optString("align").ifEmpty { null })?.let { lp.gravity = it }
                tv to lp
            }
            "image" -> buildImage(n, parentHorizontal)
            "stack" -> buildStack(n, parentHorizontal)
            else -> View(context) to LinearLayout.LayoutParams(0, 0)
        }
    }

    private fun fixedSize(n: JSONObject, index: Int): Double? =
        n.optJSONArray("size")?.optDouble(index, 0.0)?.takeIf { it > 0 }

    /** スタックが（中の伸縮スペーサーによって）その方向へ広がるか */
    private fun expands(n: JSONObject, horizontal: Boolean): Boolean {
        if (n.optString("t") != "stack") return false
        if (fixedSize(n, if (horizontal) 0 else 1) != null) return false
        val children = n.optJSONArray("children") ?: return false
        val sameAxis = (n.optString("layout") == "h") == horizontal
        for (i in 0 until children.length()) {
            val c = children.optJSONObject(i) ?: continue
            if (sameAxis && c.optString("t") == "spacer" && c.isNull("len")) return true
            if (expands(c, horizontal)) return true
        }
        return false
    }

    private fun buildStack(n: JSONObject, parentHorizontal: Boolean): Pair<View, LinearLayout.LayoutParams> {
        val horizontal = n.optString("layout") == "h"
        val align = n.optString("align", "center")
        val ll = LinearLayout(context)
        ll.orientation = if (horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        ll.gravity = if (horizontal) {
            Gravity.CENTER_HORIZONTAL or when (align) { "start" -> Gravity.TOP; "end" -> Gravity.BOTTOM; else -> Gravity.CENTER_VERTICAL }
        } else {
            Gravity.CENTER_VERTICAL or when (align) { "start" -> Gravity.START; "end" -> Gravity.END; else -> Gravity.CENTER_HORIZONTAL }
        }
        applyPadding(ll, n.optJSONArray("pad"), 0.0)
        val hasBg = n.has("bg") && !n.isNull("bg")
        val grad = n.optJSONObject("grad")
        val bgImgJson = n.optJSONObject("bgImg")
        val radius = n.optDouble("radius", 0.0)
        val bw = n.optDouble("bw", 0.0)
        if (hasBg || grad != null || bgImgJson != null || bw > 0) {
            ll.background = BoxDrawable(
                color = if (hasBg) parseColor(n.optString("bg")) else Color.TRANSPARENT,
                grad = grad,
                image = bgImgJson?.let { decodeImage(it, maxWPx, maxHPx) },
                radius = pxf(radius), borderW = pxf(bw),
                borderC = if (n.isNull("bc")) defaultText else parseColor(n.optString("bc")),
            )
        }
        addChildren(ll, n.optJSONArray("children"), horizontal, n.optDouble("spacing", 0.0))

        val fw = fixedSize(n, 0)?.let { px(it) }
        val fh = fixedSize(n, 1)?.let { px(it) }
        val eh = expands(n, true)
        val ev = expands(n, false)
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        val lp = if (parentHorizontal) {
            LinearLayout.LayoutParams(if (eh) 0 else fw ?: wrap, if (ev) match else fh ?: wrap, if (eh) 1f else 0f)
        } else {
            LinearLayout.LayoutParams(if (eh) match else fw ?: wrap, if (ev) 0 else fh ?: wrap, if (ev) 1f else 0f)
        }
        return ll to lp
    }

    // ------------------------------------------------------------------ テキスト
    private fun typeface(font: JSONObject?): Typeface {
        val weight = font?.optInt("w", 400) ?: 400
        val italic = font?.optBoolean("i") ?: false
        val base = if (font?.optBoolean("m") == true) Typeface.MONOSPACE else Typeface.DEFAULT
        return if (Build.VERSION.SDK_INT >= 28) {
            Typeface.create(base, weight.coerceIn(1, 1000), italic)
        } else {
            val style = when {
                weight >= 600 && italic -> Typeface.BOLD_ITALIC
                weight >= 600 -> Typeface.BOLD
                italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            Typeface.create(base, style)
        }
    }

    private fun buildText(n: JSONObject): TextView {
        val tv = TextView(context)
        tv.text = n.optString("text")
        val font = n.optJSONObject("font")
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, (font?.optDouble("s", 17.0) ?: 17.0).toFloat())
        tv.typeface = typeface(font)
        tv.includeFontPadding = false
        val base = if (n.isNull("color") || !n.has("color")) defaultText else parseColor(n.optString("color"))
        tv.setTextColor(withOpacity(base, n.optDouble("opacity", 1.0)))
        val lines = n.optInt("lines", 0)
        if (lines > 0) {
            tv.maxLines = lines
            tv.ellipsize = TextUtils.TruncateAt.END
        }
        tv.gravity = when (n.optString("align")) {
            "center" -> Gravity.CENTER_HORIZONTAL
            "right" -> Gravity.END
            else -> Gravity.START
        }
        n.optJSONObject("shadow")?.let {
            tv.setShadowLayer(max(0.1f, pxf(it.optDouble("r", 0.0))), pxf(it.optDouble("x", 0.0)), pxf(it.optDouble("y", 0.0)), parseColor(it.optString("c")))
        }
        return tv
    }

    private fun withOpacity(c: Int, opacity: Double): Int {
        val a = (Color.alpha(c) * opacity.coerceIn(0.0, 1.0)).roundToInt()
        return Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))
    }

    // ------------------------------------------------------------------ 画像
    private fun buildImage(n: JSONObject, parentHorizontal: Boolean): Pair<View, LinearLayout.LayoutParams> {
        val iv = ImageView(context)
        val src = n.optJSONObject("img")?.let { decodeImage(it, maxWPx, maxHPx) }
        val natW = n.optDouble("w", 0.0).takeIf { it > 0 } ?: 24.0
        val natH = n.optDouble("h", 0.0).takeIf { it > 0 } ?: 24.0
        var wPx: Int
        var hPx: Int
        val size = n.optJSONArray("size")
        if (size != null) {
            wPx = px(size.optDouble(0)); hPx = px(size.optDouble(1))
        } else {
            wPx = px(natW); hPx = px(natH)
            // 大きすぎる画像は縦横比を保って枠に収める
            val s = min(1.0, min(maxWPx.toDouble() / wPx, maxHPx.toDouble() / hPx))
            wPx = max(1, (wPx * s).roundToInt()); hPx = max(1, (hPx * s).roundToInt())
        }
        wPx = max(1, wPx); hPx = max(1, hPx)
        if (src != null) {
            iv.setImageBitmap(
                composeImage(
                    src, wPx, hPx, fill = n.optString("mode") == "fill",
                    radius = pxf(n.optDouble("radius", 0.0)),
                    tint = if (n.isNull("tint") || !n.has("tint")) (if (n.optJSONObject("img")?.has("sym") == true) defaultText else null) else parseColor(n.optString("tint")),
                    borderW = pxf(n.optDouble("bw", 0.0)),
                    borderC = if (n.isNull("bc") || !n.has("bc")) defaultText else parseColor(n.optString("bc")),
                    isSymbol = n.optJSONObject("img")?.has("sym") == true,
                ),
            )
        }
        iv.scaleType = ImageView.ScaleType.FIT_XY
        iv.alpha = n.optDouble("opacity", 1.0).toFloat().coerceIn(0f, 1f)
        val lp = LinearLayout.LayoutParams(wPx, hPx)
        if (!parentHorizontal) alignGravity(n.optString("align").ifEmpty { null })?.let { lp.gravity = it }
        return iv to lp
    }

    private fun composeImage(
        src: Bitmap, w: Int, h: Int, fill: Boolean, radius: Float, tint: Int?,
        borderW: Float, borderC: Int, isSymbol: Boolean,
    ): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val sx = w.toFloat() / src.width
        val sy = h.toFloat() / src.height
        val s = if (fill) max(sx, sy) else min(sx, sy)
        val dw = src.width * s
        val dh = src.height * s
        val dst = RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f)
        val bounds = RectF(0f, 0f, w.toFloat(), h.toFloat())
        c.save()
        if (radius > 0) c.clipPath(Path().apply { addRoundRect(if (fill) bounds else dst, radius, radius, Path.Direction.CW) })
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        // 絵文字で代用した SF Symbols は色付けしない
        if (tint != null && !(isSymbol && looksLikeEmoji(src))) paint.colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN)
        c.drawBitmap(src, null, dst, paint)
        c.restore()
        if (borderW > 0) {
            val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = borderW; color = borderC }
            val r = RectF(if (fill) bounds else dst).apply { inset(borderW / 2, borderW / 2) }
            c.drawRoundRect(r, radius, radius, bp)
        }
        return out
    }

    private val emojiBitmaps = HashSet<Bitmap>()
    private fun looksLikeEmoji(b: Bitmap) = b in emojiBitmaps

    fun decodeImage(o: JSONObject, maxW: Int, maxH: Int): Bitmap? = try {
        when {
            o.has("b64") -> {
                val bytes = Base64.decode(o.getString("b64"), Base64.DEFAULT)
                val bo = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bo)
                var sample = 1
                val limit = max(maxW, maxH).coerceAtLeast(256) * 2
                while (bo.outWidth / (sample * 2) >= limit || bo.outHeight / (sample * 2) >= limit) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            }
            o.has("draw") -> DrawRenderer(density, defaultText).render(o.getJSONObject("draw"), this)
            o.has("sym") -> {
                val sym = o.getJSONObject("sym")
                val (glyph, emoji) = SymbolMap.glyph(sym.optString("name"))
                val bmp = renderGlyph(glyph, sym.optInt("weight", 400))
                if (emoji) emojiBitmaps.add(bmp)
                bmp
            }
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    private fun renderGlyph(glyph: String, weight: Int): Bitmap {
        val size = 96
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size * 0.8f
            color = Color.BLACK
            typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, weight.coerceIn(1, 1000), false)
            else if (weight >= 600) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
        }
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val fm = p.fontMetrics
        c.drawText(glyph, size / 2f, size / 2f - (fm.ascent + fm.descent) / 2f, p)
        return b
    }

    // ------------------------------------------------------------------ 背景
    /** 単色・グラデーション・画像・角丸・枠線をまとめて描く背景 */
    class BoxDrawable(
        private val color: Int,
        private val grad: JSONObject?,
        private val image: Bitmap?,
        private val radius: Float,
        private val borderW: Float,
        private val borderC: Int,
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

        override fun draw(canvas: Canvas) {
            val b = RectF(bounds)
            if (b.width() <= 0 || b.height() <= 0) return
            paint.shader = null
            paint.color = color
            when {
                image != null -> {
                    val s = max(b.width() / image.width, b.height() / image.height)
                    val m = Matrix().apply {
                        setScale(s, s)
                        postTranslate(b.left + (b.width() - image.width * s) / 2f, b.top + (b.height() - image.height * s) / 2f)
                    }
                    paint.shader = BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) }
                    paint.color = Color.BLACK
                }
                grad != null -> {
                    val cols = grad.optJSONArray("colors")
                    if (cols != null && cols.length() > 0) {
                        val colors = IntArray(max(2, cols.length())) { i -> parseColor(cols.optString(min(i, cols.length() - 1))) }
                        val locs = grad.optJSONArray("locations")?.let { l ->
                            if (l.length() == colors.size) FloatArray(l.length()) { l.optDouble(it).toFloat() } else null
                        }
                        paint.shader = LinearGradient(
                            b.left + grad.optDouble("sx", 0.0).toFloat() * b.width(),
                            b.top + grad.optDouble("sy", 0.0).toFloat() * b.height(),
                            b.left + grad.optDouble("ex", 0.0).toFloat() * b.width(),
                            b.top + grad.optDouble("ey", 1.0).toFloat() * b.height(),
                            colors, locs, Shader.TileMode.CLAMP,
                        )
                        paint.color = Color.BLACK
                    }
                }
            }
            if (paint.shader != null || Color.alpha(color) > 0) canvas.drawRoundRect(b, radius, radius, paint)
            if (borderW > 0) {
                border.strokeWidth = borderW
                border.color = borderC
                val r = RectF(b).apply { inset(borderW / 2, borderW / 2) }
                canvas.drawRoundRect(r, radius, radius, border)
            }
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    companion object {
        fun parseColor(s: String?): Int = try {
            if (s.isNullOrEmpty()) Color.BLACK else Color.parseColor(s)
        } catch (e: Exception) {
            Color.BLACK
        }
    }
}

/** DrawContext（自由描画）の命令列を Canvas で再生する */
class DrawRenderer(private val density: Float, private val defaultText: Int) {
    fun render(d: JSONObject, owner: WidgetRenderer): Bitmap {
        val w = d.optDouble("w", 200.0).toFloat().coerceIn(1f, 2000f)
        val h = d.optDouble("h", 200.0).toFloat().coerceIn(1f, 2000f)
        val scale = min(density, 2048f / max(w, h))
        val bmp = Bitmap.createBitmap((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(scale, scale)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.BLACK }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.BLACK; strokeWidth = 1f }
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 17f }
        var align = Layout.Alignment.ALIGN_NORMAL
        var path = Path()

        val ops = d.optJSONArray("ops") ?: JSONArray()
        for (i in 0 until ops.length()) {
            val op = ops.optJSONArray(i) ?: continue
            fun f(k: Int) = op.optDouble(k, 0.0).toFloat()
            fun rect() = RectF(f(1), f(2), f(1) + f(3), f(2) + f(4))
            when (op.optString(0)) {
                "fill" -> fill.color = WidgetRenderer.parseColor(op.optString(1))
                "stroke" -> stroke.color = WidgetRenderer.parseColor(op.optString(1))
                "lw" -> stroke.strokeWidth = f(1)
                "tc" -> text.color = WidgetRenderer.parseColor(op.optString(1))
                "font" -> op.optJSONObject(1)?.let { fo ->
                    text.textSize = fo.optDouble("s", 17.0).toFloat()
                    val wgt = fo.optInt("w", 400)
                    val base = if (fo.optBoolean("m")) Typeface.MONOSPACE else Typeface.DEFAULT
                    text.typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(base, wgt.coerceIn(1, 1000), fo.optBoolean("i"))
                    else Typeface.create(base, if (wgt >= 600) Typeface.BOLD else Typeface.NORMAL)
                }
                "ta" -> align = when (op.optString(1)) {
                    "center" -> Layout.Alignment.ALIGN_CENTER
                    "right" -> Layout.Alignment.ALIGN_OPPOSITE
                    else -> Layout.Alignment.ALIGN_NORMAL
                }
                "fillRect" -> c.drawRect(rect(), fill)
                "fillEllipse" -> c.drawOval(rect(), fill)
                "strokeRect" -> c.drawRect(rect(), stroke)
                "strokeEllipse" -> c.drawOval(rect(), stroke)
                "path" -> op.optJSONArray(1)?.let { appendPath(path, it) }
                "fillPath" -> { c.drawPath(path, fill); path = Path() }
                "strokePath" -> { c.drawPath(path, stroke); path = Path() }
                "text" -> {
                    val fm = text.fontMetrics
                    c.drawText(op.optString(1), f(2), f(3) - fm.ascent, text)
                }
                "textRect" -> {
                    val r = RectF(f(2), f(3), f(2) + f(4), f(3) + f(5))
                    val layout = StaticLayout.Builder.obtain(op.optString(1), 0, op.optString(1).length, text, max(1, r.width().roundToInt()))
                        .setAlignment(align).build()
                    c.save()
                    c.clipRect(r)
                    c.translate(r.left, r.top)
                    layout.draw(c)
                    c.restore()
                }
                "img" -> op.optJSONObject(1)?.let { io ->
                    owner.decodeImage(io, 1024, 1024)?.let { b ->
                        val ww = f(4).takeIf { it > 0 } ?: b.width.toFloat()
                        val hh = f(5).takeIf { it > 0 } ?: b.height.toFloat()
                        c.drawBitmap(b, null, RectF(f(2), f(3), f(2) + ww, f(3) + hh), Paint(Paint.FILTER_BITMAP_FLAG))
                    }
                }
            }
        }
        return bmp
    }

    private fun appendPath(p: Path, ops: JSONArray) {
        for (i in 0 until ops.length()) {
            val o = ops.optJSONArray(i) ?: continue
            fun f(k: Int) = o.optDouble(k, 0.0).toFloat()
            when (o.optString(0)) {
                "M" -> p.moveTo(f(1), f(2))
                "L" -> p.lineTo(f(1), f(2))
                "R" -> p.addRect(f(1), f(2), f(1) + f(3), f(2) + f(4), Path.Direction.CW)
                "E" -> p.addOval(f(1), f(2), f(1) + f(3), f(2) + f(4), Path.Direction.CW)
                "RR" -> p.addRoundRect(f(1), f(2), f(1) + f(3), f(2) + f(4), f(5), f(6), Path.Direction.CW)
                "C" -> p.cubicTo(f(1), f(2), f(3), f(4), f(5), f(6))
                "Q" -> p.quadTo(f(1), f(2), f(3), f(4))
                "Z" -> p.close()
            }
        }
    }
}

/** SF Symbols の代わりに表示する文字 (glyph, 絵文字かどうか) */
object SymbolMap {
    private val exact = mapOf(
        "sun.max.fill" to "☀️", "sun.max" to "☀️", "sun.min.fill" to "☀️", "sunrise.fill" to "🌅", "sunset.fill" to "🌇",
        "cloud.sun.fill" to "⛅", "cloud.sun" to "⛅", "cloud.fill" to "☁️", "cloud" to "☁️",
        "cloud.rain.fill" to "🌧️", "cloud.drizzle.fill" to "🌦️", "cloud.heavyrain.fill" to "🌧️",
        "cloud.sun.rain.fill" to "🌦️", "cloud.bolt.fill" to "🌩️", "cloud.bolt.rain.fill" to "⛈️",
        "cloud.snow.fill" to "🌨️", "snowflake" to "❄️", "cloud.fog.fill" to "🌫️", "smoke.fill" to "🌫️",
        "moon.fill" to "🌙", "moon.stars.fill" to "🌙", "cloud.moon.fill" to "☁️", "cloud.moon.rain.fill" to "🌧️",
        "wind" to "💨", "tornado" to "🌪️", "thermometer" to "🌡️", "thermometer.sun.fill" to "🌡️",
        "drop.fill" to "💧", "drop" to "💧", "humidity.fill" to "💧", "umbrella.fill" to "☂️", "bolt.fill" to "⚡",
        "calendar" to "📅", "clock" to "🕒", "clock.fill" to "🕒", "alarm" to "⏰",
        "creditcard" to "💳", "creditcard.fill" to "💳", "yensign.circle" to "💴", "yensign.circle.fill" to "💴",
        "dollarsign.circle" to "💲", "dollarsign.circle.fill" to "💲", "banknote" to "💵", "cart.fill" to "🛒",
        "note.text" to "📝", "note" to "📝", "doc.text" to "📄", "doc.text.fill" to "📄", "list.bullet" to "☰",
        "checkmark" to "✓", "checkmark.circle" to "✅", "checkmark.circle.fill" to "✅", "xmark" to "✕",
        "xmark.circle.fill" to "❌", "exclamationmark.triangle.fill" to "⚠️", "info.circle" to "ℹ️",
        "heart.fill" to "❤️", "star.fill" to "⭐", "star" to "☆", "flame.fill" to "🔥", "leaf.fill" to "🍃",
        "house.fill" to "🏠", "car.fill" to "🚗", "tram.fill" to "🚃", "airplane" to "✈️", "bicycle" to "🚲",
        "figure.walk" to "🚶", "bell.fill" to "🔔", "envelope.fill" to "✉️", "phone.fill" to "📞",
        "battery.100" to "🔋", "battery.25" to "🪫", "bolt.batteryblock.fill" to "🔋", "wifi" to "📶",
        "location.fill" to "📍", "mappin" to "📍", "map.fill" to "🗺️", "gift.fill" to "🎁", "cup.and.saucer.fill" to "☕",
        "arrow.up" to "↑", "arrow.down" to "↓", "arrow.right" to "→", "arrow.left" to "←",
        "arrow.up.right" to "↗", "arrow.down.right" to "↘", "chevron.right" to "›", "chevron.left" to "‹",
        "plus" to "+", "minus" to "−", "circle.fill" to "●", "circle" to "○", "square.fill" to "■", "square" to "□",
        "person.fill" to "👤", "gearshape.fill" to "⚙️", "magnifyingglass" to "🔍", "chart.bar.fill" to "📊",
        "chart.line.uptrend.xyaxis" to "📈", "sparkles" to "✨", "brain" to "🧠", "cpu" to "🖥️",
    )
    private val plain = setOf("✓", "✕", "↑", "↓", "→", "←", "↗", "↘", "›", "‹", "+", "−", "●", "○", "■", "□", "☆", "☰")

    fun glyph(name: String): Pair<String, Boolean> {
        val g = exact[name] ?: when {
            "bolt" in name && "rain" in name -> "⛈️"
            "snow" in name -> "❄️"
            "rain" in name || "drizzle" in name -> "🌧️"
            "fog" in name || "haze" in name -> "🌫️"
            "bolt" in name -> "⚡"
            "sun" in name && "cloud" in name -> "⛅"
            "sun" in name -> "☀️"
            "moon" in name -> "🌙"
            "cloud" in name -> "☁️"
            "wind" in name -> "💨"
            "drop" in name -> "💧"
            "battery" in name -> "🔋"
            "calendar" in name -> "📅"
            "clock" in name || "timer" in name -> "🕒"
            "heart" in name -> "❤️"
            "star" in name -> "⭐"
            "checkmark" in name -> "✓"
            "xmark" in name -> "✕"
            "arrow" in name && "up" in name -> "↑"
            "arrow" in name && "down" in name -> "↓"
            "person" in name -> "👤"
            "yen" in name || "dollar" in name || "creditcard" in name -> "💳"
            "doc" in name || "note" in name -> "📝"
            else -> "●"
        }
        return g to (g !in plain)
    }
}
