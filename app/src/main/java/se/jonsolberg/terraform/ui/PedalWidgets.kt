package se.jonsolberg.terraform.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.jonsolberg.terraform.model.pct
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Enclosure colours: navy control panel over a pale blue body. */
val PanelNavy = Color(0xFF1E2A5E)
val PanelNavyDark = Color(0xFF172150)
val BodyBlue = Color(0xFF93B5CF)
val BodyBlueDark = Color(0xFF7FA2BF)
val PanelText = Color(0xFFE9EEF8)
val BodyText = Color(0xFF1B2552)
val LedRed = Color(0xFFFF3B30)
val LedBlue = Color(0xFF3D8BFF)
val LedGreen = Color(0xFF34D058)
val LedPurple = Color(0xFFB45CFF)
val LedYellow = Color(0xFFFFD43B)

private val panelLabel = TextStyle(color = PanelText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
private val bodyLabel = TextStyle(color = BodyText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)

/** Navy top panel with a faint grain. */
fun Modifier.navyPanel(): Modifier = this.drawWithCache {
    val rnd = Random(3)
    val grain = List((size.width * size.height / 1200f).toInt().coerceAtMost(3000)) {
        Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height) to rnd.nextFloat() * 0.05f
    }
    val base = Brush.verticalGradient(listOf(PanelNavy, PanelNavyDark))
    onDrawBehind {
        drawRect(base)
        grain.forEach { (o, a) -> drawCircle(Color.White.copy(alpha = a), radius = 1f, center = o) }
    }
}

/** Pale blue lower half with a wire-frame landscape fading into the distance. */
fun Modifier.wireframeBody(): Modifier = this.drawWithCache {
    val w = size.width
    val h = size.height
    val horizon = h * 0.30f
    val line = Color(0xFF4F76A3).copy(alpha = 0.35f)
    val rnd = Random(11)
    // ridge line: a few random peaks
    val peaks = List(14) { i -> Offset(w * i / 13f, horizon - rnd.nextFloat() * h * 0.22f) }
    val ridge = Path().apply {
        moveTo(0f, horizon)
        peaks.forEach { lineTo(it.x, it.y) }
        lineTo(w, horizon)
    }
    onDrawBehind {
        drawRect(Brush.verticalGradient(listOf(BodyBlue, BodyBlueDark)))
        drawPath(ridge, line, style = Stroke(1.2f))
        peaks.forEach { drawLine(line, it, Offset(it.x, horizon), strokeWidth = 0.8f) }
        // perspective floor grid
        val vanish = Offset(w / 2, horizon - h * 0.1f)
        for (i in -12..12) {
            val x = w / 2 + i * w / 10f
            val start = Offset(x, h)
            val t = (horizon - vanish.y) / (h - vanish.y)
            drawLine(line, Offset(vanish.x + (start.x - vanish.x) * t, horizon), start, strokeWidth = 0.8f)
        }
        var y = horizon
        var gap = h * 0.03f
        while (y < h) {
            drawLine(line, Offset(0f, y), Offset(w, y), strokeWidth = 0.8f)
            y += gap
            gap *= 1.35f
        }
    }
}

// ------------------------------------------------------------------ knob

/**
 * Black rotary knob, 0..127, label underneath like on the pedal.
 * Drag up/right to turn clockwise, double-tap to centre. The value shows while turning.
 */
@Composable
fun PedalKnob(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    knobSize: Dp = 60.dp,
    format: (Int) -> String = { "${pct(it)}" },
) {
    var dragging by remember { mutableStateOf(false) }
    var accum by remember { mutableFloatStateOf(value.toFloat()) }
    val latestValue by rememberUpdatedState(value)
    val latestChange by rememberUpdatedState(onValueChange)
    LaunchedEffect(value) { if (!dragging) accum = value.toFloat() }
    val pxPerStep = with(LocalDensity.current) { 1.5.dp.toPx() }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(knobSize)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            dragging = true
                            accum = latestValue.toFloat()
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, drag ->
                        change.consume()
                        accum = (accum + (drag.x - drag.y) / pxPerStep).coerceIn(0f, 127f)
                        val nv = accum.roundToInt()
                        if (nv != latestValue) latestChange(nv)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { latestChange(63) })
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) { drawBlackKnob(120f + 300f * value / 127f) }
            if (dragging) {
                Box(
                    Modifier
                        .background(Color(0xE0101010), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(format(value), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(label, style = panelLabel, maxLines = 1)
    }
}

/** Glossy black knob with ribbed skirt and a white pointer line; [angle] in degrees, 0 = right. */
fun DrawScope.drawBlackKnob(angle: Float, radius: Float = size.minDimension / 2f, c: Offset = center) {
    val r = radius
    drawCircle(Color.Black.copy(alpha = 0.55f), radius = r * 0.97f, center = c + Offset(r * 0.05f, r * 0.10f))
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFF2C2C2F), Color(0xFF060607)), center = c, radius = r),
        radius = r * 0.97f, center = c,
    )
    val ribs = 36
    for (i in 0 until ribs) {
        val a = Math.toRadians(i * 360.0 / ribs)
        val ca = cos(a).toFloat()
        val sa = sin(a).toFloat()
        drawLine(
            Color(0xFF333336), c + Offset(ca * r * 0.84f, sa * r * 0.84f),
            c + Offset(ca * r * 0.96f, sa * r * 0.96f), strokeWidth = r * 0.04f,
        )
    }
    // top face
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFF3A3A3E), Color(0xFF0D0D0F)), center = c - Offset(r * 0.25f, r * 0.3f), radius = r * 0.9f),
        radius = r * 0.80f, center = c,
    )
    drawCircle(Color.White.copy(alpha = 0.08f), radius = r * 0.80f, center = c, style = Stroke(1.2f))
    val a = Math.toRadians(angle.toDouble())
    val ca = cos(a).toFloat()
    val sa = sin(a).toFloat()
    drawLine(
        Color(0xFFF7F5F0), c + Offset(ca * r * 0.30f, sa * r * 0.30f), c + Offset(ca * r * 0.78f, sa * r * 0.78f),
        strokeWidth = r * 0.08f, cap = StrokeCap.Round,
    )
}

// ------------------------------------------------------------------ effect selector

/**
 * The program dial: a black knob ringed by one LED per effect with the
 * abbreviations beside them. Tap or drag towards an effect to select it.
 */
@Composable
fun EffectSelector(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val latestSelect by rememberUpdatedState(onSelect)
    val latestSelected by rememberUpdatedState(selected)
    val style = TextStyle(color = PanelText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    val selStyle = style.copy(color = Color(0xFF9CC7FF))

    fun pick(pos: Offset, w: Float, h: Float) {
        val dx = pos.x - w / 2
        val dy = pos.y - h / 2
        if (dx * dx + dy * dy < (h * 0.08f) * (h * 0.08f)) return
        var deg = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        if (deg < 0) deg += 360f
        val i = nearestIndex(deg, labels.size)
        if (i != latestSelected) latestSelect(i)
    }

    Canvas(
        modifier
            .pointerInput(labels.size) {
                detectTapGestures { pick(it, size.width.toFloat(), size.height.toFloat()) }
            }
            .pointerInput(labels.size) {
                detectDragGestures { change, _ ->
                    change.consume()
                    pick(change.position, size.width.toFloat(), size.height.toFloat())
                }
            },
    ) {
        val h = size.height
        val c = center
        // Shrink the ring when the space is narrow so the side labels never get clipped.
        val maxLabel = labels.maxOf { measurer.measure(it, style).size.width }
        val textGap = h * 0.06f
        val ledR = minOf(h * 0.36f, size.width / 2f - maxLabel - textGap - 4f)
        val textR = ledR + textGap
        labels.forEachIndexed { i, label ->
            val a = Math.toRadians(angleOf(i).toDouble())
            val ca = cos(a).toFloat()
            val sa = sin(a).toFloat()
            val led = c + Offset(ca * ledR, sa * ledR)
            drawSmallLed(led, h * 0.035f, LedBlue, i == selected)
            val layout = measurer.measure(label, if (i == selected) selStyle else style)
            val tw = layout.size.width
            val th = layout.size.height
            val anchor = c + Offset(ca * textR, sa * textR)
            val x = when {
                ca < -0.25f -> anchor.x - tw
                ca > 0.25f -> anchor.x
                else -> anchor.x - tw / 2f
            }
            val y = if (abs(ca) <= 0.25f) anchor.y - th else anchor.y - th / 2f
            drawText(layout, topLeft = Offset(x, y))
        }
        drawBlackKnob(angleOf(selected), ledR * 0.72f, c)
    }
}

/** LED i sits at 120° + 30°·i (screen degrees, clockwise from the right), leaving a gap at the bottom. */
private fun angleOf(i: Int): Float = 120f + 30f * i

private fun nearestIndex(deg: Float, count: Int): Int {
    var best = 0
    var bestDist = Float.MAX_VALUE
    for (i in 0 until count) {
        val d = abs(((deg - angleOf(i)) % 360f + 540f) % 360f - 180f)
        if (d < bestDist) {
            bestDist = d
            best = i
        }
    }
    return best
}

fun DrawScope.drawSmallLed(c: Offset, r: Float, color: Color, on: Boolean) {
    drawCircle(Color(0xFF8D9095), radius = r * 1.45f, center = c)
    if (on) {
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.7f), Color.Transparent), center = c, radius = r * 3f), radius = r * 3f, center = c)
        drawCircle(Brush.radialGradient(listOf(Color.White, color), center = c, radius = r), radius = r, center = c)
    } else {
        drawCircle(Brush.radialGradient(listOf(Color(0xFFDADCE0), Color(0xFF6E7176)), center = c - Offset(r * 0.3f, r * 0.3f), radius = r * 1.2f), radius = r, center = c)
    }
}

// ------------------------------------------------------------------ PRE/POST mini toggle

/** Two-way mini toggle on the top edge: left = PRE/POST, right = NORMAL. */
@Composable
fun RoutingToggle(prePost: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val latest by rememberUpdatedState(onChange)
    androidx.compose.foundation.layout.Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("PRE/POST", color = if (prePost) PanelText else PanelText.copy(alpha = 0.45f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Canvas(
            Modifier
                .size(width = 44.dp, height = 20.dp)
                .pointerInput(Unit) { detectTapGestures { latest(it.x < size.width / 2) } },
        ) {
            val c = center
            val h = size.height
            drawCircle(Brush.sweepGradient(listOf(Color(0xFFF2F2F2), Color(0xFF8C8F94), Color(0xFFE0E0E0), Color(0xFF6B6E73), Color(0xFFF2F2F2)), c), radius = h * 0.42f, center = c)
            drawCircle(Color(0xFF2B2C2F), radius = h * 0.24f, center = c)
            val tip = Offset(if (prePost) size.width * 0.12f else size.width * 0.88f, c.y)
            drawLine(
                Brush.verticalGradient(listOf(Color(0xFFE0E0E0), Color(0xFF7B7E83))),
                c, tip, strokeWidth = h * 0.22f, cap = StrokeCap.Round,
            )
            drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFF9A9DA2)), center = tip, radius = h * 0.2f), radius = h * 0.18f, center = tip)
        }
        Text("NORMAL", color = if (!prePost) PanelText else PanelText.copy(alpha = 0.45f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}

// ------------------------------------------------------------------ LEDs, buttons, footswitches

@Composable
fun Led(color: Color, on: Boolean, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    Canvas(modifier.size(size)) {
        drawSmallLed(center, this.size.minDimension * 0.28f, color, on)
    }
}

/** The small black PRESET / SAVE push button with its four LEDs above it. */
@Composable
fun PresetButton(
    litLed: Int?,
    ledColor: Color,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 30.dp,
) {
    var pressed by remember { mutableStateOf(false) }
    val tap by rememberUpdatedState(onTap)
    val long by rememberUpdatedState(onLongPress)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.foundation.layout.Row {
            for (i in 0 until 4) Led(ledColor, litLed == i, size = 14.dp)
        }
        Spacer(Modifier.height(6.dp))
        Canvas(
            Modifier
                .size(buttonSize)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            tryAwaitRelease()
                            pressed = false
                        },
                        onTap = { tap() },
                        onLongPress = { long() },
                    )
                },
        ) {
            val r = size.minDimension / 2
            drawCircle(Brush.sweepGradient(listOf(Color(0xFFE8E8E8), Color(0xFF85888D), Color(0xFFE0E0E0), Color(0xFF6B6E73), Color(0xFFE8E8E8)), center), radius = r)
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFF3A3A3E), Color(0xFF050506)), center = center - Offset(r * 0.2f, r * 0.2f), radius = r),
                radius = r * if (pressed) 0.62f else 0.70f,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text("PRESET / SAVE", style = bodyLabel, fontSize = 8.sp)
    }
}

/** Chrome footswitch with its LED beside it ([ledLeft] or right) and the label underneath. */
@Composable
fun Footswitch(
    label: String,
    ledColor: Color,
    ledOn: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    ledLeft: Boolean = false,
    switchSize: Dp = 58.dp,
) {
    var pressed by remember { mutableStateOf(false) }
    val tap by rememberUpdatedState(onTap)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            if (ledLeft) Led(ledColor, ledOn, size = switchSize * 0.42f)
            Canvas(
                Modifier
                    .size(switchSize)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                pressed = true
                                tryAwaitRelease()
                                pressed = false
                            },
                            onTap = { tap() },
                        )
                    },
            ) { drawFootswitch(pressed) }
            if (!ledLeft) Led(ledColor, ledOn, size = switchSize * 0.42f)
        }
        Spacer(Modifier.height(2.dp))
        Text(label, style = bodyLabel)
    }
}

private fun DrawScope.drawFootswitch(pressed: Boolean) {
    val r = size.minDimension / 2
    val c = center
    val chrome = listOf(Color(0xFFF2F2F2), Color(0xFF8C8F94), Color(0xFFE0E0E0), Color(0xFF6B6E73), Color(0xFFF2F2F2))
    drawCircle(Color.Black.copy(alpha = 0.45f), radius = r * 0.98f, center = c + Offset(r * 0.04f, r * 0.08f))
    drawCircle(Brush.sweepGradient(chrome, c), radius = r * 0.96f, center = c)
    drawCircle(Color(0xFF1C1D20), radius = r * 0.78f, center = c)
    val k = if (pressed) 0.90f else 1f
    drawCircle(
        Brush.sweepGradient(listOf(Color(0xFFBFC2C6), Color(0xFFF7F7F7), Color(0xFF7D8085), Color(0xFFEDEDED), Color(0xFFBFC2C6)), c),
        radius = r * 0.70f * k, center = c,
    )
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFFDFDFD), Color(0xFFA9ACB1)), center = c - Offset(r * 0.15f, r * 0.15f), radius = r * 0.6f),
        radius = r * 0.52f * k, center = c,
    )
    drawCircle(Color.Black.copy(alpha = 0.18f), radius = r * 0.52f * k, center = c, style = Stroke(1.2f))
}
