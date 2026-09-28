package com.example.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BullGreen
import com.example.ui.theme.SkyBlue

@Composable
fun TelegramAlertDialog(
    onSendAlert: (botToken: String, chatId: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var botToken by remember { mutableStateOf("") }
    var chatId by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("📢 Telegram Alerts Configuration", fontWeight = FontWeight.Bold, color = Color.White)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Receive instant alerts for newly detected TOUCH, BOUNCE, and WATCH setups via Telegram Bot API.",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = botToken,
                    onValueChange = { botToken = it },
                    label = { Text("Telegram Bot Token") },
                    placeholder = { Text("e.g. 123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("bot_token_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = chatId,
                    onValueChange = { chatId = it },
                    label = { Text("Telegram Chat ID / Channel ID") },
                    placeholder = { Text("e.g. -100123456789 or @channel") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("chat_id_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSendAlert(botToken, chatId) },
                colors = ButtonDefaults.buttonColors(containerColor = SkyBlue),
                modifier = Modifier.testTag("send_telegram_button")
            ) {
                Text("Send Alert Test")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = Color(0xFF1E293B),
        modifier = modifier
    )
}
