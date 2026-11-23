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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import io.openvault.app.ui.components.OnePassCard
import io.openvault.app.ui.components.OnePassChip
import io.openvault.app.ui.components.SyntaxColoredPasswordText
import io.openvault.app.ui.theme.LocalFoldableLayout
import io.openvault.app.ui.theme.LocalOpenVaultColors
import io.openvault.app.ui.theme.LocalStrings
import kotlin.math.roundToInt

@Composable
fun GeneratorScreen(
    onBack: () -> Unit,
    onCopy: (String) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val fold = LocalFoldableLayout.current
    var isPassphrase by remember { mutableStateOf(false) }

    var length by remember { mutableIntStateOf(20) }
    var useLower by remember { mutableStateOf(true) }
    var useUpper by remember { mutableStateOf(true) }
    var useDigits by remember { mutableStateOf(true) }
    var useSymbols by remember { mutableStateOf(true) }
    var avoidAmbiguous by remember { mutableStateOf(false) }

    var wordCount by remember { mutableIntStateOf(4) }
    var separator by remember { mutableStateOf("-") }
    var capitalize by remember { mutableStateOf(true) }
    var includeNumber by remember { mutableStateOf(true) }

    val history = remember { mutableStateListOf<String>() }

    fun nextPassword(): String = if (isPassphrase) {
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
            .background(c.sheetBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.Close, contentDescription = "Đóng", tint = c.textPrimary)
                }
                Text(
                    text = s.passwordGenerator,
                    color = c.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            IconButton(onClick = { regenerate() }) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Tạo mới", tint = c.accent)
            }
        }
        HorizontalDivider(color = c.divider, thickness = 0.8.dp)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 620.dp)
                    .fillMaxWidth()
                    .padding(horizontal = fold.horizontalPadding, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Segmented Switcher
                OnePassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 12.dp,
                    backgroundColor = c.surface,
                    padding = PaddingValues(4.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            false to if (s.isVi) "Ngẫu nhiên" else "Random Password",
                            true to if (s.isVi) "Cụm từ dễ nhớ" else "Memorable Passphrase",
                        ).forEach { (phrase, label) ->
                            val active = isPassphrase == phrase
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (active) c.accentButtonBg else Color.Transparent)
                                    .clickable {
                                        isPassphrase = phrase
                                        regenerate()
                                    }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    color = if (active) Color.White else c.textSecondary,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }

                // Hero Syntax-Colored Output Card
                OnePassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = c.surface,
                    borderColor = c.cardBorder,
                    padding = PaddingValues(18.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SyntaxColoredPasswordText(
                            password = currentPassword,
                            masked = false,
                            fontSize = if (fold.isNarrowCover) 18.sp else 20.sp,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "${strength.level.label(s.isVi)} · ${strength.entropyBits} bits",
                                    color = strengthColor,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "${currentPassword.length} ${if (s.isVi) "ký tự" else "chars"}",
                                    color = c.textSecondary,
                                    fontSize = 12.sp,
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                for (seg in 0..3) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(5.dp)
                                            .clip(CircleShape)
                                            .background(if (seg <= strength.level.score) strengthColor else c.surfaceElevated),
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { onCopy(currentPassword) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = c.accentButtonBg, contentColor = Color.White),
                                modifier = Modifier.weight(1f).height(44.dp),
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(s.copy, fontWeight = FontWeight.SemiBold)
                            }
                            Button(
                                onClick = { regenerate() },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = c.surfaceElevated, contentColor = c.textPrimary),
                                modifier = Modifier.height(44.dp),
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Tạo mới")
                            }
                        }
                    }
                }

                // Options Card
                OnePassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = c.surface,
                    padding = PaddingValues(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!isPassphrase) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Độ dài", color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
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
                            HorizontalDivider(color = c.divider, thickness = 0.7.dp)
                            GenToggleRow("Chữ thường (a-z)", useLower) { useLower = it; currentPassword = nextPassword() }
                            HorizontalDivider(color = c.divider, thickness = 0.7.dp)
                            GenToggleRow("Chữ hoa (A-Z)", useUpper) { useUpper = it; currentPassword = nextPassword() }
                            HorizontalDivider(color = c.divider, thickness = 0.7.dp)
                            GenToggleRow("Chữ số (0-9)", useDigits) { useDigits = it; currentPassword = nextPassword() }
                            HorizontalDivider(color = c.divider, thickness = 0.7.dp)
                            GenToggleRow("Ký tự đặc biệt (!@#…)", useSymbols) { useSymbols = it; currentPassword = nextPassword() }
                            HorizontalDivider(color = c.divider, thickness = 0.7.dp)
                            GenToggleRow("Tránh ký tự dễ nhầm (0/O, l/1)", avoidAmbiguous) {
                                avoidAmbiguous = it; currentPassword = nextPassword()
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Số lượng từ", color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
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
                                listOf("-" to "-", "." to ".", "_" to "_", " " to "Trống").forEach { (sep, lbl) ->
                                    OnePassChip(label = lbl, selected = separator == sep) {
                                        separator = sep
                                        currentPassword = nextPassword()
                                    }
                                }
                            }
                            HorizontalDivider(color = c.divider, thickness = 0.7.dp)
                            GenToggleRow("Viết hoa chữ cái đầu", capitalize) { capitalize = it; currentPassword = nextPassword() }
                            HorizontalDivider(color = c.divider, thickness = 0.7.dp)
                            GenToggleRow("Thêm số ngẫu nhiên", includeNumber) { includeNumber = it; currentPassword = nextPassword() }
                        }
                    }
                }

                if (history.isNotEmpty()) {
                    OnePassCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = c.surface,
                        padding = PaddingValues(0.dp),
                    ) {
                        Column {
                            Text(
                                text = "Lịch sử gần đây",
                                color = c.textSecondary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
                            )
                            history.forEach { old ->
                                HorizontalDivider(color = c.divider, thickness = 0.7.dp)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onCopy(old) }
                                        .padding(horizontal = 15.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SyntaxColoredPasswordText(
                                        password = old,
                                        masked = false,
                                        fontSize = 13.5.sp,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Chép", tint = c.accent, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun GenToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalOpenVaultColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = c.textPrimary, fontSize = 14.5.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
