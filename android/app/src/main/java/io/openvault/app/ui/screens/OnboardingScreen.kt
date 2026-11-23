package io.openvault.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.openvault.app.ui.components.OpenVaultBrandCrest
import io.openvault.app.ui.theme.LocalFoldableLayout
import io.openvault.app.ui.theme.LocalOpenVaultColors
import io.openvault.app.ui.theme.LocalStrings

/**
 * 5-Slide First-Launch Welcome / Onboarding Carousel (matching Images 21–25):
 * 1. Welcome to OpenVault
 * 2. Simple sign-in
 * 3. Access from anywhere
 * 4. More than just passwords
 * 5. Secure by design
 *
 * Also replayable from Settings -> About -> Welcome Tour with previewOnly = true.
 */
@Composable
fun OnboardingScreen(
    previewOnly: Boolean = false,
    onCreateAccount: () -> Unit = {},
    onSignIn: () -> Unit = {},
    onClosePreview: () -> Unit = {},
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val fold = LocalFoldableLayout.current
    val pagerState = rememberPagerState(pageCount = { 5 })

    val bgColor = if (c.isDark) Color(0xFF17181A) else c.background
    val dockColor = if (c.isDark) Color(0xFF1E2023) else c.surface

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding(),
    ) {
        if (previewOnly) {
            IconButton(
                onClick = onClosePreview,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            ) {
                Icon(Icons.Rounded.Close, contentDescription = s.close, tint = c.textPrimary)
            }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 460.dp)
                            .fillMaxWidth()
                            .padding(horizontal = if (fold.isNarrowCover) 22.dp else 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        when (page) {
                            0 -> SlideWelcomeBrand(isVi = s.isVi)
                            1 -> SlideFeaturePage(
                                title = if (s.isVi) "Đăng nhập cực nhanh" else "Simple sign-in",
                                subtitle = if (s.isVi) {
                                    "Tạo mật khẩu mạnh tức thì và đăng nhập chỉ với một chạm."
                                } else {
                                    "Quickly create strong passwords and log in with a tap."
                                },
                            ) { SimpleSignInArt(Modifier.size(if (fold.isNarrowCover) 210.dp else 250.dp)) }

                            2 -> SlideFeaturePage(
                                title = if (s.isVi) "Truy cập mọi nơi" else "Access from anywhere",
                                subtitle = if (s.isVi) {
                                    "Tự động đồng bộ mọi tài khoản trên điện thoại, máy tính bảng và trình duyệt."
                                } else {
                                    "Keep everything at your fingertips on all your favourite devices."
                                },
                            ) { AccessAnywhereArt(Modifier.size(if (fold.isNarrowCover) 210.dp else 250.dp)) }

                            3 -> SlideFeaturePage(
                                title = if (s.isVi) "Không chỉ là mật khẩu" else "More than just passwords",
                                subtitle = if (s.isVi) {
                                    "Lưu trữ an toàn thẻ tín dụng, danh tính, mã 2FA, passkey và ghi chú bảo mật."
                                } else {
                                    "Safely store credit cards, identities, passkeys, documents, and more."
                                },
                            ) { MoreThanPasswordsArt(Modifier.size(if (fold.isNarrowCover) 210.dp else 250.dp)) }

                            4 -> SlideFeaturePage(
                                title = if (s.isVi) "Bảo mật ngay từ thiết kế" else "Secure by design",
                                subtitle = if (s.isVi) {
                                    "Mã hóa Zero-Knowledge đầu cuối AES-256 & Argon2id giữ bí mật luôn an toàn."
                                } else {
                                    "Industry-leading Zero-Knowledge encryption keeps your secrets safe."
                                },
                            ) { SecureByDesignArt(Modifier.size(if (fold.isNarrowCover) 210.dp else 250.dp)) }
                        }
                    }
                }
            }

            // Pager Indicator Dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 24.dp),
            ) {
                repeat(5) { idx ->
                    val active = pagerState.currentPage == idx
                    Box(
                        modifier = Modifier
                            .size(if (active) 9.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) c.textPrimary
                                else c.textMuted.copy(alpha = 0.4f),
                            ),
                    )
                }
            }

            // Curved Bottom Action Dock (matching Images 21–25)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(dockColor)
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 460.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (previewOnly) {
                        Button(
                            onClick = onClosePreview,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = c.accentButtonBg,
                                contentColor = Color.White,
                            ),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Text(
                                text = s.done,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    } else {
                        Button(
                            onClick = onCreateAccount,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = c.accentButtonBg,
                                contentColor = Color.White,
                            ),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Text(
                                text = if (s.isVi) "Tạo tài khoản mới" else "Create New Account",
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        OutlinedButton(
                            onClick = onSignIn,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (c.isDark) Color(0xFF42464F) else c.cardBorder),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Text(
                                text = if (s.isVi) "Đăng nhập" else "Sign In",
                                color = c.textPrimary,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SlideWelcomeBrand(isVi: Boolean) {
    val c = LocalOpenVaultColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = if (isVi) "Chào mừng đến với" else "Welcome to",
            color = c.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OpenVaultBrandCrest(size = 44.dp)
            Text(
                text = "OpenVault",
                color = c.textPrimary,
                fontSize = 33.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.8).sp,
            )
        }
    }
}

@Composable
private fun SlideFeaturePage(
    title: String,
    subtitle: String,
    artwork: @Composable () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        artwork()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                color = c.textPrimary,
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                textAlign = TextAlign.Center,
                lineHeight = 32.sp,
            )
            Text(
                text = subtitle,
                color = c.textSecondary,
                fontSize = 14.5.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── SLIDE 2: Simple Sign-In (Autofill bars + Pen + Teal Swirl) ──────────────
@Composable
private fun SimpleSignInArt(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        // Teal orbital ring behind bars
        rotate(-14f, pivot = Offset(cx, cy)) {
            drawOval(
                color = Color(0xFF4CA9B0).copy(alpha = 0.72f),
                topLeft = Offset(w * 0.08f, h * 0.22f),
                size = Size(w * 0.84f, h * 0.56f),
                style = Stroke(width = w * 0.022f),
            )
            drawOval(
                color = Color(0xFF2DD4BF).copy(alpha = 0.35f),
                topLeft = Offset(w * 0.11f, h * 0.26f),
                size = Size(w * 0.78f, h * 0.50f),
                style = Stroke(width = w * 0.01f),
            )
        }

        // Amber & coral & magenta confetti flakes
        drawArc(
            color = Color(0xFFF5B041),
            startAngle = 200f,
            sweepAngle = 70f,
            useCenter = true,
            topLeft = Offset(w * 0.05f, h * 0.10f),
            size = Size(w * 0.22f, h * 0.16f),
        )
        drawRoundRect(
            color = Color(0xFFEC7063),
            topLeft = Offset(w * 0.28f, h * 0.21f),
            size = Size(w * 0.09f, h * 0.06f),
            cornerRadius = CornerRadius(4f, 4f),
        )
        drawArc(
            color = Color(0xFFD946EF),
            startAngle = 15f,
            sweepAngle = 145f,
            useCenter = false,
            topLeft = Offset(w * 0.68f, h * 0.58f),
            size = Size(w * 0.16f, h * 0.16f),
            style = Stroke(width = w * 0.045f, cap = StrokeCap.Round),
        )

        // Two tilted Cobalt credential pill bars
        rotate(-11f, pivot = Offset(cx, cy)) {
            // Top bar (username)
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(Color(0xFF6B8CE6), Color(0xFF819CE8))),
                topLeft = Offset(w * 0.06f, h * 0.34f),
                size = Size(w * 0.80f, h * 0.16f),
                cornerRadius = CornerRadius(h * 0.08f, h * 0.08f),
            )
            // Person glyph on left of top bar
            drawCircle(
                color = Color(0xFF3355B8),
                radius = w * 0.032f,
                center = Offset(w * 0.145f, h * 0.398f),
            )
            drawArc(
                color = Color(0xFF3355B8),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(w * 0.105f, h * 0.43f),
                size = Size(w * 0.08f, h * 0.07f),
            )

            // Bottom bar (password)
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(Color(0xFF6B8CE6), Color(0xFF819CE8))),
                topLeft = Offset(w * 0.08f, h * 0.54f),
                size = Size(w * 0.80f, h * 0.16f),
                cornerRadius = CornerRadius(h * 0.08f, h * 0.08f),
            )
            // Padlock on left of bottom bar
            drawRoundRect(
                color = Color(0xFF3355B8),
                topLeft = Offset(w * 0.13f, h * 0.605f),
                size = Size(w * 0.07f, h * 0.06f),
                cornerRadius = CornerRadius(4f, 4f),
            )
            drawArc(
                color = Color(0xFF3355B8),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(w * 0.142f, h * 0.575f),
                size = Size(w * 0.046f, h * 0.05f),
                style = Stroke(width = w * 0.014f),
            )
            // White OpenVault emblem pill on right of bottom bar
            drawCircle(
                color = Color(0xFFFDF8EE),
                radius = w * 0.052f,
                center = Offset(w * 0.80f, h * 0.62f),
            )
            drawRoundRect(
                color = Color(0xFF1E3A8A),
                topLeft = Offset(w * 0.788f, h * 0.592f),
                size = Size(w * 0.024f, h * 0.056f),
                cornerRadius = CornerRadius(4f, 4f),
            )
        }

        // Diagonal Cobalt Fountain/Ballpoint Pen hovering over the bars
        rotate(37f, pivot = Offset(cx + w * 0.05f, cy - h * 0.04f)) {
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF2E5FD9), Color(0xFF1D4ED8))),
                topLeft = Offset(cx + w * 0.02f, h * 0.14f),
                size = Size(w * 0.11f, h * 0.44f),
                cornerRadius = CornerRadius(14f, 14f),
            )
            val nibPath = Path().apply {
                moveTo(cx + w * 0.02f, h * 0.57f)
                lineTo(cx + w * 0.13f, h * 0.57f)
                lineTo(cx + w * 0.075f, h * 0.67f)
                close()
            }
            drawPath(nibPath, Color(0xFF2563EB))
            drawCircle(
                color = Color.White,
                radius = w * 0.015f,
                center = Offset(cx + w * 0.075f, h * 0.665f),
            )
        }
    }
}

// ── SLIDE 3: Access from Anywhere (Laptop + Phone + Tablet + Watch) ─────────
@Composable
private fun AccessAnywhereArt(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Subtle dark orbital ribbon
        drawArc(
            color = Color(0xFF475569).copy(alpha = 0.45f),
            startAngle = -35f,
            sweepAngle = 250f,
            useCenter = false,
            topLeft = Offset(w * 0.10f, h * 0.24f),
            size = Size(w * 0.80f, h * 0.58f),
            style = Stroke(width = w * 0.03f, cap = StrokeCap.Round),
        )

        // Teal Laptop screen + keyboard base
        val screenPath = Path().apply {
            moveTo(w * 0.44f, h * 0.34f)
            lineTo(w * 0.78f, h * 0.20f)
            lineTo(w * 0.72f, h * 0.54f)
            lineTo(w * 0.38f, h * 0.58f)
            close()
        }
        drawPath(screenPath, Brush.linearGradient(listOf(Color(0xFF38BDF8), Color(0xFF0284C7))))

        val basePath = Path().apply {
            moveTo(w * 0.10f, h * 0.69f)
            lineTo(w * 0.38f, h * 0.58f)
            lineTo(w * 0.72f, h * 0.54f)
            lineTo(w * 0.40f, h * 0.70f)
            close()
        }
        drawPath(basePath, Color(0xFF22A6C9))

        // OpenVault keyhole badge on laptop screen
        drawCircle(
            color = Color(0xFFF8FAFC),
            radius = w * 0.072f,
            center = Offset(w * 0.57f, h * 0.41f),
        )
        drawRoundRect(
            color = Color(0xFF0369A1),
            topLeft = Offset(w * 0.556f, h * 0.372f),
            size = Size(w * 0.028f, h * 0.076f),
            cornerRadius = CornerRadius(5f, 5f),
        )

        // Smartwatch at top
        drawCircle(
            color = Color(0xFF0EA5E9),
            radius = w * 0.088f,
            center = Offset(w * 0.36f, h * 0.13f),
            style = Stroke(width = w * 0.036f),
        )
        rotate(34f, pivot = Offset(w * 0.33f, h * 0.10f)) {
            drawRoundRect(
                color = Color(0xFF3B5BDB),
                topLeft = Offset(w * 0.27f, h * 0.05f),
                size = Size(w * 0.12f, h * 0.10f),
                cornerRadius = CornerRadius(10f, 10f),
            )
        }

        // Magenta Phone with fingerprint badge
        rotate(26f, pivot = Offset(w * 0.40f, h * 0.34f)) {
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFFD96BC6), Color(0xFFA8429B))),
                topLeft = Offset(w * 0.31f, h * 0.21f),
                size = Size(w * 0.19f, h * 0.28f),
                cornerRadius = CornerRadius(14f, 14f),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.26f),
                radius = w * 0.056f,
                center = Offset(w * 0.405f, h * 0.33f),
            )
            drawCircle(
                color = Color.White,
                radius = w * 0.034f,
                center = Offset(w * 0.405f, h * 0.33f),
                style = Stroke(width = w * 0.012f),
            )
        }

        // Warm Golden Tablet with Face ID icon below
        rotate(-30f, pivot = Offset(w * 0.50f, h * 0.72f)) {
            drawRoundRect(
                brush = Brush.linearGradient(listOf(Color(0xFFFBD38D), Color(0xFFF59E0B))),
                topLeft = Offset(w * 0.34f, h * 0.54f),
                size = Size(w * 0.32f, h * 0.36f),
                cornerRadius = CornerRadius(16f, 16f),
            )
            // Face ID corners & smile
            val faceColor = Color(0xFFC2410C)
            drawRoundRect(
                color = faceColor,
                topLeft = Offset(w * 0.44f, h * 0.66f),
                size = Size(w * 0.12f, h * 0.12f),
                cornerRadius = CornerRadius(8f, 8f),
                style = Stroke(width = w * 0.013f),
            )
            drawArc(
                color = faceColor,
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(w * 0.468f, h * 0.70f),
                size = Size(w * 0.064f, h * 0.05f),
                style = Stroke(width = w * 0.013f, cap = StrokeCap.Round),
            )
        }
    }
}

// ── SLIDE 4: More Than Just Passwords (Vault Chest + Floating Cards/Key) ───
@Composable
private fun MoreThanPasswordsArt(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Floating yellow document at top-left
        rotate(10f, pivot = Offset(w * 0.35f, h * 0.16f)) {
            drawRoundRect(
                color = Color(0xFFF6C667),
                topLeft = Offset(w * 0.28f, h * 0.06f),
                size = Size(w * 0.13f, h * 0.18f),
                cornerRadius = CornerRadius(6f, 6f),
            )
        }

        // Swoosh ribbon behind credit card
        val swoosh = Path().apply {
            moveTo(w * 0.28f, h * 0.42f)
            quadraticTo(w * 0.56f, h * 0.52f, w * 0.74f, h * 0.18f)
            quadraticTo(w * 0.56f, h * 0.34f, w * 0.28f, h * 0.42f)
            close()
        }
        drawPath(swoosh, Color(0xFF7B9CE8))

        // Blue Credit Card floating in center
        rotate(-40f, pivot = Offset(w * 0.50f, h * 0.24f)) {
            drawRoundRect(
                brush = Brush.linearGradient(listOf(Color(0xFF2563EB), Color(0xFF1E40AF))),
                topLeft = Offset(w * 0.39f, h * 0.17f),
                size = Size(w * 0.20f, h * 0.13f),
                cornerRadius = CornerRadius(10f, 10f),
            )
            drawRect(
                color = Color(0xFF172554),
                topLeft = Offset(w * 0.39f, h * 0.20f),
                size = Size(w * 0.20f, h * 0.03f),
            )
        }

        // Golden Key floating on left
        rotate(42f, pivot = Offset(w * 0.26f, h * 0.48f)) {
            drawCircle(color = Color(0xFFFCD34D), radius = w * 0.045f, center = Offset(w * 0.23f, h * 0.46f))
            drawCircle(color = Color(0xFF17181A), radius = w * 0.014f, center = Offset(w * 0.225f, h * 0.455f))
            drawRoundRect(
                color = Color(0xFFF59E0B),
                topLeft = Offset(w * 0.26f, h * 0.448f),
                size = Size(w * 0.11f, h * 0.028f),
                cornerRadius = CornerRadius(4f, 4f),
            )
        }

        // Open Vault Chest interior (teal)
        val openLid = Path().apply {
            moveTo(w * 0.19f, h * 0.65f)
            lineTo(w * 0.29f, h * 0.53f)
            lineTo(w * 0.71f, h * 0.53f)
            lineTo(w * 0.81f, h * 0.65f)
            close()
        }
        drawPath(openLid, Color(0xFF38B2AC))

        // White document & Purple passport sticking out of vault box
        rotate(12f, pivot = Offset(w * 0.42f, h * 0.58f)) {
            drawRoundRect(
                color = Color(0xFFF3EFE6),
                topLeft = Offset(w * 0.36f, h * 0.47f),
                size = Size(w * 0.24f, h * 0.20f),
                cornerRadius = CornerRadius(10f, 10f),
            )
            drawRoundRect(
                color = Color(0xFF9333EA),
                topLeft = Offset(w * 0.29f, h * 0.49f),
                size = Size(w * 0.21f, h * 0.22f),
                cornerRadius = CornerRadius(8f, 8f),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.75f),
                radius = w * 0.05f,
                center = Offset(w * 0.395f, h * 0.60f),
                style = Stroke(width = w * 0.008f),
            )
        }

        // Front Cobalt Vault Box
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFF2A5BD7), Color(0xFF1D4ED8))),
            topLeft = Offset(w * 0.17f, h * 0.64f),
            size = Size(w * 0.66f, h * 0.28f),
            cornerRadius = CornerRadius(14f, 14f),
        )
        // Subtle indicator dots on top-left of chest
        drawCircle(color = Color(0xFF0F172A), radius = w * 0.013f, center = Offset(w * 0.22f, h * 0.675f))
        drawCircle(color = Color(0xFF0F172A), radius = w * 0.013f, center = Offset(w * 0.26f, h * 0.675f))

        // Centered circular keyhole emblem on chest
        drawCircle(
            color = Color(0xFFCBD5E1),
            radius = w * 0.062f,
            center = Offset(w * 0.50f, h * 0.78f),
        )
        drawRoundRect(
            color = Color(0xFF1E3A8A),
            topLeft = Offset(w * 0.486f, h * 0.746f),
            size = Size(w * 0.028f, h * 0.068f),
            cornerRadius = CornerRadius(5f, 5f),
        )
    }
}

// ── SLIDE 5: Secure by Design (Golden Shield + Navy Keyhole + Orbitals) ────
@Composable
private fun SecureByDesignArt(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        // Deep navy swoosh behind shield
        rotate(32f, pivot = Offset(cx, cy)) {
            drawOval(
                color = Color(0xFF1E3A5F).copy(alpha = 0.75f),
                topLeft = Offset(w * 0.05f, h * 0.30f),
                size = Size(w * 0.90f, h * 0.38f),
                style = Stroke(width = w * 0.045f),
            )
        }

        // Golden Shield body
        val shield = Path().apply {
            moveTo(cx, h * 0.14f)
            lineTo(w * 0.79f, h * 0.25f)
            cubicTo(w * 0.79f, h * 0.56f, w * 0.66f, h * 0.76f, cx, h * 0.87f)
            cubicTo(w * 0.34f, h * 0.76f, w * 0.21f, h * 0.56f, w * 0.21f, h * 0.25f)
            close()
        }
        drawPath(
            shield,
            brush = Brush.verticalGradient(listOf(Color(0xFFFBBF24), Color(0xFFD97706))),
        )

        // Centered Deep-Navy OpenVault Keyhole in Shield
        val kw = w * 0.095f
        val kh = h * 0.27f
        drawRoundRect(
            color = Color(0xFF172554),
            topLeft = Offset(cx - kw / 2f, cy - kh * 0.52f),
            size = Size(kw, kh),
            cornerRadius = CornerRadius(8f, 8f),
        )

        // Golden orbital ring sweeping in front
        rotate(-26f, pivot = Offset(cx, cy)) {
            drawOval(
                color = Color(0xFFFDE68A).copy(alpha = 0.85f),
                topLeft = Offset(w * 0.03f, h * 0.34f),
                size = Size(w * 0.94f, h * 0.34f),
                style = Stroke(width = w * 0.018f),
            )
        }

        // Colorful ribbons (cyan, coral, orchid)
        drawArc(
            color = Color(0xFF38BDF8),
            startAngle = 210f,
            sweepAngle = 70f,
            useCenter = true,
            topLeft = Offset(w * 0.72f, h * 0.12f),
            size = Size(w * 0.20f, h * 0.18f),
        )
        drawArc(
            color = Color(0xFF06B6D4),
            startAngle = 25f,
            sweepAngle = 95f,
            useCenter = true,
            topLeft = Offset(w * 0.16f, h * 0.68f),
            size = Size(w * 0.20f, h * 0.18f),
        )
        drawRoundRect(
            color = Color(0xFFEC7063),
            topLeft = Offset(w * 0.14f, h * 0.16f),
            size = Size(w * 0.055f, h * 0.11f),
            cornerRadius = CornerRadius(6f, 6f),
        )
        drawRoundRect(
            color = Color(0xFFD946EF),
            topLeft = Offset(w * 0.72f, h * 0.60f),
            size = Size(w * 0.16f, h * 0.11f),
            cornerRadius = CornerRadius(12f, 12f),
        )
    }
}
