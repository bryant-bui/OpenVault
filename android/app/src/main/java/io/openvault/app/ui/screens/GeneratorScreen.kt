package io.openvault.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.openvault.app.core.crypto.PassphraseOptions
import io.openvault.app.core.crypto.PasswordGenerator
import io.openvault.app.core.crypto.PasswordOptions
import io.openvault.app.ui.components.LiquidGlassCard
import io.openvault.app.ui.components.LiquidGlassChip
import io.openvault.app.ui.components.SyntaxColoredPasswordText
import io.openvault.app.ui.theme.LocalOpenVaultColors
import kotlin.math.roundToInt

@Composable
fun GeneratorScreen(
    onCopy: (String) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    var isPassphrase by remember { mutableStateOf(false) }

    // Random password options
    var length by remember { mutableIntStateOf(20) }
    var useLower by remember { mutableStateOf(true) }
    var useUpper by remember { mutableStateOf(true) }
    var useDigits by remember { mutableStateOf(true) }
    var useSymbols by remember { mutableStateOf(true) }
    var avoidAmbiguous by remember { mutableStateOf(false) }

    // Passphrase options
    var wordCount by remember { mutableIntStateOf(4) }
    var separator by remember { mutableStateOf("-") }
    var capitalize by remember { mutableStateOf(true) }
    var includeNumber by remember { mutableStateOf(true) }

    val history = remember { mutableStateListOf<String>() }

    fun nextPassword(): String {
        val generated = if (isPassphrase) {
            PasswordGenerator.generatePassphrase(
                PassphraseOptions(
                    wordCount = wordCount,
                    separator = separator,
                    capitalize = capitalize,
                    includeNumber = includeNumber,
                ),
            )
        } else {
            val safeLower = useLower || (!useUpper && !useDigits && !useSymbols)
            PasswordGenerator.generatePassword(
                PasswordOptions(
                    length = length,
                    lowercase = safeLower,
                    uppercase = useUpper,
                    digits = useDigits,
                    symbols = useSymbols,
                    avoidAmbiguous = avoidAmbiguous,
                ),
            )
        }
        return generated
    }

    var currentPassword by remember { mutableStateOf(nextPassword()) }

    fun regenerate() {
        val old = currentPassword
        if (old.isNotBlank() && history.firstOrNull() != old) {
            history.add(0, old)
            if (history.size > 10) history.removeAt(history.lastIndex)
        }
        currentPassword = nextPassword()
    }

    val strength = remember(currentPassword) { PasswordGenerator.estimateStrength(currentPassword) }
    val strengthColor = when {
        strength.level.score >= 3 -> c.success
        strength.level.score == 2 -> c.warning
        else -> c.danger
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 128.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Trình tạo mật khẩu",
            color = c.textPrimary,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
        )

        // Segmented Liquid Glass Mode Switcher
        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 18.dp,
            padding = PaddingValues(4.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf(false to "Ngẫu nhiên", true to "Cụm từ dễ nhớ").forEach { (phrase, label) ->
                    val active = isPassphrase == phrase
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (active) c.accent else Color.Transparent)
                            .clickable {
                                isPassphrase = phrase
                                regenerate()
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            color = if (active) Color.White else c.textSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        // Hero Output Card
        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 26.dp,
            elevated = true,
            padding = PaddingValues(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SyntaxColoredPasswordText(
                    password = currentPassword,
                    masked = false,
                    fontSize = 20.sp,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Strength bar + entropy
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Độ mạnh: ${strength.level.labelVi}",
                            color = strengthColor,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${strength.entropyBits} bits entropy",
                            color = c.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        for (seg in 0..3) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(5.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (seg <= strength.level.score) strengthColor else c.glassSurface,
                                    ),
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onCopy(currentPassword) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = Color.White),
                        modifier = Modifier.weight(1f).height(46.dp),
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Sao chép", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = { regenerate() },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = c.accentSoft,
                            contentColor = c.accent,
                        ),
                        modifier = Modifier.height(46.dp),
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Tạo mới")
                    }
                }
            }
        }

        // Controls Card
        LiquidGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!isPassphrase) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Độ dài mật khẩu", color = c.textPrimary, fontWeight = FontWeight.Medium)
                        Text("$length ký tự", color = c.accent, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = length.toFloat(),
                        onValueChange = {
                            length = it.roundToInt()
                            currentPassword = nextPassword()
                        },
                        valueRange = 8f..64f,
                        colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent),
                    )
                    GeneratorToggle("Chữ thường (a-z)", useLower) { useLower = it; currentPassword = nextPassword() }
                    GeneratorToggle("Chữ hoa (A-Z)", useUpper) { useUpper = it; currentPassword = nextPassword() }
                    GeneratorToggle("Chữ số (0-9)", useDigits) { useDigits = it; currentPassword = nextPassword() }
                    GeneratorToggle("Ký tự đặc biệt (!@#…)", useSymbols) { useSymbols = it; currentPassword = nextPassword() }
                    GeneratorToggle("Tránh ký tự dễ nhầm (0/O, l/1)", avoidAmbiguous) {
                        avoidAmbiguous = it; currentPassword = nextPassword()
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Số lượng từ", color = c.textPrimary, fontWeight = FontWeight.Medium)
                        Text("$wordCount từ", color = c.accent, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = wordCount.toFloat(),
                        onValueChange = {
                            wordCount = it.roundToInt()
                            currentPassword = nextPassword()
                        },
                        valueRange = 3f..8f,
                        steps = 4,
                        colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Phân tách:", color = c.textSecondary, fontSize = 13.5.sp)
                        listOf("-" to "Gạch -", "." to "Chấm .", "_" to "Gạch _", " " to "Trống").forEach { (sep, lbl) ->
                            LiquidGlassChip(
                                label = lbl,
                                selected = separator == sep,
                                onClick = { separator = sep; currentPassword = nextPassword() },
                            )
                        }
                    }
                    GeneratorToggle("Viết hoa chữ cái đầu", capitalize) { capitalize = it; currentPassword = nextPassword() }
                    GeneratorToggle("Thêm số ngẫu nhiên", includeNumber) { includeNumber = it; currentPassword = nextPassword() }
                }
            }
        }

        // Session History
        if (history.isNotEmpty()) {
            LiquidGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Lịch sử vừa tạo (chỉ trong RAM)",
                        color = c.textSecondary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    history.forEach { old ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCopy(old) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SyntaxColoredPasswordText(
                                password = old,
                                masked = false,
                                fontSize = 13.5.sp,
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { onCopy(old) }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = "Chép", tint = c.accent, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneratorToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalOpenVaultColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = c.textPrimary, fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
