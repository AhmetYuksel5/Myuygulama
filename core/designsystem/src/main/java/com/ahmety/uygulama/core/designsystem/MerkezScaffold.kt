@file:OptIn(ExperimentalMaterial3Api::class)

package com.ahmety.uygulama.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Ekranların ortak başlığı.
 *
 * Dokuz ekran, üç ayrı başlık boyu ve hiç geri düğmesi yoktu; her sayfa
 * başka biri yazmış gibi duruyordu. Alt sayfalara ([onBack] verilenlere)
 * geri oku da buradan geliyor — eskiden tek çıkış yolu sistem hareketiydi.
 */
@Composable
fun MerkezTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(MerkezIcons.Back, contentDescription = "Geri")
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            // Saydam: başlık sayfanın zemininde duruyor, ayrı bir şerit
            // gibi değil. Kaydırınca içerik altından geçiyor.
            containerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier,
    )
}

/**
 * Bir listenin boş hâli.
 *
 * Bütün boş ekranlar tek satır gri yazıydı — hâlbuki bunlar uygulamanın en
 * çok görülen anları: destesini bitirdiğin an, henüz hiçbir şey
 * kaydetmediğin an. Bir çizim, iki satır ve tek bir düğme.
 */
@Composable
fun MerkezEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    glyph: @Composable (() -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MerkezSpacing.xl, vertical = MerkezSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MerkezSpacing.sm),
    ) {
        if (glyph != null) {
            Row(modifier = Modifier.size(96.dp), verticalAlignment = Alignment.CenterVertically) {
                glyph()
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                modifier = Modifier.padding(top = MerkezSpacing.xs),
            ) {
                Text(actionLabel)
            }
        }
    }
}
