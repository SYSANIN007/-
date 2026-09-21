package com.example.acidwallet.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.acidwallet.domain.Money
import com.example.acidwallet.ui.theme.AcidColors

/** Универсальная карточка-секция с необязательной цветной рамкой. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val dark = isSystemInDarkTheme()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = AcidColors.card(dark),
        border = accent?.let { BorderStroke(1.dp, it.copy(alpha = 0.5f)) }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

/** Заголовок карточки ACID-свойства: буква, название, пояснение. */
@Composable
fun PropertyHeader(letter: String, color: Color, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = color.copy(alpha = 0.18f),
            shape = CircleShape,
            border = BorderStroke(1.dp, color.copy(alpha = 0.7f))
        ) {
            Text(
                text = letter,
                color = color,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Блок кода: моноширинный шрифт, тёмный фон, прокрутка по горизонтали. */
@Composable
fun CodeBlock(code: String, color: Color = AcidColors.Atomicity) {
    val dark = isSystemInDarkTheme()
    Surface(
        color = AcidColors.code(dark),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = code,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            color = color.copy(alpha = 0.92f),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        )
    }
}

/** Строка «подпись — значение» для отчётов. */
@Composable
fun KeyValueRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    emphasize: Boolean = false
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = if (emphasize) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal,
            color = valueColor,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Результат проверки: галочка или предупреждение. */
@Composable
fun ResultRow(ok: Boolean, text: String) {
    val color = if (ok) AcidColors.Ok else AcidColors.Danger
    Row(verticalAlignment = Alignment.Top) {
        Surface(color = color.copy(alpha = 0.18f), shape = CircleShape, modifier = Modifier.size(22.dp)) {
            Text(
                text = if (ok) "✓" else "!",
                color = color,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Маленький цветной бейдж. */
@Composable
fun Badge(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.16f), shape = RoundedCornerShape(8.dp)) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/** Крупная сумма денег. */
@Composable
fun MoneyText(cents: Long, color: Color = MaterialTheme.colorScheme.onSurface, signed: Boolean = false) {
    Text(
        text = if (signed) Money.formatSigned(cents) else Money.format(cents),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = color
    )
}

/** Заголовок раздела внутри экрана. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/** Разделительный отступ. */
@Composable
fun VSpace(height: Int = 8) = Spacer(Modifier.height(height.dp))

/** Полупрозрачная плашка-подсказка. */
@Composable
fun HintBox(text: String, color: Color = AcidColors.Atomicity) {
    Surface(color = color.copy(alpha = 0.10f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp)
        )
    }
}

/** Рамка вокруг блока (для примеров «как не надо» / «как надо»). */
@Composable
fun TintedBox(color: Color, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = color.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/** Мини-«светофор» слева от текста отчёта. */
@Composable
fun StatusDot(color: Color) {
    Surface(color = color, shape = CircleShape, modifier = Modifier.size(10.dp)) { Spacer(Modifier.size(10.dp)) }
}

/** Моноширинный текст небольшого размера (для идентификаторов, чисел). */
@Composable
fun MonoText(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = color)
}

/** Фон-подложка под «тёмную» карточку. */
@Composable
fun SubtleBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    Surface(color = AcidColors.cardAlt(dark), shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Column(Modifier.padding(12.dp)) { content() }
    }
}

/** Текст с фоном «код» внутри строки. */
@Composable
fun InlineCode(text: String) {
    val dark = isSystemInDarkTheme()
    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall,
        color = AcidColors.Atomicity,
        modifier = Modifier
            .background(AcidColors.code(dark), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
