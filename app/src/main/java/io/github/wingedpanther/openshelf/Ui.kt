package io.github.wingedpanther.openshelf

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

object C {
    val dark: Boolean get() = Prefs.dark
    private fun pick(light: Long, dark: Long) = Color(if (this.dark) dark else light)
    val Wall get() = pick(0xFFFAFAF8, 0xFF121211)
    val WallDeep get() = pick(0xFFF2F2EE, 0xFF1A1A18)
    val Mat get() = pick(0xFFFFFFFF, 0xFF222220)
    val Ink get() = pick(0xFF1D1D1F, 0xFFF2F2EE)
    val Ink2 get() = pick(0xFF3A3A3C, 0xFFCFCFC9)
    val Stone get() = pick(0xFF6E6E73, 0xFF9C9C96)
    val Stone2 get() = pick(0xFF8E8E93, 0xFF7E7E78)
    val Hair get() = pick(0xFFE3E3DE, 0xFF2E2E2B)
    val Hair2 get() = pick(0xFFD6D6D0, 0xFF3D3D39)
    val Bar get() = pick(0xB8FFFFFF, 0xCC1E1E1C)
    val Selected get() = pick(0xFFF2F2EE, 0xFF34342F)
    val OnAccent get() = pick(0xFFFFFFFF, 0xFF121211)
    val OnInk get() = pick(0xFFFFFFFF, 0xFF121211)
    val ShadowAmbient get() = pick(0x331D1D1F, 0x66000000)
    val ShadowSpot get() = pick(0x551D1D1F, 0x99000000)
    val accent: Color get() = Prefs.accents[Prefs.accent].let { pick(it.second, it.third) }
    val mark: Int get() = if (dark) R.drawable.mark_dark else R.drawable.mark
}

private fun weight(w: Int) = FontVariation.Settings(FontVariation.weight(w))

val Geist = FontFamily(
    Font(R.font.geist, weight = FontWeight.Normal, variationSettings = weight(400)),
    Font(R.font.geist, weight = FontWeight.Medium, variationSettings = weight(500)),
    Font(R.font.geist, weight = FontWeight.SemiBold, variationSettings = weight(600)),
)

@Composable
fun T(
    text: String,
    size: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = C.Ink,
    weight: FontWeight = FontWeight.Normal,
    maxLines: Int = Int.MAX_VALUE,
    tight: Boolean = false,
) {
    BasicText(
        text, modifier,
        style = TextStyle(
            fontFamily = Geist, fontSize = size, color = color, fontWeight = weight,
            letterSpacing = if (tight) (-0.025).em else 0.em, lineHeight = size * 1.3f,
        ),
        maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun T(text: AnnotatedString, size: TextUnit, modifier: Modifier = Modifier, color: Color = C.Ink, maxLines: Int = Int.MAX_VALUE) {
    BasicText(
        text, modifier,
        style = TextStyle(fontFamily = Geist, fontSize = size, color = color, lineHeight = size * 1.3f),
        maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}

private fun icon(vararg paths: String): ImageVector = ImageVector.Builder(
    defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
).apply {
    paths.forEach {
        addPath(
            pathData = PathParser().parsePathString(it).toNodes(),
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
    }
}.build()

object Icons {
    val Download = icon("M12 4v11m0 0l-4.5-4.5M12 15l4.5-4.5M5 20h14")
    val Check = icon("M12 3a9 9 0 1 1 0 18a9 9 0 1 1 0-18z", "M8 12.5l2.7 2.7L16 9.8")
    val Up = icon("M12 3a9 9 0 1 1 0 18a9 9 0 1 1 0-18z", "M12 16.5v-9M8 11l4-4 4 4")
    val Shield = icon("M12 3l7.5 3v5.5c0 4.5-3.2 8-7.5 9.5-4.3-1.5-7.5-5-7.5-9.5V6z", "M8.5 12l2.5 2.5 4.5-5")
    val ShieldAlert = icon("M12 3l7.5 3v5.5c0 4.5-3.2 8-7.5 9.5-4.3-1.5-7.5-5-7.5-9.5V6z", "M12 8v5M12 16.5v0.01")
    val ChevRight = icon("M9 5l7 7-7 7")
    val ChevLeft = icon("M15 5l-7 7 7 7")
    val ChevDown = icon("M5 9l7 7 7-7")
    val Delete = icon("M9 6h11v12H9l-6-6z", "M12.5 9.5l5 5M17.5 9.5l-5 5")
    val Play = icon("M8 5.5v13l10.5-6.5z")
    val Search = icon("M11 4.5a6.5 6.5 0 1 1 0 13a6.5 6.5 0 1 1 0-13z", "M16 16l4.5 4.5")
    val Chip = icon("M8 6h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z", "M9 2v4M15 2v4M9 18v4M15 18v4M2 9h4M2 15h4M18 9h4M18 15h4")
    val Star = icon("M12 3.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8-4.3-4.1 5.9-.9z")
    val Calendar = icon("M5.5 5h13a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z", "M3.5 10h17M8 3v4M16 3v4")
    val Scale = icon("M12 4v16M7 20h10M5 7h14M5 7l-2.5 6a3 3 0 0 0 5 0zM19 7l-2.5 6a3 3 0 0 0 5 0z")
    val Box = icon("M3.5 7.5L12 3l8.5 4.5v9L12 21l-8.5-4.5z", "M3.5 7.5L12 12l8.5-4.5M12 12v9")
    val Trash = icon("M4 7h16M9 7V4h6v3M6.5 7l1 13h9l1-13")
}

@Composable
fun Ico(v: ImageVector, size: Dp, color: Color, modifier: Modifier = Modifier) {
    Image(rememberVectorPainter(v), null, modifier.size(size), colorFilter = ColorFilter.tint(color))
}

@Composable
fun Focusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    requester: FocusRequester? = null,
    onFocus: () -> Unit = {},
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable(interactionSource = source, indication = null, onClick = onClick),
    ) { content(focused) }
}

@Composable
fun Modifier.lift(
    focused: Boolean,
    scale: Float = 1.08f,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(10.dp),
    rest: Dp = 2.dp,
    ring: Dp? = null,
): Modifier {
    val s by animateFloatAsState(if (focused) scale else 1f, tween(260), label = "lift")
    val e by animateDpAsState(if (focused) 16.dp else rest, tween(260), label = "shadow")
    val a by animateFloatAsState(if (focused) 1f else 0f, tween(200), label = "ring")
    val color = C.accent
    return this
        .graphicsLayer { scaleX = s; scaleY = s }
        .then(
            if (ring == null) Modifier else Modifier.drawWithContent {
                drawContent()
                if (a > 0f) {
                    val gap = 3.dp.toPx()
                    val w = 3.dp.toPx()
                    val o = gap + w / 2
                    drawRoundRect(
                        color.copy(alpha = a), topLeft = Offset(-o, -o),
                        size = androidx.compose.ui.geometry.Size(size.width + 2 * o, size.height + 2 * o),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(ring.toPx() + o), style = Stroke(w),
                    )
                }
            },
        )
        .shadow(e, shape, ambientColor = C.ShadowAmbient, spotColor = C.ShadowSpot)
}

@Composable
fun Pill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    solid: Boolean = false,
    icon: ImageVector? = null,
    requester: FocusRequester? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    Focusable(onClick, modifier, requester) { f ->
        val s by animateFloatAsState(if (f) 1.07f else 1f, tween(220), label = "pill")
        val bg = when {
            f -> C.accent
            solid -> C.Ink
            else -> Color.Transparent
        }
        Row(
            Modifier
                .padding(4.dp)
                .graphicsLayer { scaleX = s; scaleY = s }
                .shadow(if (f) 10.dp else 0.dp, CircleShape, spotColor = C.accent)
                .height(38.dp)
                .background(bg, CircleShape)
                .then(if (!solid && !f) Modifier.border(1.dp, C.Hair2, CircleShape) else Modifier)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val fg = if (f) C.OnAccent else if (solid) C.OnInk else C.Ink
            leading?.invoke()
            if (icon != null) Ico(icon, 16.dp, fg)
            if (icon != null || leading != null) Spacer(Modifier.width(7.dp))
            T(text, 14.5.sp, color = fg, weight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
fun Ring(fraction: Float, color: Color, track: Color, size: Dp = 20.dp) {
    Canvas(Modifier.size(size)) {
        val w = 2.dp.toPx()
        val r = this.size.minDimension / 2 - w
        val tl = Offset(center.x - r, center.y - r)
        val sz = androidx.compose.ui.geometry.Size(r * 2, r * 2)
        drawArc(track, 0f, 360f, false, tl, sz, style = Stroke(w))
        drawArc(color, -90f, 360f * fraction, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
    }
}

@Composable
fun Dot(size: Dp = 7.dp) {
    Box(Modifier.size(size).background(C.accent, CircleShape))
}

fun launch(ctx: Context, pkg: String) {
    val pm = ctx.packageManager
    val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return
    ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun uninstall(ctx: Context, pkg: String) {
    val intent = Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(intent) }
}

@Composable
fun InstallButton(app: App, modifier: Modifier = Modifier, solid: Boolean = true, requester: FocusRequester? = null) {
    val ctx = LocalContext.current
    val tick = Device.tick
    val installed = remember(app.pkg, tick) { Device.installed(ctx, app.pkg) }
    val size = Fmt.mb(pickApk(app).size)
    val fg = if (solid) C.OnInk else C.Ink
    val track = if (solid) C.OnInk.copy(alpha = 0.25f) else C.Hair2
    val job = Installer.jobs[app.id]
    val ring: Float? = when (job) {
        is Job.Downloading -> job.fraction
        Job.Checking -> 0.15f
        Job.Installing -> 0.85f
        else -> null
    }
    if (installed == null && !app.runsHere) {
        Pill("Needs ${androidName(app.minSdk ?: 0)}", {}, modifier, solid, null, requester)
        return
    }
    val (text, icon) = when {
        job is Job.Downloading -> "Downloading ${(job.fraction * 100).toInt()}%" to null
        job == Job.Checking -> "Checking signature" to null
        job == Job.Installing -> "Installing" to null
        installed != null && installed.versionCode < app.versionCode -> "Update · $size" to Icons.Download
        installed != null -> "Open" to Icons.Play
        else -> "Install · $size" to Icons.Download
    }
    val onClick: () -> Unit = when {
        ring != null -> ({})
        installed != null && installed.versionCode >= app.versionCode -> ({ launch(ctx, app.pkg) })
        else -> ({ Installer.start(ctx, app) })
    }
    Pill(text, onClick, modifier, solid, icon, requester, leading = ring?.let { r -> { Ring(r, fg, track) } })
}

@Composable
fun JobNote(app: App, modifier: Modifier = Modifier) {
    when (val job = Installer.jobs[app.id]) {
        is Job.Failed -> T(job.message, 12.5.sp, modifier, color = C.Ink2, maxLines = 2)
        is Job.Held -> Row(modifier, verticalAlignment = Alignment.Top) {
            Ico(Icons.ShieldAlert, 16.dp, C.Ink)
            Spacer(Modifier.width(7.dp))
            T(job.message, 12.5.sp, color = C.Ink, weight = FontWeight.Medium, maxLines = 3)
        }
        else -> {}
    }
}
