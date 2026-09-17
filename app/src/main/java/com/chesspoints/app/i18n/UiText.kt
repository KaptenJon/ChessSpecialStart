package com.chesspoints.app.i18n

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalResources

/**
 * A resolved-at-render-time piece of user-facing text.
 *
 * State and mapping layers describe *what* should be said with a string resource
 * plus arguments; the Compose layer resolves it against the active configuration,
 * so the device/system locale is always honoured and missing translations fall
 * back to the default `values/strings.xml` catalogue.
 */
@Immutable
sealed interface UiText {

    @Immutable
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    /** Text that is already locale-independent (algebraic squares, brand names). */
    @Immutable
    data class Raw(val value: String) : UiText

    @Immutable
    data class Joined(val parts: List<UiText>, val separator: String) : UiText

    companion object {
        fun of(@StringRes id: Int, vararg args: Any): UiText = Res(id, args.toList())
    }
}

fun UiText.resolve(resources: Resources): String = when (this) {
    is UiText.Raw -> value
    is UiText.Joined -> parts.joinToString(separator) { it.resolve(resources) }
    is UiText.Res -> if (args.isEmpty()) {
        resources.getString(id)
    } else {
        resources.getString(id, *args.map { it.resolveArgument(resources) }.toTypedArray())
    }
}

@Composable
fun UiText.resolve(): String = resolve(LocalResources.current)

@Composable
fun UiText?.resolveOrNull(): String? = this?.resolve()

private fun Any.resolveArgument(resources: Resources): Any =
    if (this is UiText) resolve(resources) else this
