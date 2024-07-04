package com.scottishtecharmy.soundscape

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun CreditScreen(
    modifier: Modifier = Modifier
) {

    CreditScreenContent(
        modifier = modifier
    )
}
@Composable
private fun CreditScreenContent(
    modifier: Modifier = Modifier
) {

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
        modifier = modifier
            .fillMaxSize()
            .padding(48.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.fmod_logo),
            contentDescription = stringResource(id = R.string.fmod_logo_description)
        )
        Text(
            text = "Audio Engine: FMOD Studio by Firelight Technologies Pty Ltd.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall
        )
    }
}
