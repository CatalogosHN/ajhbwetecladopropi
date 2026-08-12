package com.brayan.tecladoanclado

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

class MiTecladoAnclado : InputMethodService() {
    private lateinit var adapter: PinnedAdapter
    private lateinit var clipboardManager: ClipboardManager
    
    private val backgroundExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private lateinit var audioManager: AudioManager
    private lateinit var vibrator: Vibrator // <-- ¡AQUÍ ESTÁ EL ARREGLO!
    private var soundEnabled = true
    private var soundEnterEnabled = true
    private var vibrationEnabled = false
    private var autocorrectEnabled = true
    
    private var learnedWords = mutableSetOf<String>()
    private var currentBestSuggestion = ""
    private var shiftState = 0
    private var lastShiftTime = 0L
    
    private var allQrItems = mutableListOf<QuickReplyItem>()
    private lateinit var qrAdapter: QuickReplyKeyboardAdapter
    private var qrTriggerChar = "["
    
    private lateinit var layoutTopBar: View
    private lateinit var layoutSuggestionsBar: View
    private lateinit var layoutTranslatorBar: View
    private lateinit var rvQuickRepliesKeyboard: RecyclerView
    
    private lateinit var btnSuggest1: Button
    private lateinit var btnSuggest2: Button
    private lateinit var btnSuggest3: Button
    
    private lateinit var layoutLetters: View
    private lateinit var layoutSymbols1: View
    private lateinit var layoutSymbols2: View
    private lateinit var layoutNumpad: View
    private lateinit var layoutClipboard: View
    private lateinit var layoutEmojis: View 

    private var btnMic1: Button? = null
    private var btnQrTrigger: Button? = null
    private lateinit var btnLangToggle: Button
    private var isEsToEn = true

    // --- LISTAS DE EMOJIS DE WHATSAPP ---
    private val emojisSmileys = "😀,😃,😄,😁,😆,😅,😂,🤣,🥲,☺️,😊,😇,🙂,🙃,😉,😌,😍,🥰,😘,😗,😙,😚,😋,😛,😝,😜,🤪,🤨,🧐,🤓,😎,🥸,🤩,🥳,😏,😒,😞,😔,😟,😕,🙁,☹️,😣,😖,😫,😩,🥺,😢,😭,😤,😠,😡,🤬,🤯,😳,🥵,🥶,😱,😨,😰,😥,😓,🫣,🤭,🫢,🫡,🤔,🤫,🤥,😶,😐,😑,😬,🙄,😯,😦,😧,😮,😲,🥱,😴,🤤,😪,😮‍💨,😵,😵‍💫,🤐,🥴,🤢,🤮,🤧,😷,🤒,🤕,🤑,🤠,😈,👿,👹,👺,🤡,💩,👻,💀,👽,👾,🤖,🎃,🫶,🤲,👐,🙌,👏,🤝,👍,👎,👊,✊,🤛,🤜,🤞,✌️,🫰,🤟,🤘,👌,🤌,🤏,🫳,🫴,👈,👉,👆,👇,☝️,✋,🤚,🖐,🖖,👋,🤙,💪,🦾,🖕,✍️,🙏,🦶,🦵,🦿,💄,💋,👄,🦷,👅,👂,🦻,👃,👣,👁,👀,🫀,🫁,🧠,🗣,👤,👥,🫂,👶,👧,🧒,👦,👩,🧑,👨,👩‍🦱,🧑‍🦱,👨‍🦱,👩‍🦰,🧑‍🦰,👨‍🦰,👱‍♀️,👱,👱‍♂️,👩‍🦳,🧑‍🦳,👨‍🦳,👩‍🦲,🧑‍🦲,👨‍🦲,🧔‍♀️,🧔,🧔‍♂️,👵,🧓,👴,👲,👳‍♀️,👳,👳‍♂️,🧕,👮‍♀️,👮,👮‍♂️,👷‍♀️,👷,👷‍♂️,💂‍♀️,💂,💂‍♂️,🕵️‍♀️,🕵️,🕵️‍♂️,👩‍⚕️,🧑‍⚕️,👨‍⚕️,👩‍🌾,🧑‍🌾,👨‍🌾,👩‍🍳,🧑‍🍳,👨‍🍳,👩‍🎓,🧑‍🎓,👨‍🎓,👩‍🎤,🧑‍🎤,👨‍🎤,👩‍🏫,🧑‍🏫,👨‍🏫,👩‍🏭,🧑‍🏭,👨‍🏭,👩‍💻,🧑‍💻,👨‍💻,👩‍💼,🧑‍💼,👨‍💼,👩‍🔧,🧑‍🔧,👨‍🔧,👩‍🔬,🧑‍🔬,👨‍🔬,👩‍🎨,🧑‍🎨,👨‍🎨,👩‍🚒,🧑‍🚒,👨‍🚒,👩‍✈️,🧑‍✈️,👨‍✈️,👩‍🚀,🧑‍🚀,👨‍🚀,👩‍⚖️,🧑‍⚖️,👨‍⚖️,👰‍♀️,👰,👰‍♂️,🤵‍♀️,🤵,🤵‍♂️,👸,🤴,🥷,🦸‍♀️,🦸,🦸‍♂️,🦹‍♀️,🦹,🦹‍♂️,🤶,🧑‍🎄,🎅,🧙‍♀️,🧙,🧙‍♂️,🧝‍♀️,🧝,🧝‍♂️,🧛‍♀️,🧛,🧛‍♂️,🧟‍♀️,🧟,🧟‍♂️,🧞‍♀️,🧞,🧞‍♂️,🧜‍♀️,🧜,🧜‍♂️,🧚‍♀️,🧚,🧚‍♂️,👼,🤰,🫄,🫃,🤱,👩‍🍼,🧑‍🍼,👨‍🍼,🙇‍♀️,🙇,🙇‍♂️,💁‍♀️,💁,💁‍♂️,🙅‍♀️,🙅,🙅‍♂️,🙆‍♀️,🙆,🙆‍♂️,🙋‍♀️,🙋,🙋‍♂️,🧏‍♀️,🧏,🧏‍♂️,🤦‍♀️,🤦,🤦‍♂️,🤷‍♀️,🤷,🤷‍♂️,🙎‍♀️,🙎,🙎‍♂️,🙍‍♀️,🙍,🙍‍♂️,💇‍♀️,💇,💇‍♂️,💆‍♀️,💆,💆‍♂️,🧖‍♀️,🧖,🧖‍♂️,💅,🤳,💃,🕺,👯‍♀️,👯,👯‍♂️,🕴,👩‍🦽,🧑‍🦽,👨‍🦽,👩‍🦼,🧑‍🦼,👨‍🦼,🚶‍♀️,🚶,🚶‍♂️,👩‍🦯,🧑‍🦯,👨‍🦯,🧎‍♀️,🧎,🧎‍♂️,🏃‍♀️,🏃,🏃‍♂️,🧍‍♀️,🧍,🧍‍♂️,👫,👭,👬,👩‍❤️‍👨,👩‍❤️‍👩,💑,👨‍❤️‍👨,👩‍❤️‍💋‍👨,👩‍❤️‍💋‍👩,💏,👨‍❤️‍💋‍👨,👨‍👩‍👦,👨‍👩‍👧,👨‍👩‍👧‍👦,👨‍👩‍👦‍👦,👨‍👩‍👧‍👧,👩‍👩‍👦,👩‍👩‍👧,👩‍👩‍👧‍👦,👩‍👩‍👦‍👦,👩‍👩‍👧‍👧,👨‍👨‍👦,👨‍👨‍👧,👨‍👨‍👧‍👦,👨‍👨‍👦‍👦,👨‍👨‍👧‍👧,👩‍👦,👩‍👧,👩‍👧‍👦,👩‍👦‍👦,👩‍👧‍👧,👨‍👦,👨‍👧,👨‍👧‍👦,👨‍👦‍👦,👨‍👧‍👧"
    private val emojisAnimals = "🐶,🐱,🐭,🐹,🐰,🦊,🐻,🐼,🐻‍❄️,🐨,🐯,🦁,🐮,🐷,🐽,🐸,🐵,🙈,🙉,🙊,🐒,🐔,🐧,🐦,🐤,🐣,🐥,🦆,🦅,🦉,🦇,🐺,🐗,🐴,🦄,🐝,🪱,🐛,🦋,🐌,🐞,🐜,🪰,🪲,🪳,🦟,🦗,🕷,🕸,🦂,🐢,🐍,🦎,🦖,🦕,🐙,🦑,🦐,🦞,🦀,🐡,🐠,🐟,🐬,🐳,🐋,🦈,🦭,🐊,🐅,🐆,🦓,🦍,🦧,🦣,🐘,🦛,🦏,🐪,🐫,🦒,🦘,🦬,🐃,🐂,🐄,🐎,🐖,🐏,🐑,🦙,🐐,🦌,🐕,🐩,🦮,🐕‍🦺,🐈,🐈‍⬛,🪶,🐓,🦃,🦤,🦚,🦜,🦢,🦩,🕊,🐇,🦝,🦨,🦡,🦫,🦦,🦥,🐁,🐀,🐿,🦔,🐾,🐉,🐲,🌵,🎄,🌲,🌳,🌴,🪵,🌱,🌿,☘️,🍀,🎍,🪴,🎋,🍃,🍂,🍁,🍄,🐚,🪨,🌾,💐,🌷,🌹,🥀,🌺,🌸,🌼,🌻,🌞,🌝,🌛,🌜,🌚,🌕,🌖,🌗,🌘,🌑,🌒,🌓,🌔,🌙,🌎,🌍,🌏,🪐,💫,⭐️,🌟,✨,⚡️,☄️,💥,🔥,🌪,🌈,☀️,🌤,⛅️,🌥,☁️,🌦,🌧,⛈,🌩,🌨,❄️,☃️,⛄️,🌬,💨,💧,💦,☔️,☂️,🌊,🌫"
    private val emojisFood = "🍏,🍎,🍐,🍊,🍋,🍌,🍉,🍇,🍓,🍈,🍒,🍑,🥭,🍍,🥥,🥝,🍅,🍆,🥑,🥦,🥬,🥒,🌶,🫑,🌽,🥕,🫒,🧄,🧅,🥔,🍠,🥐,🥯,🍞,🥖,🥨,🧀,🥚,🍳,🧈,🥞,🧇,🥓,🥩,🍗,🍖,🦴,🌭,🍔,🍟,🍕,🫓,🥪,🥙,🧆,🫔,🌮,🌯,🫢,🥗,🥘,🫕,🥫,🍝,🍜,🍲,🍛,🍣,🍱,🥟,🦪,🍤,🍙,🍚,🍘,🍥,🥠,🥮,🍢,🍡,🍧,🍨,🍦,🥧,🧁,🍰,🎂,🍮,🍭,🍬,🍫,🍿,🍩,🍪,🌰,🥜,🍯,🥛,🍼,🫖,☕️,🍵,🧃,🥤,🧋,🍶,🍺,🍻,🥂,🍷,🥃,🍸,🍹,🧉,🍾,🧊,🥄,🍴,🍽,🥣,🥡,🥢,🧂"
    private val emojisSports = "⚽️,🏀,🏈,⚾️,🥎,🎾,🏐,🏉,🥏,🎱,🪀,🏓,🏸,🏒,🏑,🥍,🏏,🪃,🥅,⛳️,🪁,🏹,🎣,🤿,🥊,🥋,🎽,🛹,🛼,🛷,⛸,🥌,🎿,⛷,🏂,🪂,🏋️‍♀️,🏋️,🏋️‍♂️,🤼‍♀️,🤼,🤼‍♂️,🤸‍♀️,🤸,🤸‍♂️,⛹️‍♀️,⛹️,⛹️‍♂️,🤺,🤾‍♀️,🤾,🤾‍♂️,🏌️‍♀️,🏌️,🏌️‍♂️,🏇,🧘‍♀️,🧘,🧘‍♂️,🏄‍♀️,🏄,🏄‍♂️,🏊‍♀️,🏊,🏊‍♂️,🤽‍♀️,🤽,🤽‍♂️,🚣‍♀️,🚣,🚣‍♂️,🧗‍♀️,🧗,🧗‍♂️,🚵‍♀️,🚵,🚵‍♂️,🚴‍♀️,🚴,🚴‍♂️,🏆,🥇,🥈,🥉,🏅,🎖,🏵,🎗,🎫,🎟,🎪,🤹‍♀️,🤹,🤹‍♂️,🎭,🩰,🎨,🎬,🎤,🎧,🎼,🎹,🥁,🪘,🎷,🎺,🪗,🎸,🪕,🎻,🎲,♟,🎯,🎳,🎮,🎰,🧩"
    private val emojisTravel = "🚗,🚕,🚙,🚌,🚎,🏎,🚓,🚑,🚒,🚐,🛻,🚚,🚛,🚜,🦯,🦽,🦼,🛴,🚲,🛵,🏍,🛺,🚨,🚔,🚍,🚘,🚖,🚡,🚠,🚟,🚃,🚋,🚞,🚝,🚄,🚅,🚈,🚂,🚆,🚇,🚊,🚉,✈️,🛫,🛬,🛩,💺,🛰,🚀,🛸,🚁,🛶,⛵️,🚤,🛥,🛳,⛴,🚢,⚓️,🪝,⛽️,🚧,🚦,🚥,🚏,🗺,🗿,🗽,🗼,🏰,🏯,🏟,🎡,🎢,🎠,⛲️,⛱,🏖,🏝,🏜,🌋,⛰,🏔,🗻,🏕,⛺️,🛖,🏠,🏡,🏘,🏚,🏗,🏭,🏢,🏬,🏣,🏤,🏥,🏦,🏨,🏪,🏫,🏩,💒,🏛,⛪️,🕌,🕍,🛕,🕋,⛩,🛤,🛣,🗾,🎑,🏞,🌅,🌄,🌠,🎇,🎆,🌇,🌆,🏙,🌃,🌌,🌉,🌁"
    private val emojisObjects = "⌚️,📱,📲,💻,⌨️,🖥,🖨,🖱,🖲,🕹,🗜,💽,💾,💿,📀,📼,📷,📸,📹,🎥,📽,🎞,📞,☎️,📟,📠,📺,📻,🎙,🎚,🎛,🧭,⏱,⏲,⏰,🕰,⌛️,⏳,📡,🔋,🔌,💡,🔦,🕯,🪔,🧯,🛢,💸,💵,💴,💶,💷,🪙,💰,💳,💎,⚖️,🪜,🧰,🪛,🔧,🔨,⚒,🛠,⛏,🪚,🔩,⚙️,🪤,🧱,⛓,🧲,🔫,💣,🧨,🪓,🔪,🗡,⚔️,🛡,🚬,⚰️,🪦,⚱️,🏺,🔮,📿,🧿,💈,⚗️,🔭,🔬,🕳,🩹,🩺,💊,💉,🩸,🧬,🦠,🧫,🧪,🌡,🧹,🪠,🧺,🧻,🪣,🧼,🪥,🧽,🧯,🛒"
    private val emojisSymbols = "❤️,🧡,💛,💚,💙,💜,🖤,🤍,🤎,💔,❣️,💕,💞,💓,💗,💖,💘,💝,❤️‍🔥,❤️‍🩹,☮️,✝️,☪️,🕉,☸️,✡️,🔯,🕎,☯️,☦️,🛐,⛎,♈️,♉️,♊️,♋️,♌️,♍️,♎️,♏️,♐️,♑️,♒️,♓️,🆔,⚛️,🉑,☢️,☣️,📴,📳,🈶,🈚️,🈸,🈺,🈷️,✴️,🆚,💮,🉐,㊙️,㊗️,🈴,🈵,🈹,🈲,🅰️,🅱️,🆎,🆑,🅾️,🆘,❌,⭕️,🛑,⛔️,📛,🚫,💯,💢,♨️,🚷,🚯,🚳,🚱,🔞,📵,🚭,❗️,❕,❓,❔,‼️,⁉️,🔅,🔆,〽️,⚠️,🚸,🔱,⚜️,🔰,♻️,✅,🈯️,💹,❇️,✳️,❎,🌐,💠,Ⓜ️,🌀,💤,🏧,🚾,♿️,🅿️,🛗,🈳,🈂️,🛂,🛃,🛄,🛅,🚹,🚺,🚼,⚧,🚻,🚮,🎦,📶,🈁,🔣,ℹ️,🔤,🔡,🔠,🆖,🆗,🆙,🆒,🆕,🆓,0️⃣,1️⃣,2️⃣,3️⃣,4️⃣,5️⃣,6️⃣,7️⃣,8️⃣,9️⃣,🔟,🔢,▶️,⏸,⏯,⏹,⏺,⏭,⏮,⏩,⏪,🔀,🔁,🔂,◀️,🔼,🔽,⏫,⏬,➡️,⬅️,⬆️,⬇️,↗️,↘️,↙️,↖️,↕️,↔️,↪️,↩️,⤴️,⤵️,🔀,🔁,🔂,🔄,🔃,🎵,🎶,➕,➖,➗,✖️,♾,💲,💱,™️,©️,®️,〰️,➰,➿,🔚,🔙,🔛,🔝,🔜,✔️,☑️,🔘,🔴,🟠,🟡,🟢,🔵,🟣,⚫️,⚪️,🟤,🔺,🔻,🔸,🔹,🔶,🔷,🔳,🔲,▪️,▫️,◾️,◽️,◼️,◻️,⬛️,⬜️,🟥,🟧,🟨,🟩,🟦,🟪,⬛️,⬜️,🟫,🔈,🔇,🔉,🔊,🔔,🔕,📣,📢,👁‍🗨,💬,💭,🗯,♠️,♣️,♥️,♦️,🃏,🎴,🀄️,🕐,🕑,🕒,🕓,🕔,🕕,🕖,🕗,🕘,🕙,🕚,🕛,🕜,🕝,🕞,🕟,🕠,🕡,🕢,🕣,🕤,🕥,🕦,🕧"

    // LA LISTA MAESTRA (Todos fusionados para el scroll infinito)
    private val masterEmojiList by lazy {
        emojisSmileys.split(",") + emojisAnimals.split(",") + emojisFood.split(",") + 
        emojisSports.split(",") + emojisTravel.split(",") + emojisObjects.split(",") + 
        emojisSymbols.split(",")
    }

    private lateinit var rvEmojis: RecyclerView
    private var emojiAdapter: EmojiAdapter? = null

    // RECEPTOR NINJA: Espera el texto del dictado de voz
    private val voiceReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val text = intent?.getStringExtra("text")
            if (!text.isNullOrEmpty()) {
                currentInputConnection?.commitText("$text ", 1)
            }
        }
    }

    private val deleteRunnable = object : Runnable {
        override fun run() {
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            mainHandler.postDelayed({ 
                updateSuggestionsUI() 
                checkQuickReplyTrigger()
            }, 10)
            mainHandler.postDelayed(this, 50) 
        }
    }

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { 
        mainHandler.postDelayed({ checkSystemClipboard() }, 150)
    }

    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(clipListener)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        
        learnedWords = DataManager.loadLearnedWords(this)
        
        backgroundExecutor.execute {
            if (learnedWords.size < 5000) {
                mainHandler.post { Toast.makeText(this@MiTecladoAnclado, "Descargando Diccionario (80k palabras)...", Toast.LENGTH_SHORT).show() }
                try {
                    val urlStr = "https://raw.githubusercontent.com/javierarce/palabras/master/listado-general.txt"
                    val conn = URL(urlStr).openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 10000
                    if (conn.responseCode == 200) {
                        val words = conn.inputStream.bufferedReader(Charsets.UTF_8).readLines()
                            .map { it.trim().lowercase() }
                            .filter { it.length > 2 }
                        learnedWords.addAll(words)
                        DataManager.saveLearnedWords(this@MiTecladoAnclado, learnedWords)
                        mainHandler.post { Toast.makeText(this@MiTecladoAnclado, "¡Diccionario Español Instalado!", Toast.LENGTH_LONG).show() }
                    }
                } catch (e: Exception) {
                    val baseWords = listOf("qué", "cómo", "cuándo", "dónde", "quién", "método", "envío", "garantía", "cámara", "teléfono", "también", "está", "días", "gracias", "artículo", "domicilio", "transferencia", "depósito", "número", "página", "tecnología", "promoción", "atención", "inmediata", "catálogo", "hola", "buenas", "tardes", "noches", "lempiras", "éxito", "rápido", "fácil", "útil", "increíble", "excelente", "ubicación", "dirección", "código", "guía", "recibo", "comprobante", "crédito", "débito", "artículos", "electrónica", "audífonos", "batería", "cargador", "imágenes", "vídeo", "música", "tamaño", "volumen", "computación")
                    learnedWords.addAll(baseWords)
                }
            }
        }

        val filter = android.content.IntentFilter("com.brayan.tecladoanclado.VOICE_TEXT")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(voiceReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(voiceReceiver, filter)
        }
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_layout, null)
        
        layoutTopBar = view.findViewById(R.id.layout_top_bar)
        layoutSuggestionsBar = view.findViewById(R.id.layout_suggestions_bar)
        layoutTranslatorBar = view.findViewById(R.id.layout_translator_bar)
        rvQuickRepliesKeyboard = view.findViewById(R.id.rv_quick_replies_keyboard)
        
        btnSuggest1 = view.findViewById(R.id.btnSuggest1)
        btnSuggest2 = view.findViewById(R.id.btnSuggest2)
        btnSuggest3 = view.findViewById(R.id.btnSuggest3)
        
        val suggestListener = View.OnClickListener {
            val text = (it as Button).text.toString()
            if (text.isNotBlank()) {
                playClickFeedback(it)
                insertSuggestion(text)
            }
        }
        btnSuggest1.setOnClickListener(suggestListener)
        btnSuggest2.setOnClickListener(suggestListener)
        btnSuggest3.setOnClickListener(suggestListener)
        
        layoutLetters = view.findViewById(R.id.layout_letters)
        layoutSymbols1 = view.findViewById(R.id.layout_symbols_1)
        layoutSymbols2 = view.findViewById(R.id.layout_symbols_2)
        layoutNumpad = view.findViewById(R.id.layout_numpad)
        layoutClipboard = view.findViewById(R.id.layout_clipboard)
        layoutEmojis = view.findViewById(R.id.layout_emojis) 

        btnMic1 = view.findViewById(R.id.btnMic1)
        btnQrTrigger = view.findViewById(R.id.btnQrTrigger)
        btnLangToggle = view.findViewById(R.id.btnLangToggle)

        val btnTranslateSend = view.findViewById<Button>(R.id.btnTranslateSend)
        btnLangToggle.setOnClickListener {
            playClickFeedback(btnLangToggle)
            isEsToEn = !isEsToEn
            btnLangToggle.text = if (isEsToEn) "ES ➔ EN" else "EN ➔ ES"
        }
        btnTranslateSend.setOnClickListener { translateText(btnTranslateSend) }

        val btnClipboardEnter = view.findViewById<Button>(R.id.btnClipboardEnter)
        btnClipboardEnter?.setOnTouchListener { v, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                playEnterSound(btnClipboardEnter)
                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
            true
        }

        val rvClipboard = view.findViewById<RecyclerView>(R.id.keyboard_recycler_view)
        adapter = PinnedAdapter(DataManager.loadItems(this), 
            onItemClick = { text -> currentInputConnection?.commitText(text, 1) },
            onItemLongClick = { item, position -> handleLongPressItem(item, position) }
        )
        rvClipboard.layoutManager = GridLayoutManager(this, 3) 
        rvClipboard.adapter = adapter

        allQrItems = DataManager.loadQuickReplies(this)
        qrAdapter = QuickReplyKeyboardAdapter(allQrItems) { selectedItem ->
            playClickFeedback(null)
            val ic = currentInputConnection ?: return@QuickReplyKeyboardAdapter
            val textBefore = ic.getTextBeforeCursor(50, 0)?.toString() ?: ""
            val lastTriggerIndex = textBefore.lastIndexOf(qrTriggerChar)
            if (lastTriggerIndex != -1) {
                val charsToDelete = textBefore.length - lastTriggerIndex
                ic.deleteSurroundingText(charsToDelete, 0)
            }
            ic.commitText(selectedItem.text + " ", 1)
            rvQuickRepliesKeyboard.visibility = View.GONE
        }
        rvQuickRepliesKeyboard.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvQuickRepliesKeyboard.adapter = qrAdapter

        // --- SISTEMA DEFINITIVO DE EMOJIS CON SCROLL INFINITO ---
        rvEmojis = view.findViewById(R.id.rv_emojis_keyboard)
        rvEmojis.layoutManager = GridLayoutManager(this, 8) 
        emojiAdapter = EmojiAdapter(emptyList()) { emoji ->
            playClickFeedback(null)
            currentInputConnection?.commitText(emoji, 1)
            DataManager.addRecentEmoji(this, emoji) // Auto-guarda en recientes
        }
        rvEmojis.adapter = emojiAdapter

        setKeyListeners(view as ViewGroup)
        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        soundEnabled = DataManager.isSoundEnabled(this)
        soundEnterEnabled = DataManager.isSoundEnterEnabled(this)
        vibrationEnabled = DataManager.isVibrationEnabled(this)
        autocorrectEnabled = DataManager.isAutocorrectEnabled(this)
        allQrItems = DataManager.loadQuickReplies(this)
        qrTriggerChar = DataManager.getQrTrigger(this)
        btnQrTrigger?.text = qrTriggerChar
        
        rvQuickRepliesKeyboard.visibility = View.GONE
        checkSystemClipboard()
        updateAutoCaps(info)
        layoutSuggestionsBar.visibility = View.GONE
        layoutTopBar.visibility = View.VISIBLE
    }

    // --- EL CEREBRO DE LAS CATEGORÍAS TIPO WHATSAPP ---
    private fun loadEmojiCategory(category: String, button: Button?) {
        if (button != null) playClickFeedback(button)
        val tvCategory = layoutEmojis.findViewById<TextView>(R.id.tvEmojiCategory)
        
        if (category == "RECENT") {
            tvCategory.text = "Recientes"
            val recents = DataManager.loadRecentEmojis(this)
            if (recents.isEmpty()) {
                emojiAdapter?.updateData(masterEmojiList)
                (rvEmojis.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(0, 0)
            } else {
                emojiAdapter?.updateData(recents)
            }
        } else {
            // Carga la lista completa para hacer scroll infinito
            emojiAdapter?.updateData(masterEmojiList)
            
            var offset = 0
            when(category) {
                "SMILEYS" -> { tvCategory.text = "Emoticonos y personas"; offset = 0 }
                "ANIMALS" -> { tvCategory.text = "Animales y naturaleza"; offset = emojisSmileys.split(",").size }
                "FOOD" -> { tvCategory.text = "Alimentos y bebidas"; offset = emojisSmileys.split(",").size + emojisAnimals.split(",").size }
                "SPORTS" -> { tvCategory.text = "Actividades"; offset = emojisSmileys.split(",").size + emojisAnimals.split(",").size + emojisFood.split(",").size }
                "TRAVEL" -> { tvCategory.text = "Viajes y destinos"; offset = emojisSmileys.split(",").size + emojisAnimals.split(",").size + emojisFood.split(",").size + emojisSports.split(",").size }
                "OBJECTS" -> { tvCategory.text = "Objetos"; offset = emojisSmileys.split(",").size + emojisAnimals.split(",").size + emojisFood.split(",").size + emojisSports.split(",").size + emojisTravel.split(",").size }
                "SYMBOLS" -> { tvCategory.text = "Símbolos"; offset = emojisSmileys.split(",").size + emojisAnimals.split(",").size + emojisFood.split(",").size + emojisSports.split(",").size + emojisTravel.split(",").size + emojisObjects.split(",").size }
            }
            // Navega rápido a la sección correcta como hace WhatsApp
            (rvEmojis.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(offset, 0)
        }
    }

    private fun checkQuickReplyTrigger() {
        val ic = currentInputConnection ?: return
        val textBefore = ic.getTextBeforeCursor(50, 0)?.toString() ?: ""
        
        val lastTriggerIndex = textBefore.lastIndexOf(qrTriggerChar)
        if (lastTriggerIndex != -1) {
            val query = textBefore.substring(lastTriggerIndex + qrTriggerChar.length)
            if (!query.contains(" ")) {
                val strictQuery = query.lowercase()
                val filteredList = allQrItems.filter {
                    it.shortcut.lowercase().contains(strictQuery) || it.text.lowercase().contains(strictQuery)
                }
                if (filteredList.isNotEmpty()) {
                    qrAdapter.updateData(filteredList)
                    rvQuickRepliesKeyboard.visibility = View.VISIBLE
                    return
                }
            }
        }
        rvQuickRepliesKeyboard.visibility = View.GONE
    }

    private fun startVoiceRecognition() {
        playClickFeedback(btnMic1)
        try {
            val intent = Intent(this, Class.forName("com.brayan.tecladoanclado.VoiceActivity"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Falla al iniciar micrófono", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playClickFeedback(v: View?) {
        backgroundExecutor.execute {
            if (soundEnabled) audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.8f)
        }
        if (vibrationEnabled && v != null) {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
        }
    }

    private fun playEnterSound(v: View?) {
        backgroundExecutor.execute {
            if (soundEnterEnabled) {
                try {
                    val mp = MediaPlayer.create(this, R.raw.sonido_enter)
                    mp.setOnCompletionListener { it.release() }
                    mp.start()
                } catch (e: Exception) {
                    if (soundEnabled) audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_RETURN, 0.8f)
                }
            } else if (soundEnabled) {
                audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_RETURN, 0.8f)
            }
        }
        if (vibrationEnabled && v != null) {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
        }
    }

    private fun levenshtein(a: String, b: String): Int {
        var v0 = IntArray(b.length + 1) { it }
        var v1 = IntArray(b.length + 1)
        for (i in 0 until a.length) {
            v1[0] = i + 1
            for (j in 0 until b.length) {
                val cost = if (a[i] == b[j]) 0 else 1
                v1[j + 1] = minOf(v1[j] + 1, v0[j + 1] + 1, v0[j] + cost)
            }
            val temp = v0; v0 = v1; v1 = temp
        }
        return v0[b.length]
    }

    private fun getCurrentWord(): String {
        val ic = currentInputConnection ?: return ""
        val textBefore = ic.getTextBeforeCursor(50, 0)?.toString() ?: return ""
        return textBefore.takeLastWhile { it.isLetter() || it == 'ñ' || it == 'Ñ' || it == 'á' || it == 'é' || it == 'í' || it == 'ó' || it == 'ú' }
    }

    private fun updateSuggestionsUI() {
        if (!autocorrectEnabled) return
        val currentWord = getCurrentWord()
        
        if (currentWord.length >= 2) {
            val lowerWord = currentWord.lowercase()
            val cleanLower = removeAccents(lowerWord)

            var matches = learnedWords.asSequence()
                .filter { removeAccents(it).startsWith(cleanLower) && it != lowerWord }
                .take(2).toList()

            if (matches.isEmpty() && currentWord.length >= 3) {
                matches = learnedWords.asSequence()
                    .filter { it.startsWith(cleanLower[0]) || it.startsWith('h') }
                    .filter { Math.abs(it.length - cleanLower.length) <= 1 }
                    .filter { levenshtein(cleanLower, removeAccents(it)) <= 1 }
                    .take(2).toList()
            }

            btnSuggest1.text = currentWord
            
            if (matches.isNotEmpty()) {
                currentBestSuggestion = matches[0]
                if (currentWord[0].isUpperCase()) currentBestSuggestion = currentBestSuggestion.replaceFirstChar { it.uppercase() }
                btnSuggest2.text = currentBestSuggestion
                btnSuggest3.text = if (matches.size > 1) matches[1] else ""
            } else {
                currentBestSuggestion = ""
                btnSuggest2.text = ""
                btnSuggest3.text = ""
            }
            layoutTopBar.visibility = View.GONE
            layoutSuggestionsBar.visibility = View.VISIBLE
        } else {
            currentBestSuggestion = ""
            layoutSuggestionsBar.visibility = View.GONE
            layoutTopBar.visibility = View.VISIBLE
        }
    }

    private fun insertSuggestion(suggestion: String) {
        val currentWord = getCurrentWord()
        val ic = currentInputConnection ?: return
        if (currentWord.isNotEmpty() && suggestion.isNotEmpty()) {
            ic.deleteSurroundingText(currentWord.length, 0)
            ic.commitText("$suggestion ", 1)
            learnWord(suggestion)
        }
        mainHandler.postDelayed({ updateSuggestionsUI() }, 10)
    }

    private fun learnWord(word: String) {
        if (word.length > 2) {
            val lowerWord = word.lowercase()
            if (!learnedWords.contains(lowerWord)) {
                learnedWords.add(lowerWord)
                backgroundExecutor.execute { DataManager.saveLearnedWords(this, learnedWords) }
            }
        }
    }

    private fun removeAccents(str: String): String {
        return str.replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")
    }

    // AQUÍ DEVOLVEMOS LA FUNCIÓN PERDIDA DE TRADUCIR
    private fun translateText(btnSend: Button) {
        playClickFeedback(btnSend)
        val ic = currentInputConnection ?: return
        val textToTranslate = ic.getTextBeforeCursor(1000, 0)?.toString() ?: ""
        if (textToTranslate.isNotBlank()) {
            btnSend.text = "⏳..."
            btnSend.isEnabled = false
            thread {
                try {
                    val sl = if (isEsToEn) "es" else "en"
                    val tl = if (isEsToEn) "en" else "es"
                    val encodedText = URLEncoder.encode(textToTranslate, "UTF-8")
                    val urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sl&tl=$tl&dt=t&q=$encodedText"
                    val conn = URL(urlStr).openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                    conn.connectTimeout = 5000
                    
                    if (conn.responseCode == 200) {
                        val response = conn.inputStream.bufferedReader().readText()
                        val jsonArray = JSONArray(response)
                        val translatedText = jsonArray.getJSONArray(0).getJSONArray(0).getString(0)
                        mainHandler.post {
                            ic.deleteSurroundingText(textToTranslate.length, 0)
                            ic.commitText(translatedText, 1)
                            btnSend.text = "✨ Traducir texto"
                            btnSend.isEnabled = true
                            layoutTranslatorBar.visibility = View.GONE
                            layoutTopBar.visibility = View.VISIBLE
                        }
                    } else throw Exception()
                } catch (e: Exception) {
                    mainHandler.post {
                        Toast.makeText(this@MiTecladoAnclado, "Error de red", Toast.LENGTH_SHORT).show()
                        btnSend.text = "✨ Traducir texto"
                        btnSend.isEnabled = true
                    }
                }
            }
        }
    }

    // --- ESTO ELIMINA EL LAG DEL ESPACIO AL SUGERIR PALABRAS ---
    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        updateAutoCaps(currentInputEditorInfo)
        updateSuggestionsUI() // Ahora es instantáneo al detectar que escribiste un espacio
        checkQuickReplyTrigger() 
    }

    private fun updateAutoCaps(info: EditorInfo?) {
        if (info != null && shiftState != 2) { 
            val capsMode = currentInputConnection?.getCursorCapsMode(info.inputType) ?: 0
            if (capsMode != 0) setShiftState(1) else if (shiftState == 1) setShiftState(0)
        }
    }

    private fun setShiftState(state: Int) {
        shiftState = state
        val isUpper = shiftState > 0
        updateLettersCase(layoutLetters as ViewGroup, isUpper)
    }

    private fun updateLettersCase(group: ViewGroup, isUpper: Boolean) {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child is ViewGroup) {
                updateLettersCase(child, isUpper)
            } else if (child is Button) {
                val tag = child.tag as? String
                if (tag == "SHIFT") {
                    child.text = when(shiftState) { 0 -> "⇧"; 1 -> "⬆"; else -> "⇪" }
                } else if (child.text.length == 1) {
                    val letter = child.text.toString()
                    if (letter[0].isLetter() || letter == "ñ" || letter == "Ñ") {
                        child.text = if (isUpper) letter.uppercase() else letter.lowercase()
                    }
                }
            }
        }
    }

    private fun setKeyListeners(parent: ViewGroup) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is ViewGroup) setKeyListeners(child)
            else if (child is Button) {
                val tag = child.tag as? String
                
                if (tag == "SUGGESTION") continue
                
                val isActionKey = tag in listOf(
                    "MIC", "OPEN_TRANSLATOR", "CLIPBOARD", "MODE_LETTERS", "CLEAR_CLIPBOARD", 
                    "OPEN_EMOJI", "MODE_SYM1", "MODE_SYM2", "MODE_NUMPAD", "CLOSE_TRANSLATOR", 
                    "LANG_TOGGLE", "TRANSLATE_SEND", "TYPE_TRIGGER",
                    "CAT_RECENT", "CAT_SMILEYS", "CAT_ANIMALS", "CAT_FOOD", "CAT_SPORTS", 
                    "CAT_TRAVEL", "CAT_OBJECTS", "CAT_SYMBOLS"
                )
                
                if (isActionKey) {
                    child.setOnTouchListener { v, event ->
                        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                            handleKeyPress(child)
                        }
                        true
                    }
                    continue
                }

                if (tag == "DELETE") {
                    child.setOnTouchListener { v, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                                playClickFeedback(v)
                                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
                                mainHandler.postDelayed({ 
                                    updateSuggestionsUI()
                                    checkQuickReplyTrigger()
                                }, 10)
                                mainHandler.postDelayed(deleteRunnable, 400) 
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                                mainHandler.removeCallbacks(deleteRunnable)
                            }
                        }
                        true
                    }
                    continue
                }

                var isLongPress = false
                var longPressRunnable: Runnable? = null

                child.setOnTouchListener { v, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                            isLongPress = false
                            playClickFeedback(v)

                            if (tag == null || tag.matches(Regex("\\d"))) {
                                val textToInsert = child.text.toString()
                                currentInputConnection?.commitText(textToInsert, 1)
                                if (shiftState == 1) setShiftState(0)
                                mainHandler.postDelayed({ 
                                    updateSuggestionsUI()
                                    checkQuickReplyTrigger()
                                }, 10)
                            } else if (tag == "SPACE" || tag == "ENTER" || tag == "SHIFT") {
                                handleKeyPress(child)
                            }

                            val text = child.text.toString().lowercase()
                            val accentedChar = when(text) { "a"->"á"; "e"->"é"; "i"->"í"; "o"->"ó"; "u"->"ú"; "n"->"ñ"; else -> null }
                            
                            if (accentedChar != null) {
                                longPressRunnable = Runnable {
                                    isLongPress = true
                                    if (vibrationEnabled) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                                    
                                    currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                                    currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
                                    
                                    val textToInsert = if (shiftState > 0) accentedChar.uppercase() else accentedChar
                                    currentInputConnection?.commitText(textToInsert, 1)
                                    mainHandler.postDelayed({ 
                                        updateSuggestionsUI()
                                        checkQuickReplyTrigger()
                                    }, 10)
                                }
                                mainHandler.postDelayed(longPressRunnable!!, 350)
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                            longPressRunnable?.let { mainHandler.removeCallbacks(it) }
                        }
                    }
                    true
                }
            }
        }
    }

    private fun handleKeyPress(button: Button) {
        val tag = button.tag as? String

        // --- ACCIONES DE UI INMORTALES (Funcionan siempre) ---
        when (tag) {
            "CAT_RECENT" -> { loadEmojiCategory("RECENT", button); return }
            "CAT_SMILEYS" -> { loadEmojiCategory("SMILEYS", button); return }
            "CAT_ANIMALS" -> { loadEmojiCategory("ANIMALS", button); return }
            "CAT_FOOD" -> { loadEmojiCategory("FOOD", button); return }
            "CAT_SPORTS" -> { loadEmojiCategory("SPORTS", button); return }
            "CAT_TRAVEL" -> { loadEmojiCategory("TRAVEL", button); return }
            "CAT_OBJECTS" -> { loadEmojiCategory("OBJECTS", button); return }
            "CAT_SYMBOLS" -> { loadEmojiCategory("SYMBOLS", button); return }
            
            "MODE_LETTERS" -> { switchLayout(layoutLetters); return }
            "OPEN_EMOJI" -> { switchLayout(layoutEmojis); loadEmojiCategory("RECENT", null); return }
            "MODE_SYM1" -> { switchLayout(layoutSymbols1); return }
            "MODE_SYM2" -> { switchLayout(layoutSymbols2); return }
            "MODE_NUMPAD" -> { switchLayout(layoutNumpad); return }
            "CLIPBOARD" -> { checkSystemClipboard(); switchLayout(layoutClipboard); return }
            
            "OPEN_TRANSLATOR" -> { layoutTopBar.visibility = View.GONE; layoutSuggestionsBar.visibility = View.GONE; layoutTranslatorBar.visibility = View.VISIBLE; return }
            "CLOSE_TRANSLATOR" -> { layoutTranslatorBar.visibility = View.GONE; layoutTopBar.visibility = View.VISIBLE; return }
            "MIC" -> { startVoiceRecognition(); return }
            "LANG_TOGGLE" -> {
                playClickFeedback(button)
                isEsToEn = !isEsToEn
                btnLangToggle.text = if (isEsToEn) "ES ➔ EN" else "EN ➔ ES"
                return
            }
        }

        // --- ACCIONES DE ESCRITURA (Requieren input connection) ---
        val ic = currentInputConnection ?: return

        when (tag) {
            "ENTER" -> {
                playEnterSound(button)
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
            "SPACE" -> {
                playClickFeedback(button)
                val word = getCurrentWord()
                if (autocorrectEnabled && currentBestSuggestion.isNotEmpty() && removeAccents(currentBestSuggestion.lowercase()) == removeAccents(word.lowercase()) && currentBestSuggestion != word) {
                    ic.deleteSurroundingText(word.length, 0)
                    ic.commitText("$currentBestSuggestion ", 1)
                    learnWord(currentBestSuggestion)
                } else {
                    if (word.isNotEmpty()) learnWord(word)
                    ic.commitText(" ", 1)
                }
                mainHandler.postDelayed({ 
                    updateSuggestionsUI() 
                    checkQuickReplyTrigger()
                }, 10)
            }
            "TYPE_TRIGGER" -> {
                playClickFeedback(button)
                ic.commitText(qrTriggerChar, 1)
                mainHandler.postDelayed({ checkQuickReplyTrigger() }, 10)
            }
            "CLEAR_CLIPBOARD" -> clearUnpinned()
            "SHIFT" -> {
                val now = System.currentTimeMillis()
                if (now - lastShiftTime < 400) setShiftState(2) else setShiftState(if (shiftState == 0) 1 else 0)
                lastShiftTime = now
            }
            "TRANSLATE_SEND" -> translateText(button)
        }
    }

    private fun switchLayout(activeLayout: View) {
        layoutLetters.visibility = View.GONE
        layoutSymbols1.visibility = View.GONE
        layoutSymbols2.visibility = View.GONE
        layoutNumpad.visibility = View.GONE
        layoutClipboard.visibility = View.GONE
        layoutEmojis.visibility = View.GONE 
        activeLayout.visibility = View.VISIBLE
    }

    private fun checkSystemClipboard() {
        try {
            if (clipboardManager.hasPrimaryClip()) {
                val clip = clipboardManager.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val newText = clip.getItemAt(0).coerceToText(this)?.toString()?.trim()
                    if (!newText.isNullOrBlank()) {
                        val items = DataManager.loadItems(this)
                        if (items.find { it.text == newText } == null) {
                            items.add(0, ClipboardItem(newText, false))
                            DataManager.saveItems(this, items)
                        }
                    }
                }
            }
        } catch (e: Exception) {}
        if (::adapter.isInitialized) mainHandler.post { adapter.updateData(DataManager.loadItems(this)) }
    }

    private fun handleLongPressItem(item: ClipboardItem, position: Int) {
        val items = DataManager.loadItems(this)
        val realItem = items.find { it.text == item.text } ?: return
        if (realItem.isPinned) {
            realItem.isPinned = false
            Toast.makeText(this, "Elemento desanclado", Toast.LENGTH_SHORT).show()
        } else {
            realItem.isPinned = true
            items.remove(realItem); items.add(0, realItem)
            Toast.makeText(this, "📌 Elemento anclado", Toast.LENGTH_SHORT).show()
        }
        DataManager.saveItems(this, items)
        adapter.updateData(items)
    }

    private fun clearUnpinned() {
        val items = DataManager.loadItems(this)
        val pinnedOnly = items.filter { it.isPinned }.toMutableList()
        DataManager.saveItems(this, pinnedOnly)
        adapter.updateData(pinnedOnly)
        Toast.makeText(this, "Borrados", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        clipboardManager.removePrimaryClipChangedListener(clipListener) 
        try { unregisterReceiver(voiceReceiver) } catch (e: Exception) {}
    }

    inner class EmojiAdapter(private var emojiList: List<String>, private val onEmojiClick: (String) -> Unit) : RecyclerView.Adapter<EmojiAdapter.EmojiViewHolder>() {
        fun updateData(newList: List<String>) {
            emojiList = newList
            notifyDataSetChanged()
        }
        
        inner class EmojiViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiViewHolder {
            val tv = TextView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120)
                gravity = android.view.Gravity.CENTER
                textSize = 28f
            }
            return EmojiViewHolder(tv)
        }
        override fun onBindViewHolder(holder: EmojiViewHolder, position: Int) {
            holder.textView.text = emojiList[position]
            holder.textView.setOnClickListener { onEmojiClick(emojiList[position]) }
        }
        override fun getItemCount() = emojiList.size
    }

    inner class QuickReplyKeyboardAdapter(private var items: List<QuickReplyItem>, private val onClick: (QuickReplyItem) -> Unit) : RecyclerView.Adapter<QuickReplyKeyboardAdapter.QRViewHolder>() {
        fun updateData(newItems: List<QuickReplyItem>) {
            this.items = newItems
            notifyDataSetChanged()
        }
        inner class QRViewHolder(val view: LinearLayout) : RecyclerView.ViewHolder(view) {
            val tvShortcut = TextView(view.context)
            val tvText = TextView(view.context)
            init {
                view.orientation = LinearLayout.VERTICAL
                view.setPadding(24, 24, 24, 24)
                view.setBackgroundColor(Color.parseColor("#1E1E1E"))
                val params = LinearLayout.LayoutParams(400, ViewGroup.LayoutParams.MATCH_PARENT)
                params.setMargins(8, 8, 8, 8)
                view.layoutParams = params
                tvShortcut.setTextColor(Color.parseColor("#2196F3"))
                tvShortcut.textSize = 15f
                tvShortcut.setTypeface(null, Typeface.BOLD)
                tvText.setTextColor(Color.WHITE)
                tvText.textSize = 13f
                tvText.maxLines = 4
                tvText.ellipsize = android.text.TextUtils.TruncateAt.END
                tvText.setPadding(0, 8, 0, 0)
                view.addView(tvShortcut)
                view.addView(tvText)
            }
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = QRViewHolder(LinearLayout(parent.context))
        override fun onBindViewHolder(holder: QRViewHolder, position: Int) {
            val item = items[position]
            holder.tvShortcut.text = "⚡ ${item.shortcut}"
            holder.tvText.text = item.text
            holder.view.setOnClickListener { onClick(item) }
        }
        override fun getItemCount() = items.size
    }
}
