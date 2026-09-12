package com.mockrun.app.ui.theme

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * AGSL (Android Graphics Shading Language) renderer for the iOS-26-style Liquid Glass material.
 *
 * ## Why a shader, and why it does NOT sample the backdrop
 *
 * The project previously drove real backdrop blur through Haze (`hazeChild`) and had to remove it
 * in v1.3.9 (`1ac36a2`): the main screen is an OSMDroid map (an Android View, invisible to
 * Compose's GraphicsLayer capture) moved by `graphicsLayer.translationX`, and Haze's captured
 * backdrop lagged one frame behind the gesture — cards showed a misplaced "ghost" blur.
 * Any library that samples the backdrop (haze, NadeemIqbal/liquid-glass, ...) inherits that
 * misalignment on this screen layout.
 *
 * The observation this file is built on: what makes iOS 26 glass read as *glass* is not the
 * backdrop blur — it is the **edge optics**: a Fresnel rim that catches light, chromatic
 * dispersion splitting that rim into a subtle prismatic rainbow, a diagonal specular sweep,
 * and a whisper of frosted grain. All of these are functions of the glass silhouette alone
 * (a signed distance field), so they can be rendered without reading a single pixel of the
 * backdrop. That gives:
 *
 *  - zero backdrop capture → no ghost-blur, no slide misalignment, no per-frame GraphicsLayer cost
 *  - one fullscreen-quad fragment shader on the glass surface itself → negligible GPU cost
 *  - the translucent base gradient is kept (map roads still shine through, as before)
 *
 * ## Rendering contract
 *
 *  - AGSL output must be **premultiplied** alpha; additive light (rim/specular/grain) is added
 *    after premultiplication so a glowing edge stays bright even where the tint is thin.
 *  - The shape is a rounded rectangle; the corner radius is extracted from the Compose
 *    [androidx.compose.ui.graphics.Outline.Rounded] at draw time, so every existing call site
 *    keeps its own corner geometry.
 *  - Callers gate on `Build.VERSION.SDK_INT >= 33` (RuntimeShader/AGSL requirement); older
 *    devices keep the previous gradient implementation.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class LiquidGlassPaint {

    private val shader = RuntimeShader(SHADER_SOURCE)
    val paint = Paint().apply { shader = this@LiquidGlassPaint.shader }

    /**
     * Uploads per-draw uniforms. Colors are passed as straight (non-premultiplied) RGBA floats.
     */
    fun configure(
        width: Float,
        height: Float,
        cornerPx: Float,
        baseTop: Color,
        baseBottom: Color,
        edgeTint: Color,
        rimWidthPx: Float,
        dispersion: Float,
        specular: Float,
        grain: Float,
        hairline: Float
    ) {
        shader.setFloatUniform("res", width, height)
        shader.setFloatUniform("corner", cornerPx.coerceIn(0f, minOf(width, height) * 0.5f))
        shader.setFloatUniform("baseTop", baseTop.red, baseTop.green, baseTop.blue, baseTop.alpha)
        shader.setFloatUniform("baseBottom", baseBottom.red, baseBottom.green, baseBottom.blue, baseBottom.alpha)
        shader.setFloatUniform("edgeTint", edgeTint.red, edgeTint.green, edgeTint.blue, edgeTint.alpha)
        shader.setFloatUniform("rim", rimWidthPx)
        shader.setFloatUniform("dispersion", dispersion)
        shader.setFloatUniform("specular", specular)
        shader.setFloatUniform("grain", grain)
        shader.setFloatUniform("hairline", hairline)
    }

    companion object {
        /**
         * Tuned against the iOS 26 "Regular" glass material.
         *
         * Visual layers, back to front:
         *  1. vertical base gradient (kept from the old implementation — high translucency so
         *     map roads/pins remain readable underneath)
         *  2. Fresnel lensing: the tint deepens toward the silhouette edge, as a real lens is
         *     thicker at its rim
         *  3. rim light with per-channel falloff widths (R widest, B narrowest) → prismatic
         *     chromatic dispersion, directionally weighted so the top-left light source reads
         *  4. diagonal specular sweep across the upper half
         *  5. hash grain for the frosted texture (also dithers away gradient banding)
         *  6. crisp hairline at d≈0, the spiritual successor of the old `.border()`
         */
        private const val SHADER_SOURCE = /* language=AGSL */ """
uniform float2 res;
uniform float corner;
uniform float4 baseTop;
uniform float4 baseBottom;
uniform float4 edgeTint;
uniform float rim;
uniform float dispersion;
uniform float specular;
uniform float grain;
uniform float hairline;

// Signed distance to a rounded rectangle centred at the origin.
// Negative inside, zero on the edge, positive outside.
float sdRound(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

float hash21(float2 p) {
    p = fract(p * float2(234.34, 435.345));
    p += dot(p, p + 34.23);
    return fract(p.x * p.y);
}

half4 main(float2 fragCoord) {
    float2 halfRes = res * 0.5;
    // Inset by ~0.75px so the SDF edge lands inside the (already clipped) bounds: the
    // modifier clips to the same shape, so anything outside is discarded anyway.
    float d = sdRound(fragCoord - halfRes, halfRes - 0.75, corner);

    // Anti-aliased coverage of the shape at this pixel.
    float mask = smoothstep(0.75, -0.75, d);
    if (mask <= 0.0) {
        return half4(0.0);
    }

    float inside = -d;            // > 0 inside the glass
    float2 uv = fragCoord / res;

    // (1) base gradient
    float4 base = mix(baseTop, baseBottom, smoothstep(0.0, 1.0, uv.y));

    // (2) Fresnel lensing — tint deepens toward the edge like a thick lens rim.
    float fres = exp(-inside / (min(res.x, res.y) * 0.30));
    float4 lensed = mix(base, edgeTint, fres * 0.45);
    float a = clamp(lensed.a, 0.0, 1.0);

    // (3) rim light with chromatic dispersion.
    // Different exponential falloffs per channel: R bleeds widest, B tightest, so the rim
    // splits into a faint prismatic rainbow — the iOS 26 signature.
    float eR = exp(-inside / max(rim * (1.0 + dispersion), 0.5));
    float eG = exp(-inside / max(rim, 0.5));
    float eB = exp(-inside / max(rim * (1.0 - dispersion * 0.55), 0.5));
    float3 rimRGB = float3(eR, eG, eB);

    // Directional weighting: the light source sits top-left, so rim sections whose outward
    // normal faces up-left glow brightest (a real lens catches light asymmetrically).
    float2 outward = normalize(fragCoord - halfRes + float2(1e-4));
    float dirTerm = clamp(0.5 + 0.5 * dot(outward, normalize(float2(-0.55, -0.83))), 0.0, 1.0);
    rimRGB *= 0.30 + 0.90 * dirTerm;

    // (4) diagonal specular sweep across the upper half.
    float bandPos = uv.x - uv.y * 0.45 - 0.30;
    float band = exp(-bandPos * bandPos * 42.0);
    float spec = band * smoothstep(0.80, 0.10, uv.y) * specular;
    // Corners catch extra glint because they are doubly curved.
    float glint = max(max(rimRGB.r, rimRGB.g), rimRGB.b) * smoothstep(0.55, 0.0, uv.y) * specular * 1.6;

    // (5) frosted grain (also dithers gradient banding on 8-bit targets).
    float g = (hash21(fragCoord) - 0.5) * grain;

    // (6) crisp hairline exactly on the edge — replaces the old `.border()` stroke.
    float line = smoothstep(1.6, 0.0, abs(d + 0.6)) * hairline;

    // Compose. The base is alpha-composited (premultiplied); the light layers are additive
    // and applied *after* premultiplication so a glowing rim stays bright even where the
    // tint is nearly transparent — that is what makes it read as light, not as paint.
    float3 premul = lensed.rgb * a;
    float3 col = premul
        + rimRGB * 0.62
        + float3(spec)
        + float3(glint)
        + float3(g)
        + float3(line);
    col = min(col, float3(1.0));

    return half4(col * mask, min(a + line * 0.6, 1.0) * mask);
}
"""
    }
}

/**
 * Visual presets mirroring the two system appearances (and the containerColor override used by
 * tinted glass cards). Values tuned to keep map content readable underneath.
 */
object LiquidGlassPresets {

    data class Params(
        val baseTop: Color,
        val baseBottom: Color,
        val edgeTint: Color,
        val dispersion: Float,
        val specular: Float,
        val grain: Float,
        val hairline: Float
    )

    /** Everyday light-mode glass: cool near-white, airy translucency, gentle dispersion. */
    val light = Params(
        baseTop = Color(0.96f, 0.98f, 1.00f, 0.52f),
        baseBottom = Color(0.86f, 0.92f, 0.98f, 0.40f),
        edgeTint = Color(1.00f, 1.00f, 1.00f, 0.50f),
        dispersion = 0.32f,
        specular = 0.16f,
        grain = 0.018f,
        hairline = 0.55f
    )

    /** Dark-mode glass: obsidian depth, cooler rim, slightly stronger grain. */
    val dark = Params(
        baseTop = Color(0.17f, 0.19f, 0.24f, 0.56f),
        baseBottom = Color(0.09f, 0.10f, 0.13f, 0.46f),
        edgeTint = Color(0.56f, 0.63f, 0.80f, 0.32f),
        dispersion = 0.34f,
        specular = 0.11f,
        grain = 0.022f,
        hairline = 0.38f
    )

    /** Derives a preset from a caller-supplied [containerColor] (tinted glass). */
    fun tinted(containerColor: Color, dark: Boolean): Params {
        val lift = if (dark) Color(0.30f, 0.32f, 0.36f) else Color.White
        val base = lerp(containerColor, lift, 0.35f)
        return Params(
            baseTop = base.copy(alpha = 0.62f),
            baseBottom = containerColor.copy(alpha = 0.48f),
            edgeTint = lerp(containerColor, Color.White, 0.5f).copy(alpha = 0.45f),
            dispersion = if (dark) 0.32f else 0.30f,
            specular = if (dark) 0.10f else 0.14f,
            grain = if (dark) 0.020f else 0.016f,
            hairline = 0.45f
        )
    }
}
