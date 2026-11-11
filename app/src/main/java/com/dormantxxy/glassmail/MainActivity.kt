package com.dormantxxy.glassmail

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dormantxxy.glassmail.ui.AppViewModel
import com.dormantxxy.glassmail.ui.GlassMailRoot
import com.dormantxxy.glassmail.ui.MailtoRequest

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()
    private var mailtoRequest by mutableStateOf<MailtoRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        mailtoRequest = parseMailto(intent)
        setContent { GlassMailRoot(viewModel, mailtoRequest) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        mailtoRequest = parseMailto(intent)
    }

    private fun parseMailto(intent: Intent?): MailtoRequest? {
        if (intent?.action != Intent.ACTION_SENDTO || intent.data?.scheme != "mailto") return null
        val uri = intent.data ?: return null
        val to = uri.schemeSpecificPart.substringBefore('?').let(Uri::decode)
        return MailtoRequest(
            id = System.nanoTime(),
            to = to,
            subject = uri.getQueryParameter("subject").orEmpty(),
            body = uri.getQueryParameter("body").orEmpty()
        )
    }
}
