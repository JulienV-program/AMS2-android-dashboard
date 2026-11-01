package ams2.dash.ui

import ams2.dash.model.TelemetryState
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

/**
 * Le dashboard, dessiné dans un repère virtuel de 1280×800 (résolution de la Galaxy Tab 4)
 * puis mis à l'échelle pour l'écran réel.
 */
class DashboardView(context: Context, private val state: TelemetryState) : View(context) {
    var ipText = ""

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        fontFeatureSettings = "tnum" // chiffres à chasse fixe : pas de tremblement
    }
    private val rect = RectF()

    override fun onDraw(c: Canvas) {
        c.drawColor(BG)
        val scale = min(width / W, height / H)
        c.save()
        c.translate((width - W * scale) / 2f, (height - H * scale) / 2f)
        c.scale(scale, scale)
        synchronized(state) {
            if (state.hasData(SystemClock.elapsedRealtime())) drawDash(c, state) else drawWaiting(c)
        }
        c.restore()
    }

    private fun drawDash(c: Canvas, s: TelemetryState) {
        drawLeds(c, s)
        drawLeft(c, s)
        drawCenter(c, s)
        drawRight(c, s)
        drawTyres(c, s)
        drawBrakes(c, s)
        drawStatus(c, s)
    }

    // ---- Barre de LEDs ---------------------------------------------------------------------

    private fun drawLeds(c: Canvas, s: TelemetryState) {
        val start = s.maxRpm * LED_START
        val shift = s.maxRpm * LED_SHIFT
        val ratio = if (s.maxRpm > 0) (s.rpm - start) / (shift - start) else 0f
        val lit = (ratio * LEDS).toInt().coerceIn(0, LEDS)
        val overRev = s.maxRpm > 0 && s.rpm >= shift
        val blinkOn = (SystemClock.elapsedRealtime() / 80) % 2 == 0L
        val gap = 10f
        val w = (1200f - gap * (LEDS - 1)) / LEDS
        for (i in 0 until LEDS) {
            val x = 40f + i * (w + gap)
            fill.color = when {
                overRev -> if (blinkOn) BLUE else LED_OFF
                i >= lit -> LED_OFF
                i < 6 -> GREEN
                i < 11 -> RED
                else -> BLUE
            }
            rect.set(x, 12f, x + w, 62f)
            c.drawRoundRect(rect, 8f, 8f, fill)
        }
    }

    // ---- Panneau gauche : position, écarts, tours, carburant ---------------------------------

    private fun drawLeft(c: Canvas, s: TelemetryState) {
        panel(c, 16f, 80f, 400f, 556f)
        val x = 36f
        label(c, "POSITION", x, 116f)
        if (s.position > 0) {
            val pos = "P${s.position}"
            draw(c, pos, x, 200f, 92f, FG)
            draw(c, "/${s.numParticipants}", x + width(pos, 92f) + 8f, 200f, 40f, DIM)
        } else {
            draw(c, "P-", x, 200f, 92f, DIM)
        }
        draw(c, "▲ ${gap(s.gapAhead)}", x, 246f, 32f, FG)
        draw(c, "▼ ${gap(s.gapBehind)}", x, 284f, 32f, FG)

        line(c, 32f, 302f, 384f)
        label(c, "TOUR", x, 332f)
        val laps = when {
            s.timedEvent && s.eventTimeRemaining > 0 -> "${s.currentLap}   ${clock(s.eventTimeRemaining)}"
            s.lapsInEvent > 0 -> "${s.currentLap} / ${s.lapsInEvent}"
            else -> "${s.currentLap}"
        }
        draw(c, laps, x, 382f, 46f, FG)

        line(c, 32f, 400f, 384f)
        label(c, "CARBURANT", x, 430f)
        draw(c, String.format(Locale.US, "%.1f L", s.fuelLiters), x, 486f, 52f, FG)
        if (s.fuelPerLap > 0f) {
            val lapsLeft = s.fuelLiters / s.fuelPerLap
            draw(c, String.format(Locale.US, "%.2f L/tour", s.fuelPerLap), x, 532f, 28f, DIM)
            val color = if (lapsLeft < 2f) RED else if (lapsLeft < 4f) YELLOW else FG
            draw(c, String.format(Locale.US, "≈ %.1f tours", lapsLeft), 380f, 532f, 28f, color, Paint.Align.RIGHT)
        } else {
            draw(c, "conso : 1 tour complet requis", x, 532f, 24f, DIM)
        }
    }

    // ---- Centre : rapport, vitesse, pédales --------------------------------------------------

    private fun drawCenter(c: Canvas, s: TelemetryState) {
        panel(c, 412f, 80f, 868f, 556f)
        // Pédales : frein à gauche, accélérateur à droite
        bar(c, 428f, 100f, 448f, 536f, s.brake, RED)
        bar(c, 832f, 100f, 852f, 536f, s.throttle, GREEN)

        val gear = when (s.gear) {
            -1 -> "R"
            0 -> "N"
            else -> s.gear.toString()
        }
        draw(c, gear, 640f, 392f, 340f, FG, Paint.Align.CENTER)
        draw(c, s.speedKmh.toInt().toString(), 640f, 474f, 72f, FG, Paint.Align.CENTER)
        draw(c, "${s.rpm} tr/min", 640f, 520f, 28f, DIM, Paint.Align.CENTER)

        if (s.pitLimiter) {
            fill.color = YELLOW
            rect.set(470f, 92f, 810f, 142f)
            c.drawRoundRect(rect, 10f, 10f, fill)
            draw(c, "LIMITEUR", 640f, 130f, 36f, BG, Paint.Align.CENTER)
        }
    }

    // ---- Panneau droit : chronos et delta ----------------------------------------------------

    private fun drawRight(c: Canvas, s: TelemetryState) {
        panel(c, 880f, 80f, 1264f, 556f)
        val x = 900f
        val r = 1244f
        label(c, "EN COURS", x, 116f)
        if (s.lapInvalid) draw(c, "INVALIDE", r, 116f, 22f, RED, Paint.Align.RIGHT)
        draw(c, lap(s.currentLapTime), x, 172f, 56f, if (s.lapInvalid) RED else FG)

        label(c, "DERNIER", x, 214f)
        draw(c, lap(s.lastLapTime), x, 258f, 42f, FG)
        label(c, "MEILLEUR", x, 298f)
        draw(c, lap(s.bestLapTime), x, 342f, 42f, PURPLE)

        line(c, 896f, 362f, 1248f)
        label(c, "DELTA", x, 392f)
        if (s.sector > 0) draw(c, "S${s.sector}", r, 392f, 22f, DIM, Paint.Align.RIGHT)
        val d = s.delta
        if (d.isNaN()) {
            draw(c, "--.---", 1072f, 470f, 76f, DIM, Paint.Align.CENTER)
        } else {
            val color = if (d <= 0f) GREEN else RED
            draw(c, String.format(Locale.US, "%+.3f", d), 1072f, 470f, 76f, color, Paint.Align.CENTER)
            // Barre de delta : ±1 s pleine échelle
            fill.color = LED_OFF
            rect.set(900f, 496f, 1244f, 524f)
            c.drawRoundRect(rect, 6f, 6f, fill)
            val half = 172f * min(abs(d), 1f)
            fill.color = color
            if (d <= 0f) rect.set(1072f - half, 496f, 1072f, 524f) else rect.set(1072f, 496f, 1072f + half, 524f)
            c.drawRect(rect, fill)
            fill.color = FG
            c.drawRect(1071f, 490f, 1073f, 530f, fill)
        }
    }

    // ---- Bas : pneus et freins ---------------------------------------------------------------

    private fun drawTyres(c: Canvas, s: TelemetryState) {
        panel(c, 16f, 568f, 632f, 748f)
        label(c, "PNEUS", 32f, 600f)
        draw(c, "AV", 32f, 650f, 24f, DIM)
        draw(c, "AR", 32f, 726f, 24f, DIM)
        for (i in 0 until 4) {
            val x = if (i % 2 == 0) 140f else 384f
            val y = if (i < 2) 584f else 662f
            fill.color = tyreColor(s.tyreTemp[i])
            rect.set(x, y, x + 236f, y + 70f)
            c.drawRoundRect(rect, 10f, 10f, fill)
            draw(c, "${s.tyreTemp[i]}°", x + 16f, y + 50f, 40f, BG)
            val remaining = ((1f - s.tyreWear[i]) * 100f).toInt()
            draw(c, "$remaining%", x + 220f, y + 48f, 30f, BG, Paint.Align.RIGHT)
        }
    }

    private fun drawBrakes(c: Canvas, s: TelemetryState) {
        panel(c, 648f, 568f, 1264f, 748f)
        label(c, "FREINS", 664f, 600f)
        draw(c, "AV", 664f, 650f, 24f, DIM)
        draw(c, "AR", 664f, 726f, 24f, DIM)
        for (i in 0 until 4) {
            val x = if (i % 2 == 0) 772f else 1016f
            val y = if (i < 2) 584f else 662f
            fill.color = brakeColor(s.brakeTemp[i])
            rect.set(x, y, x + 236f, y + 70f)
            c.drawRoundRect(rect, 10f, 10f, fill)
            draw(c, "${s.brakeTemp[i]}°", x + 118f, y + 50f, 40f, BG, Paint.Align.CENTER)
        }
    }

    // ---- Barre d'état : drapeau, piste, santé du flux ----------------------------------------

    private fun drawStatus(c: Canvas, s: TelemetryState) {
        val flag = FLAGS.getOrNull(s.flagColour)
        if (flag != null && s.flagColour != 0) {
            fill.color = flag.second
            rect.set(16f, 756f, 1000f, 796f)
            c.drawRoundRect(rect, 8f, 8f, fill)
            val textColor = if (s.flagColour == 5 || s.flagColour == 2) FG else BG
            draw(c, flag.first, 508f, 788f, 30f, textColor, Paint.Align.CENTER)
        } else {
            draw(c, s.trackName, 24f, 786f, 26f, DIM)
        }
        fill.color = if (s.telemetryHz >= 20) GREEN else YELLOW
        c.drawCircle(1150f, 777f, 8f, fill)
        draw(c, "${s.telemetryHz} Hz", 1264f, 786f, 26f, DIM, Paint.Align.RIGHT)
    }

    // ---- Écran d'attente ---------------------------------------------------------------------

    private fun drawWaiting(c: Canvas) {
        draw(c, "En attente des données AMS2…", 640f, 340f, 56f, FG, Paint.Align.CENTER)
        draw(c, "Écoute UDP sur ${ipText.ifEmpty { "?" }} : port 5606", 640f, 410f, 32f, DIM, Paint.Align.CENTER)
        draw(c, "AMS2 › Options › Système : protocole UDP « Project CARS 2 », fréquence UDP activée",
            640f, 470f, 24f, DIM, Paint.Align.CENTER)
    }

    // ---- Outils de dessin --------------------------------------------------------------------

    private fun panel(c: Canvas, l: Float, t: Float, r: Float, b: Float) {
        fill.color = PANEL
        rect.set(l, t, r, b)
        c.drawRoundRect(rect, 14f, 14f, fill)
    }

    private fun line(c: Canvas, x0: Float, y: Float, x1: Float) {
        fill.color = LED_OFF
        c.drawRect(x0, y, x1, y + 2f, fill)
    }

    private fun bar(c: Canvas, l: Float, t: Float, r: Float, b: Float, value: Float, color: Int) {
        fill.color = LED_OFF
        rect.set(l, t, r, b)
        c.drawRoundRect(rect, 6f, 6f, fill)
        fill.color = color
        rect.set(l, b - (b - t) * value.coerceIn(0f, 1f), r, b)
        c.drawRoundRect(rect, 6f, 6f, fill)
    }

    private fun label(c: Canvas, s: String, x: Float, y: Float) = draw(c, s, x, y, 22f, DIM)

    private fun draw(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int,
                     align: Paint.Align = Paint.Align.LEFT) {
        text.textSize = size
        text.color = color
        text.textAlign = align
        c.drawText(s, x, y, text)
    }

    private fun width(s: String, size: Float): Float {
        text.textSize = size
        return text.measureText(s)
    }

    private fun lap(t: Float): String {
        if (t <= 0f) return "-:--.---"
        val m = (t / 60f).toInt()
        return String.format(Locale.US, "%d:%06.3f", m, t - m * 60f)
    }

    private fun clock(t: Float): String {
        val total = t.toInt()
        return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
    }

    private fun gap(t: Float) = if (t > 0f) String.format(Locale.US, "%.3f", t) else "--"

    private fun tyreColor(t: Int) = when {
        t < 60 -> BLUE
        t < 75 -> CYAN
        t <= 100 -> GREEN
        t <= 110 -> YELLOW
        else -> RED
    }

    private fun brakeColor(t: Int) = when {
        t < 150 -> BLUE
        t < 300 -> CYAN
        t <= 750 -> GREEN
        t <= 900 -> YELLOW
        else -> RED
    }

    private companion object {
        const val W = 1280f
        const val H = 800f
        const val LEDS = 15
        const val LED_START = 0.75f // proportion du régime max où la première LED s'allume
        const val LED_SHIFT = 0.96f // proportion du régime max où l'on doit passer le rapport

        val BG = Color.rgb(11, 13, 16)
        val PANEL = Color.rgb(22, 26, 32)
        val LED_OFF = Color.rgb(38, 44, 52)
        val FG = Color.rgb(240, 242, 245)
        val DIM = Color.rgb(138, 147, 160)
        val GREEN = Color.rgb(46, 204, 113)
        val RED = Color.rgb(231, 76, 60)
        val YELLOW = Color.rgb(241, 196, 15)
        val BLUE = Color.rgb(52, 152, 219)
        val CYAN = Color.rgb(26, 188, 156)
        val PURPLE = Color.rgb(181, 123, 255)

        // Couleur de drapeau sur 3 bits (cf. sHighestFlag)
        val FLAGS = listOf(
            "" to BG,
            "DRAPEAU VERT" to GREEN,
            "DRAPEAU BLEU – LAISSEZ PASSER" to BLUE,
            "DRAPEAU BLANC – VOITURE LENTE" to FG,
            "DRAPEAU BLANC – DERNIER TOUR" to FG,
            "DRAPEAU ROUGE" to RED,
            "DRAPEAU JAUNE" to YELLOW,
            "DOUBLE DRAPEAU JAUNE" to YELLOW,
        )
    }
}
