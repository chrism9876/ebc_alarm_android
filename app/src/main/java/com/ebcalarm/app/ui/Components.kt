package com.ebcalarm.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Small grey label above a field, like the web page's labels. */
@Composable
fun FieldLabel(text: String) {
    Text(text, fontSize = 12.sp, color = LocalAppColors.current.muted, modifier = Modifier.padding(bottom = 3.dp))
}

/** A select box: shows the chosen option, opens a menu on tap. */
@Composable
fun <T> Select(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val c = LocalAppColors.current
    Column(modifier) {
        FieldLabel(label)
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, c.line, RoundedCornerShape(6.dp))
                    .clickable { open = true }
                    .padding(horizontal = 10.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(options.firstOrNull { it.first == selected }?.second ?: selected.toString(),
                     modifier = Modifier.weight(1f), maxLines = 1, fontSize = 15.sp)
                Text("▾", color = c.muted)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (value, text) ->
                    DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onSelect(value) })
                }
            }
        }
    }
}

/** Whole-number input that allows clearing the field while typing. */
@Composable
fun NumberField(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit, modifier: Modifier = Modifier, placeholder: String = "") {
    var text by remember { mutableStateOf(value.toString()) }
    LaunchedEffect(value) { if (text.toIntOrNull() != value) text = value.toString() }
    Column(modifier) {
        FieldLabel(label)
        OutlinedTextField(
            value = text,
            onValueChange = { t ->
                val digits = t.filter { it.isDigit() }.take(5)
                text = digits
                digits.toIntOrNull()?.let { onChange(it.coerceIn(range)) } ?: onChange(range.first)
            },
            singleLine = true,
            placeholder = if (placeholder.isNotEmpty()) ({ Text(placeholder) }) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun CheckLabel(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onChange(!checked) }.padding(end = 12.dp)) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(text, fontSize = 14.sp)
    }
}

@Composable
fun AppCard(modifier: Modifier = Modifier, alpha: Float = 1f, content: @Composable () -> Unit) {
    val c = LocalAppColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = c.card,
        border = androidx.compose.foundation.BorderStroke(1.dp, c.line),
    ) {
        Box(Modifier.padding(14.dp).alpha(alpha)) { content() }
    }
}

/** Time button that opens a 24 h picker plus seconds, matching the web page's hh:mm:ss input. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(hour: Int, min: Int, sec: Int, onChange: (Int, Int, Int) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val c = LocalAppColors.current
    Column(modifier) {
        FieldLabel("Time")
        Text(
            "%02d:%02d:%02d".format(hour, min, sec),
            fontSize = 15.sp,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, c.line, RoundedCornerShape(6.dp))
                .clickable { open = true }
                .padding(horizontal = 10.dp, vertical = 11.dp),
        )
    }
    if (open) {
        val state = rememberTimePickerState(initialHour = hour, initialMinute = min, is24Hour = true)
        var seconds by remember { mutableIntStateOf(sec) }
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { open = false; onChange(state.hour, state.minute, seconds) }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state = state)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Text("Seconds", color = c.muted)
                        Spacer(Modifier.width(12.dp))
                        TextButton(onClick = { seconds = (seconds + 59) % 60 }) { Text("−", fontSize = 20.sp) }
                        Text("%02d".format(seconds), fontSize = 20.sp)
                        TextButton(onClick = { seconds = (seconds + 1) % 60 }) { Text("+", fontSize = 20.sp) }
                        TextButton(onClick = { seconds = 0 }) { Text("0") }
                    }
                }
            },
        )
    }
}
