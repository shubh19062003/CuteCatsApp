package com.example.cutecats

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import java.io.FileOutputStream
import kotlin.math.*
import kotlin.random.Random

val BabyPink = Color(0xFFFFF0F5)
val PastelPink = Color(0xFFFFB6C1)
val BlushPink = Color(0xFFFFC0CB)
val White = Color(0xFFFFFFFF)
val Cream = Color(0xFFFFFDD0)
val DarkGrey = Color(0xFF4A4A4A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CuteCatsApp()
        }
    }
}

@Composable
fun CuteCatsApp() {
    val context = LocalContext.current
    val soundEngine = remember { SoundEngine(context) }
    
    DisposableEffect(Unit) {
        onDispose { soundEngine.release() }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(BabyPink)) {
        val screenWidth = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()
        val engine = remember { Engine(screenWidth, screenHeight, soundEngine) }

        var frameTime by remember { mutableStateOf(0L) }
        LaunchedEffect(Unit) {
            var lastTime = System.nanoTime()
            while (isActive) {
                val currentTime = System.nanoTime()
                val dt = (currentTime - lastTime) / 1e9f
                lastTime = currentTime
                engine.update(dt)
                frameTime = currentTime
                delay(16L) 
            }
        }

        Canvas(
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures { offset -> engine.onTap(offset.x, offset.y) }
            }
        ) {
            val t = frameTime
            engine.draw(this)
        }
    }
}

class Engine(private val width: Float, private val height: Float, private val soundEngine: SoundEngine) {
    private val cats = List(8) {
        Cat(x = Random.nextFloat() * width, y = Random.nextFloat() * height,
            color = if (Random.nextBoolean()) White else Cream, hasBow = Random.nextFloat() > 0.6f)
    }
    private val particles = mutableListOf<Particle>()
    private val backgroundHearts = List(15) { FloatingHeart(width, height) }

    fun update(dt: Float) {
        cats.forEach { it.update(dt, this) }
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.update(dt)
            if (p.isDead()) iterator.remove()
        }
        backgroundHearts.forEach { it.update(dt, width, height) }
    }

    fun draw(scope: DrawScope) {
        backgroundHearts.forEach { it.draw(scope) }
        cats.sortedBy { it.y }.forEach { it.draw(scope) }
        particles.forEach { it.draw(scope) }
    }

    fun onTap(tx: Float, ty: Float) {
        particles.add(Particle(tx, ty, isTap = true))
        val availableCats = cats.filter { !it.isRunning }.sortedBy { hypot(it.x - tx, it.y - ty) }
        val catsToMove = availableCats.take(Random.nextInt(1, 3))
        catsToMove.forEach { cat ->
            val offsetX = tx + Random.nextFloat() * 40f - 20f
            val offsetY = ty + Random.nextFloat() * 40f - 20f
            cat.setTarget(offsetX, offsetY)
        }
    }

    fun playMeow() = soundEngine.play()
    fun addHeart(x: Float, y: Float) = particles.add(Particle(x, y, isTap = false))
}

class Cat(var x: Float, var y: Float, val color: Color, val hasBow: Boolean) {
    private var targetX: Float = x
    private var targetY: Float = y
    var isRunning = false
    private var facingRight = true
    private var bounceTime = 0f
    private var idleTime = Random.nextFloat() * 100f
    private val speed = 400f 

    fun setTarget(tx: Float, ty: Float) {
        targetX = tx; targetY = ty; isRunning = true; facingRight = tx > x
    }

    fun update(dt: Float, engine: Engine) {
        if (isRunning) {
            bounceTime += dt * 15f
            val dx = targetX - x
            val dy = targetY - y
            val dist = hypot(dx, dy)
            if (dist < 10f) {
                x = targetX; y = targetY; isRunning = false; bounceTime = 0f
                engine.playMeow(); engine.addHeart(x, y - 40f)
            } else {
                x += (dx / dist) * speed * dt; y += (dy / dist) * speed * dt; facingRight = dx > 0
            }
        } else idleTime += dt
    }

    fun draw(scope: DrawScope) {
        scope.withTransform({
            translate(left = x, top = y)
            if (!facingRight) scale(scaleX = -1f, scaleY = 1f)
        }) {
            val bounceOffset = if (isRunning) -abs(sin(bounceTime)) * 15f else 0f
            val breathScale = if (!isRunning) 1f + sin(idleTime * 2f) * 0.02f else 1f
            
            drawOval(Color.Black.copy(alpha = 0.08f), Offset(-35f, 15f), Size(70f, 20f))

            withTransform({
                translate(left = 0f, top = bounceOffset)
                scale(scaleX = 1f, scaleY = breathScale, pivot = Offset(0f, 20f))
            }) {
                val tailAngle = if (isRunning) sin(bounceTime) * 20f else sin(idleTime * 3f) * 10f
                withTransform({ translate(-35f, 5f); rotate(tailAngle, Offset(0f, 0f)) }) {
                    drawOval(color, Offset(-20f, -5f), Size(30f, 12f))
                }

                drawRoundRect(color, Offset(-40f, -30f), Size(80f, 60f), CornerRadius(40f, 40f))
                
                drawPath(Path().apply { moveTo(-25f, -25f); lineTo(-35f, -50f); lineTo(-10f, -30f); close() }, color)
                drawPath(Path().apply { moveTo(-25f, -25f); lineTo(-35f, -50f); lineTo(-10f, -30f); close() }, PastelPink.copy(0.6f))
                drawPath(Path().apply { moveTo(25f, -25f); lineTo(35f, -50f); lineTo(10f, -30f); close() }, color)
                drawPath(Path().apply { moveTo(25f, -25f); lineTo(35f, -50f); lineTo(10f, -30f); close() }, PastelPink.copy(0.6f))

                val blink = !isRunning && (idleTime % 4f) > 3.8f
                if (blink) {
                    drawLine(DarkGrey, Offset(10f, -5f), Offset(25f, -5f), 3f)
                    drawLine(DarkGrey, Offset(-10f, -5f), Offset(-25f, -5f), 3f)
                } else {
                    drawOval(DarkGrey, Offset(12f, -12f), Size(10f, 14f))
                    drawOval(DarkGrey, Offset(-22f, -12f), Size(10f, 14f))
                    drawOval(White, Offset(14f, -10f), Size(4f, 6f))
                    drawOval(White, Offset(-20f, -10f), Size(4f, 6f))
                }

                drawOval(PastelPink.copy(0.7f), Offset(22f, -2f), Size(12f, 8f))
                drawOval(PastelPink.copy(0.7f), Offset(-34f, -2f), Size(12f, 8f))
                
                drawPath(Path().apply { moveTo(-4f, 2f); quadraticBezierTo(0f, 6f, 4f, 2f) }, DarkGrey)
                
                drawOval(color, Offset(-20f, 25f), Size(14f, 10f))
                drawOval(color, Offset(6f, 25f), Size(14f, 10f))

                if (hasBow) {
                    drawPath(Path().apply { moveTo(0f, -20f); lineTo(-10f, -30f); lineTo(-10f, -10f); close() }, BlushPink)
                    drawPath(Path().apply { moveTo(0f, -20f); lineTo(10f, -30f); lineTo(10f, -10f); close() }, BlushPink)
                    drawOval(BlushPink, Offset(-3f, -23f), Size(6f, 6f))
                }
            }
        }
    }
}

class Particle(var x: Float, var y: Float, val isTap: Boolean) {
    private var life = 1f
    fun update(dt: Float) { life -= dt * 1.5f; if (!isTap) y -= dt * 50f }
    fun isDead() = life <= 0f
    fun draw(scope: DrawScope) {
        val alpha = life.coerceIn(0f, 1f)
        scope.withTransform({
            translate(x, y)
            val s = if (isTap) 1f + (1f - life) * 2f else 1f
            scale(s, s)
        }) {
            if (isTap) drawCircle(PastelPink.copy(alpha * 0.5f), 20f)
            else drawPath(createHeartPath(), BlushPink.copy(alpha))
        }
    }
}

class FloatingHeart(screenWidth: Float, screenHeight: Float) {
    var x = Random.nextFloat() * screenWidth
    var y = Random.nextFloat() * screenHeight
    var speed = Random.nextFloat() * 20f + 10f
    var size = Random.nextFloat() * 0.5f + 0.5f
    fun update(dt: Float, w: Float, h: Float) {
        y -= speed * dt
        if (y < -50f) { y = h + 50f; x = Random.nextFloat() * w }
    }
    fun draw(scope: DrawScope) {
        scope.withTransform({ translate(x, y); scale(size, size) }) {
            drawPath(createHeartPath(), White.copy(0.4f))
        }
    }
}

fun createHeartPath(): Path = Path().apply {
    moveTo(0f, 5f); cubicTo(-15f, -10f, -30f, 10f, 0f, 25f); cubicTo(30f, 10f, 15f, -10f, 0f, 5f); close()
}

class SoundEngine(context: Context) {
    private val soundPool = SoundPool.Builder().setMaxStreams(4)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private var meowId = -1

    init {
        val tempFile = File(context.cacheDir, "meow.wav")
        if (!tempFile.exists()) generateMeowWav(tempFile)
        meowId = soundPool.load(tempFile.absolutePath, 1)
    }

    fun play() { if (meowId != -1) soundPool.play(meowId, 1f, 1f, 0, 0, 1f) }
    fun release() = soundPool.release()

    private fun generateMeowWav(file: File) {
        val sampleRate = 22050; val duration = 0.25; val numSamples = (sampleRate * duration).toInt()
        val data = ByteArray(numSamples * 2)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val freq = 700.0 + 400.0 * sin(t * Math.PI / duration)
            val amp = if (t < 0.05) t / 0.05 else max(0.0, 1.0 - (t - 0.05) / (duration - 0.05))
            val signal = sin(2 * Math.PI * freq * t) + 0.3 * sin(2 * Math.PI * (freq * 1.5) * t)
            val sample = (signal * amp * 12000).toInt().toShort()
            data[i * 2] = (sample.toInt() and 0xFF).toByte()
            data[i * 2 + 1] = (sample.toInt() shr 8).toByte()
        }
        FileOutputStream(file).use { out ->
            val h = ByteArray(44); val s = data.size + 36; val b = sampleRate * 2
            h[0]='R'.code.toByte(); h[1]='I'.code.toByte(); h[2]='F'.code.toByte(); h[3]='F'.code.toByte()
            h[4]=(s and 0xff).toByte(); h[5]=((s shr 8) and 0xff).toByte(); h[6]=((s shr 16) and 0xff).toByte(); h[7]=((s shr 24) and 0xff).toByte()
            h[8]='W'.code.toByte(); h[9]='A'.code.toByte(); h[10]='V'.code.toByte(); h[11]='E'.code.toByte()
            h[12]='f'.code.toByte(); h[13]='m'.code.toByte(); h[14]='t'.code.toByte(); h[15]=' '.code.toByte()
            h[16]=16; h[20]=1; h[22]=1; h[32]=2; h[34]=16
            h[24]=(sampleRate and 0xff).toByte(); h[25]=((sampleRate shr 8) and 0xff).toByte()
            h[26]=((sampleRate shr 16) and 0xff).toByte(); h[27]=((sampleRate shr 24) and 0xff).toByte()
            h[28]=(b and 0xff).toByte(); h[29]=((b shr 8) and 0xff).toByte()
            h[30]=((b shr 16) and 0xff).toByte(); h[31]=((b shr 24) and 0xff).toByte()
            h[36]='d'.code.toByte(); h[37]='a'.code.toByte(); h[38]='t'.code.toByte(); h[39]='a'.code.toByte()
            h[40]=(data.size and 0xff).toByte(); h[41]=((data.size shr 8) and 0xff).toByte()
            h[42]=((data.size shr 16) and 0xff).toByte(); h[43]=((data.size shr 24) and 0xff).toByte()
            out.write(h); out.write(data)
        }
    }
}
