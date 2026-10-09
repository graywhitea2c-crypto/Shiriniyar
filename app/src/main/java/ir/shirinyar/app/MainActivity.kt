package ir.shirinyar.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import ir.shirinyar.app.data.*
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var dao: ShopDao
    private var tts: TextToSpeech? = null
    private lateinit var transcript: TextView
    private lateinit var totalView: TextView
    private lateinit var itemsView: TextView
    private lateinit var commandInput: EditText
    private lateinit var productBox: LinearLayout
    private val fa = Locale("fa", "IR")
    private val numberFormat = NumberFormat.getIntegerInstance(fa)

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listen() else speak("برای فرمان صوتی، اجازهٔ میکروفون لازم است.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dao = AppDatabase.get(this).dao()
        tts = TextToSpeech(this, this)
        buildUi()
        lifecycleScope.launch {
            seedProducts()
            refresh()
        }
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(24))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(0xFFFFF8F0.toInt())
        }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = "شیرین‌یار 🍦"
            textSize = 28f; gravity = Gravity.CENTER; setTextColor(0xFF6D3D2C.toInt())
        }, match())
        root.addView(TextView(this).apply {
            text = "ماشین‌حساب صوتی بستنی و شیرینی"
            textSize = 15f; gravity = Gravity.CENTER; setPadding(0, dp(5), 0, dp(14))
        }, match())

        val voice = button("🎙 صحبت کن") { requestMicAndListen() }
        root.addView(voice, match())
        transcript = TextView(this).apply {
            text = "نمونه: «دو تا بستنی وانیلی» یا «نیم کیلو شیرینی»"
            textSize = 16f; setPadding(dp(10), dp(12), dp(10), dp(12))
        }
        root.addView(transcript, match())

        commandInput = EditText(this).apply {
            hint = "فرمان را بنویسید…"
            textSize = 16f
            gravity = Gravity.RIGHT
            setSingleLine(false)
        }
        root.addView(commandInput, match())
        root.addView(button("اجرای فرمان متنی") { processCommand(commandInput.text.toString()) }, match())

        totalView = TextView(this).apply {
            textSize = 25f; gravity = Gravity.CENTER
            setTextColor(0xFF176B45.toInt()); setPadding(0, dp(18), 0, dp(12))
        }
        root.addView(totalView, match())
        itemsView = TextView(this).apply {
            textSize = 16f; setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        root.addView(itemsView, match())
        root.addView(button("↩ آخرین مورد را پاک کن") {
            lifecycleScope.launch { dao.deleteLastItem(); refresh(); speak("آخرین مورد پاک شد.") }
        }, match())
        root.addView(button("＋ فاکتور جدید") {
            lifecycleScope.launch { dao.clearReceipt(); refresh(); speak("فاکتور جدید آماده است.") }
        }, match())

        root.addView(TextView(this).apply {
            text = "قیمت محصولات (تومان)"; textSize = 20f
            setPadding(0, dp(22), 0, dp(8)); setTextColor(0xFF6D3D2C.toInt())
        }, match())
        productBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(productBox, match())
        root.addView(button("＋ افزودن / تغییر قیمت محصول") { showProductEditor() }, match())
        root.addView(TextView(this).apply {
            text = "نکته: قیمت‌ها روی همین گوشی ذخیره می‌شوند. تشخیص گفتار فارسی به سرویس گفتار نصب‌شده روی دستگاه وابسته است."
            textSize = 12f; setPadding(0, dp(14), 0, 0)
        }, match())
    }

    private fun requestMicAndListen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen()
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            speak("تشخیص گفتار روی این دستگاه در دسترس نیست. لطفاً فرمان را تایپ کنید.")
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "فرمان خود را بگویید")
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, 901)
        } catch (_: Exception) {
            speak("شروع تشخیص گفتار ممکن نشد.")
        }
    }

    @Deprecated("Uses Android's speech UI for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 901 && resultCode == Activity.RESULT_OK) {
            val words = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            transcript.text = "شنیده‌شده: $words"
            processCommand(words)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("fa", "IR"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale("fa"))
            }
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "shirinyar-${System.currentTimeMillis()}")
    }

    private fun processCommand(raw: String) {
        val text = normalize(raw).trim()
        if (text.isBlank()) { ask("لطفاً فرمان را بگویید یا بنویسید."); return }
        lifecycleScope.launch {
            when {
                text.contains("جمعش") || text.contains("جمع کل") || text.contains("چقدر شد") || text == "مجموع" -> {
                    val total = dao.items().sumOf { it.amount }
                    speak("جمع کل ${money(total)} تومان است.")
                    transcript.text = "جمع کل: ${money(total)} تومان"
                }
                text.contains("فاکتور جدید") || text.contains("فاکتور تازه") -> {
                    dao.clearReceipt(); refresh(); speak("فاکتور جدید آماده است.")
                }
                text.contains("آخرین مورد") && (text.contains("پاک") || text.contains("حذف")) -> {
                    dao.deleteLastItem(); refresh(); speak("آخرین مورد پاک شد.")
                }
                text.contains("تخفیف") -> {
                    val amount = extractAmount(text)
                    if (amount == null || amount <= 0) ask("مبلغ تخفیف مشخص نیست. چند تومان تخفیف بدهم؟")
                    else {
                        val total = dao.items().sumOf { it.amount }
                        if (amount > total) ask("تخفیف از جمع فعلی بیشتر است. مبلغ دیگری بگویید.")
                        else { dao.addItem(ReceiptItem(label = "تخفیف", quantity = 1.0, unit = "مبلغ", unitPrice = amount, amount = -amount, kind = "discount")); refresh(); speak("$amount تومان تخفیف ثبت شد.") }
                    }
                }
                text.contains("بذار روش") || text.contains("اضافه کن") || text.contains("مبلغ دستی") -> {
                    val amount = extractAmount(text)
                    if (amount == null || amount <= 0) ask("مبلغ اضافه‌شونده مشخص نیست. چند تومان اضافه کنم؟")
                    else { dao.addItem(ReceiptItem(label = "مبلغ دستی", quantity = 1.0, unit = "مبلغ", unitPrice = amount, amount = amount, kind = "manual")); refresh(); speak("$amount تومان اضافه شد.") }
                }
                else -> addProductCommand(text)
            }
        }
    }

    private suspend fun addProductCommand(text: String) {
        val products = dao.products()
        val product = products.firstOrNull { text.contains(normalize(it.name)) }
        if (product == null) {
            ask("محصول را دقیق متوجه نشدم. نام محصول و قیمت آن را از بخش قیمت محصولات ثبت کنید.")
            return
        }
        val quantity = parseQuantity(text, product.unit)
        if (quantity == null || quantity <= 0.0) {
            ask(if (product.unit == "کیلو") "چه مقدار از ${product.name}؟ مثلاً نیم کیلو یا ربع کیلو." else "چند عدد ${product.name}؟")
            return
        }
        val amount = (quantity * product.price).roundToLong()
        dao.addItem(ReceiptItem(label = product.name, quantity = quantity, unit = product.unit, unitPrice = product.price, amount = amount))
        refresh()
        speak("${quantityLabel(quantity, product.unit)} ${product.name} ثبت شد. مبلغ ${money(amount)} تومان.")
    }

    private fun parseQuantity(text: String, unit: String): Double? {
        if (unit == "کیلو") {
            if (text.contains("ربع")) return 0.25
            if (text.contains("نیم")) return 0.5
            val m = Regex("""([۰-۹0-9]+(?:[٫.,/][۰-۹0-9]+)?)\s*کیلو""").find(text)
            if (m != null) return parseNumber(m.groupValues[1])
            val grams = Regex("""([۰-۹0-9]+)\s*گرم""").find(text)
            if (grams != null) return (parseNumber(grams.groupValues[1]) ?: return null) / 1000.0
            return null
        }
        val words = listOf("یک" to 1.0, "یه" to 1.0, "دو" to 2.0, "سه" to 3.0, "چهار" to 4.0, "پنج" to 5.0, "شش" to 6.0, "هفت" to 7.0, "هشت" to 8.0, "نه" to 9.0, "ده" to 10.0)
        words.firstOrNull { text.contains(it.first) }?.let { return it.second }
        val m = Regex("""([۰-۹0-9]+)\s*(?:تا|عدد)?""").find(text) ?: return null
        return parseNumber(m.groupValues[1])
    }

    private fun extractAmount(text: String): Long? {
        val n = Regex("""([۰-۹0-9]+(?:[٫.,][۰-۹0-9]+)?)\s*(میلیون|هزار|تومن|تومان)?""").find(text)
        if (n != null) {
            val base = parseNumber(n.groupValues[1]) ?: return null
            val multiplier = when (n.groupValues.getOrElse(2) { "" }) {
                "میلیون" -> 1_000_000.0
                "هزار" -> 1_000.0
                else -> 1.0
            }
            return (base * multiplier).roundToLong()
        }
        // Common spoken Persian numbers; ambiguous bare "تومن" is not silently
        // converted to thousands, so the numeric amount remains literal.
        val words = listOf(
            "یک" to 1L, "یه" to 1L, "دو" to 2L, "سه" to 3L, "چهار" to 4L,
            "پنج" to 5L, "شش" to 6L, "شیش" to 6L, "هفت" to 7L, "هشت" to 8L,
            "نه" to 9L, "ده" to 10L, "یازده" to 11L, "دوازده" to 12L,
            "سیزده" to 13L, "چهارده" to 14L, "پانزده" to 15L, "شانزده" to 16L,
            "هفده" to 17L, "هجده" to 18L, "نوزده" to 19L, "بیست" to 20L,
            "سی" to 30L, "چهل" to 40L, "پنجاه" to 50L, "شصت" to 60L,
            "هفتاد" to 70L, "هشتاد" to 80L, "نود" to 90L,
            "صد" to 100L, "دویست" to 200L, "سیصد" to 300L, "چهارصد" to 400L,
            "پانصد" to 500L, "ششصد" to 600L, "هفتصد" to 700L, "هشتصد" to 800L,
            "نهصد" to 900L
        )
        val matched = words.filter { text.contains(it.first) }
        if (matched.isEmpty()) return null
        var value = matched.sumOf { it.second }
        if (text.contains("میلیون")) value *= 1_000_000
        else if (text.contains("هزار")) value *= 1_000
        return value
    }

    private fun parseNumber(s: String): Double? {
        val latin = s.map { c -> when (c) {
            in '۰'..'۹' -> ('0'.code + c.code - '۰'.code).toChar()
            '٫', ',' -> '.'
            else -> c
        } }.joinToString("")
        return latin.replace("/", ".").toDoubleOrNull()
    }

    private fun normalize(s: String): String = s
        .replace('ي', 'ی').replace('ك', 'ک')
        .replace('۰','0').replace('۱','1').replace('۲','2').replace('۳','3').replace('۴','4')
        .replace('۵','5').replace('۶','6').replace('۷','7').replace('۸','8').replace('۹','9')
        .replace("‌", " ").replace(Regex("\\s+"), " ")

    private fun ask(question: String) {
        transcript.text = question
        speak(question)
    }

    private suspend fun seedProducts() {
        if (dao.products().isNotEmpty()) return
        listOf(
            Product(name = "بستنی وانیلی", unit = "عدد", price = 30000),
            Product(name = "بستنی شکلاتی", unit = "عدد", price = 35000),
            Product(name = "شیرینی خشک", unit = "کیلو", price = 250000),
            Product(name = "شیرینی تر", unit = "کیلو", price = 380000)
        ).forEach { dao.saveProduct(it) }
    }

    private suspend fun refresh() {
        val items = dao.items()
        val total = items.sumOf { it.amount }
        totalView.text = "جمع کل: ${money(total)} تومان"
        itemsView.text = if (items.isEmpty()) "هنوز موردی در فاکتور نیست." else items.joinToString("\n\n") {
            val sign = if (it.amount < 0) "−" else ""
            "• ${it.label} — ${quantityLabel(it.quantity, it.unit)}\n  $sign${money(kotlin.math.abs(it.amount))} تومان"
        }
        productBox.removeAllViews()
        dao.products().forEach { p ->
            productBox.addView(TextView(this).apply {
                text = "${p.name} · هر ${p.unit}: ${money(p.price)} تومان"
                textSize = 15f; setPadding(dp(8), dp(8), dp(8), dp(8))
            })
        }
    }

    private fun showProductEditor() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(12), 0) }
        val name = EditText(this).apply { hint = "نام دقیق محصول"; gravity = Gravity.RIGHT }
        val price = EditText(this).apply { hint = "قیمت به تومان"; inputType = 2; gravity = Gravity.RIGHT }
        val unit = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("عدد", "کیلو"))
        }
        layout.addView(name); layout.addView(unit); layout.addView(price)
        android.app.AlertDialog.Builder(this).setTitle("ثبت یا تغییر محصول")
            .setMessage("نام موجود را وارد کنید تا قیمتش تغییر کند.")
            .setView(layout)
            .setNegativeButton("انصراف", null)
            .setPositiveButton("ذخیره", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(-1).setOnClickListener {
                        val n = name.text.toString().trim()
                        val p = price.text.toString().toString().toLongOrNull()
                        if (n.isBlank() || p == null || p < 0) {
                            Toast.makeText(this, "نام و قیمت معتبر وارد کنید.", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        lifecycleScope.launch {
                            dao.saveProduct(Product(name = n, unit = unit.selectedItem.toString(), price = p))
                            refresh(); dialog.dismiss(); speak("قیمت $n ذخیره شد.")
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun quantityLabel(q: Double, unit: String): String {
        if (unit == "کیلو") return when (q) { 0.25 -> "ربع کیلو"; 0.5 -> "نیم کیلو"; else -> "${q} کیلو" }
        return "${q.toInt()} عدد"
    }

    private fun money(n: Long): String = numberFormat.format(n)
    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 16f; isAllCaps = false; setOnClickListener { action() }
    }
    private fun match() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        tts?.stop(); tts?.shutdown(); super.onDestroy()
    }
}
