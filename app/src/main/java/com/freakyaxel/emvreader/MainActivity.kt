package com.freakyaxel.emvreader

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcA
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.freakyaxel.emvparser.api.CardData
import com.freakyaxel.emvparser.api.CardDataResponse
import com.freakyaxel.emvparser.api.EMVReader
import com.freakyaxel.emvparser.api.EMVReaderLogger
import com.freakyaxel.emvparser.api.fold
import com.freakyaxel.emvreader.ui.theme.EMVReaderTheme

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback, EMVReaderLogger {

    private val cardStateLabel = mutableStateOf("Tap Card to read")

    private val emvReader = EMVReader.get(this)

    private var nfcAdapter: NfcAdapter? = null
    private var isNfcDiscovered by mutableStateOf(false)
    private var isAmountEntered by mutableStateOf(true)
    private var enteredAmount by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        var enteredNum = 0
        setContent {
            EMVReaderTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "start") {
                    composable("start") {
                        StartController(
                            this@MainActivity,
                            { num, bool -> enteredNum = num; isAmountEntered = bool
                            }, navController
                        )
                    }
                    composable("pinInput") {
                        PinInputScreen(navController)
                    }
                    composable("success") {
                        SuccessScreen(navController)
                    }
                    composable("nfcReader") {
                        NfcReaderScreen(
                            this@MainActivity,
                            enteredAmount = enteredNum,
                            navController
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun StartController(
        context: Context,
        pass: (Int, Boolean) -> Unit,
        navController: NavController
    ) {
        var enteredNum by remember { mutableStateOf(0) }
        var isAmountEntered by remember { mutableStateOf(true) }
        pass(enteredNum, isAmountEntered)
        Column() {
            if (isAmountEntered) {
                AmountEntryScreen { number ->
                    enteredNum = number
                    isAmountEntered = false
                    navController.navigate("nfcReader")
                }
        }
            //            else {
//                navController.navigate("nfcReader")
//            }
        }
    }
    private fun enableForegroundDispatch(activity: MainActivity, adapter: NfcAdapter?) {

        val intent = Intent(activity.applicationContext, activity.javaClass)
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP

        val pendingIntent = PendingIntent.getActivity(
            activity.applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val filters = arrayOfNulls<IntentFilter>(1)
        val techList = arrayOf<Array<String>>()

        filters[0] = IntentFilter()
        with(filters[0]) {
            this?.addAction(NfcAdapter.ACTION_NDEF_DISCOVERED)
            this?.addCategory(Intent.CATEGORY_DEFAULT)
            try {
                this?.addDataType("text/plain")
            } catch (ex: IntentFilter.MalformedMimeTypeException) {
                throw RuntimeException("")
            }
        }

        adapter?.enableForegroundDispatch(activity, pendingIntent, filters, techList)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        var tagFromIntent: Tag? = intent?.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        val nfc = NfcA.get(tagFromIntent)

        val atqa: ByteArray = nfc.atqa
        val sak: Short = nfc.sak
        val commandBytes: ByteArray = byteArrayOf(
            0x00,
            0xA4.toByte(),
            0x04,
            0x00,
            0x07,
            0xF0.toByte(),
            0xCA.toByte(),
            0xFE.toByte(),
            0xBA.toByte(),
            0xBE.toByte(),
            0xDE.toByte(),
            0xAD.toByte(),
            0xBE.toByte(),
            0xEF.toByte()
        )
        nfc.connect()

        val isConnected = nfc.isConnected

        if (isConnected) {
            val receivedData: ByteArray = nfc.transceive(commandBytes)
            isNfcDiscovered = true
            val receivedString: String = receivedData.toString(Charsets.UTF_8)
            cardStateLabel.value = receivedString
        }
    }

    override fun onResume() {
        super.onResume()

        enableForegroundDispatch(this, this.nfcAdapter)

    }

    override fun emvLog(key: String, value: String) {
        Log.e(key, value)
    }

    override fun onTagDiscovered(tag: Tag) {
        cardStateLabel.value = "Reading Card..."

        val cardTag = EmvCardTag.get(tag)
        val cardData = emvReader.getCardData(cardTag)

        cardStateLabel.value = cardData.fold(
            onError = { it.error.message },
            onSuccess = { getCardLabel(it.cardData) },
            onTagLost = { "Card lost. Keep card steady!" },
            onCardNotSupported = { getCardNotSupportedLabel(it) }
        )
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }
}


private fun getCardNotSupportedLabel(response: CardDataResponse.CardNotSupported): String {
    val aids = response.aids
    return """
        Card is not supported!
        AID: ${aids.takeIf { it.isNotEmpty() }?.joinToString(" | ") ?: "NOT FOUND"}
    """.trimIndent()
}

private fun getCardLabel(cardData: CardData?): String {
    return """
        AID: ${cardData?.aid?.joinToString(" | ")}
        Number: ${cardData?.formattedNumber}
        Expires: ${cardData?.formattedExpDate}
    """.trimIndent()
}

@Composable
fun CardDataScreen(data: String) {
    var data1 by remember {
        mutableStateOf("")
    }
    var clicked by remember {
        mutableStateOf(false)
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextButton(onClick = {
            data1 = "Reading Card ..."
        }) {
            Text(text = "Tap card to read ")
        }
        if (clicked) {
            Text(text = "")
        } else {
            Text(text = data1)
        }

    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    EMVReaderTheme {
        CardDataScreen(data = getCardLabel(null))
    }
}

@Composable
fun NfcReaderScreen(
    context: Context,
    enteredAmount: Int,
    navController: NavController
) {
    val nfcStatusText = remember { mutableStateOf("NFC Status: ") }
    val isCardPresent = remember { mutableStateOf(false) }
    val enteredPIN = remember { mutableStateOf("") }
    val isTransactionSuccessful = remember { mutableStateOf(false) } // New state variable

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = nfcStatusText.value)

        if (isCardPresent.value) {
            if (enteredAmount > 100) {
//                IF Amount is greater than 100 move to pin input screen and check is the entered Pin
//                is equal to a value say 4848
//                then display success on a clear screen
                navController.navigate("pinInput")
            } else {
                Text(text = "Transaction successful...") // Show card found message
//                Display success on a clear screen
            }
        }

        Button(
            onClick = {
                val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
                if (nfcAdapter != null) {
                    if (!nfcAdapter.isEnabled) {
                        nfcStatusText.value = "NFC Status: NFC is not enabled"
                        return@Button
                    }

                    nfcStatusText.value = "NFC Status: Detecting NFC Tag..."
                    val tag = nfcAdapter

                    // TODO: Handle tag connection, transceive data, and update status
                    // For example:
                    isCardPresent.value = true

                } else {
                    nfcStatusText.value = "NFC Status: NFC is not supported on this device"
                }
            }
        ) {
            Text(text = "Detect NFC")
        }
    }
}