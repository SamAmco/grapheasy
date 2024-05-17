/*
 *  This file is part of Track & Graph
 *
 *  Track & Graph is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Track & Graph is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Track & Graph.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.samco.trackandgraph.ui.compose.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import com.samco.trackandgraph.ui.compose.theming.tngColors

/**
 * Compose + Material 2 doesn't let us do what we want for styling
 * So we implement our own TextField with a customized Box
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FormTextInput(
    modifier: Modifier = Modifier,
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    focusManager: FocusManager? = null,
    focusRequester: FocusRequester? = null,
    keyboardController: SoftwareKeyboardController? = null,
    textAlign: TextAlign = TextAlign.Start,
    singleLine: Boolean = true,
    isNumeric: Boolean = false
) {
    val keyboardActions =
        if (focusManager != null) KeyboardActions(onNext = {
            focusManager.moveFocus(FocusDirection.Down)
        }) else KeyboardActions.Default

    val keyboardType = if (isNumeric) KeyboardType.Decimal else KeyboardType.Text

    val keyboardOptions =
        if (singleLine) KeyboardOptions(
            imeAction = ImeAction.Next,
            capitalization = KeyboardCapitalization.Sentences,
            keyboardType = keyboardType
        ) else KeyboardOptions.Default.copy(
            capitalization = KeyboardCapitalization.Sentences,
            keyboardType = keyboardType
        )

    var isFocused by remember { mutableStateOf(false) }

    BasicTextField(
        textFieldValue,
        onValueChange,
        keyboardActions = keyboardActions,
        keyboardOptions = keyboardOptions,
        textStyle = TextStyle(
            color = MaterialTheme.tngColors.inputTextColor,
            textAlign = textAlign,
        ),
        // This is a function because we tell to BasicTextField how to decorate the field
        decorationBox = { textField ->
            FormInputDecoration(isFocused = isFocused) { textField() }
        },
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged {
                isFocused = it.hasFocus

                if (isFocused) {
                    keyboardController?.show()
                }
            }
            .let {
                if (focusRequester != null) it.focusRequester(focusRequester)
                else it
            })
}
