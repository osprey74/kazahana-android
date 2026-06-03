package com.kazahana.app.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kazahana.app.R
import com.kazahana.app.data.model.VerificationState

private val VerificationBlue = Color(0xFF0EA5E9) // sky-500

fun isVerifiedAccount(verification: VerificationState?): Boolean =
    verification?.verifiedStatus == "valid"

fun isTrustedVerifier(verification: VerificationState?): Boolean =
    verification?.trustedVerifierStatus == "valid"

/**
 * Bluesky verification badge displayed next to display names.
 * Trusted verifiers take precedence over verified accounts (matching the desktop client).
 */
@Composable
fun VerificationBadge(
    verification: VerificationState?,
    modifier: Modifier = Modifier,
    size: Dp = 14.dp,
) {
    val trusted = isTrustedVerifier(verification)
    val verified = !trusted && isVerifiedAccount(verification)
    if (!trusted && !verified) return

    Icon(
        imageVector = if (trusted) Icons.Filled.WorkspacePremium else Icons.Filled.Verified,
        contentDescription = stringResource(
            if (trusted) R.string.verification_trusted_verifier else R.string.verification_verified,
        ),
        tint = VerificationBlue,
        modifier = modifier.size(size),
    )
}
