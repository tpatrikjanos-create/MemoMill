package hu.memomill.app

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Egyszerű, kód alapú felület a natív képernyőkhöz (a webes app színeivel). */
object Ui {
    const val BG = 0xFF0A1124.toInt()
    const val PANEL = 0xFF121D3B.toInt()
    const val TEXT = 0xFFE9EEFB.toInt()
    const val MUTED = 0xFF8D9BBD.toInt()
    const val ACCENT = 0xFF4F8CFF.toInt()
    const val OK = 0xFF6FD3A0.toInt()
    const val BAD = 0xFFFF7F7F.toInt()

    fun dp(a: Activity, v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), a.resources.displayMetrics).toInt()

    fun lp(a: Activity, top: Int = 12): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(a, top) }

    fun text(a: Activity, s: String, size: Float = 16f, color: Int = TEXT, bold: Boolean = false, center: Boolean = true): TextView =
        TextView(a).apply {
            text = s
            setTextColor(color)
            textSize = size
            if (bold) typeface = Typeface.DEFAULT_BOLD
            gravity = if (center) Gravity.CENTER_HORIZONTAL else Gravity.START
            layoutParams = lp(a)
            setLineSpacing(0f, 1.2f)
        }

    fun title(a: Activity, s: String, size: Float = 24f): TextView =
        text(a, s, size, TEXT, true).apply { typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) }

    fun button(a: Activity, s: String, primary: Boolean = false, onClick: () -> Unit): Button =
        Button(a).apply {
            text = s
            isAllCaps = false
            textSize = 16f
            setTextColor(if (primary) Color.WHITE else TEXT)
            background = GradientDrawable().apply {
                cornerRadius = dp(a, 28).toFloat()
                if (primary) setColor(ACCENT) else {
                    setColor(PANEL)
                    setStroke(dp(a, 1), 0xFF22305A.toInt())
                }
            }
            setPadding(dp(a, 20), dp(a, 14), dp(a, 20), dp(a, 14))
            layoutParams = lp(a)
            setOnClickListener { onClick() }
        }

    fun input(a: Activity, hint: String, number: Boolean = false): EditText =
        EditText(a).apply {
            this.hint = hint
            setHintTextColor(MUTED)
            setTextColor(TEXT)
            textSize = 18f
            inputType = if (number) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            background = GradientDrawable().apply {
                cornerRadius = dp(a, 14).toFloat()
                setColor(0xFF0B1430.toInt())
                setStroke(dp(a, 1), 0xFF22305A.toInt())
            }
            setPadding(dp(a, 14), dp(a, 12), dp(a, 14), dp(a, 12))
            layoutParams = lp(a)
        }

    fun column(a: Activity): LinearLayout =
        LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(a, 22), dp(a, 40), dp(a, 22), dp(a, 28))
        }
}
