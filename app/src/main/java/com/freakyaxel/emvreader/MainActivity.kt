package com.freakyaxel.emvreader

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.Alignment
import androidx.lifecycle.LifecycleOwner
import android.content.Context
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.freakyaxel.emvparser.api.CardData
import com.freakyaxel.emvparser.api.CardDataResponse
import com.freakyaxel.emvparser.api.EMVReader
import com.freakyaxel.emvparser.api.EMVReaderLogger
import com.freakyaxel.emvparser.api.fold
import com.freakyaxel.emvreader.ui.theme.EMVReaderTheme

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback, EMVReaderLogger {

    private val cardStateLabel = mutableStateOf("Tap Card to read")
    private var transactionAmount by mutableStateOf(0)
    private val emvReader = EMVReader.get(this)
    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        setContent {
            EMVReaderTheme {
                val lifecycleOwner = LocalLifecycleOwner.current
                val context = LocalContext.current

                if (transactionAmount == 0) {
                    EnterTransactionAmountScreen(
                        onAmountEntered = { amount ->
                            transactionAmount = amount
                            cardStateLabel.value = "Tap Card to read"
                        },
                        lifecycleOwner = lifecycleOwner,
                        context = context
                    )
                } else if (transactionAmount > 0 && cardStateLabel.value == "Tap Card to read") {
                    CardDataScreen(data = cardStateLabel.value)
                } else if (cardStateLabel.value == "Enter the PIN to make this transaction") {
                    PinInputScreen(
                        onPinSuccess = { msg ->
                            cardStateLabel.value = msg
                        }
                    )
                } else if (cardStateLabel.value == "Card lost. Keep card steady!") {
                    CardDataScreen(data = cardStateLabel.value)
                } else if (cardStateLabel.value == "Card is not supported!") {
                    CardDataScreen(data = cardStateLabel.value)
                } else if (cardStateLabel.value == "Transaction Successful") {
                    SuccessScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableReaderMode(
            this,
            this,
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
                    NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_BARCODE or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V,
            null
        )
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

        // Handle the transaction based on the amount.
        if (transactionAmount <= 100) {
            cardStateLabel.value = "Transaction Successful"
        } else {
            cardStateLabel.value = "Enter the PIN to make this transaction"
            // TODO: Implement PIN entry logic here.
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterTransactionAmountScreen(
    onAmountEntered: (Int) -> Unit,
    lifecycleOwner: LifecycleOwner,
    context: Context
) {
    var amountText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Enter the amount:")

        // TextField to allow the user to input the transaction amount.
        TextField(
            value = amountText,
            onValueChange = { amountText = it },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )

        // Button to submit the transaction amount.
        Button(
            onClick = {
                // Parse the amount from the input text and update the transactionAmount variable.
                val amount = amountText.toIntOrNull()
                if (amount != null && amount > 0) {
                    onAmountEntered(amount)
                } else {
                    // Show an error message if the input is not a valid positive integer.
                    Toast.makeText(
                        context,
                        "Invalid amount. Please enter a valid positive integer.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        ) {
            Text(text = "Submit")
        }
    }
}


@Composable
fun CardDataScreen(data: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = data)
    }
}

@Preview
@Composable
fun SuccessScreen(
    activity: MainActivity = MainActivity()
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Transaction Successful!", fontSize = 30.sp)
        Button(onClick = {
            activity.finish()
        }) {
            Text(text = "Exit")
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