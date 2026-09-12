#version 150

// TPSThings: 儀式の最中に世界そのものを崩す後処理。
// ポストエフェクトのプログラム名は minecraft 名前空間でしか引かれないので、ここに接頭辞付きで置く。
//
// Noise  (0〜1): 帯状の横ずれ・色収差・層の色のノイズブロック・砂嵐・走査線。深い段ほど強い
// Impact (0〜1): インパクトフレーム。2 階調に潰し、コマごとに白黒を反転し、中心へ向かう集中線を入れる
// Seed         : tick ごとに進む。崩れ方は tick 単位で切り替わる (滑らかにしない)
// Depth  (0〜1): 層の色。表層のシアンから深層の赤紫へ
// Dim    (0〜1): 暗さ。崩れ終わった絵にかける (HUD は暗くしない)

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform float Noise;
uniform float Impact;
uniform float Seed;
uniform float Depth;
uniform float Dim;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

// 整数のハッシュ (lowbias32)。float の掛け算で作るハッシュは、入力 (Seed) が大きくなると
// 小数部が消えて崩れ方が固まる
uint mixBits(uint x) {
    x ^= x >> 16;
    x *= 0x7feb352dU;
    x ^= x >> 15;
    x *= 0x846ca68bU;
    x ^= x >> 16;
    return x;
}

// 入力は 1/16 刻みで整数に丸めてから混ぜる (band + 3.1 のような小数も来る)。負の値は下駄を履かせる
float hash(vec2 p) {
    uvec2 q = uvec2(ivec2(floor(p * 16.0)) + ivec2(1 << 20));
    return float(mixBits(q.x ^ mixBits(q.y)) >> 8) / 16777216.0;
}

void main() {
    vec2 px = texCoord * InSize;
    vec2 uv = texCoord;
    vec3 tint = mix(vec3(0.10, 0.80, 0.90), vec3(0.90, 0.08, 0.40), Depth);

    // ---- 帯状の横ずれ ----
    float bandHeight = mix(48.0, 6.0, Noise);
    float band = floor(px.y / bandHeight);
    float roll = hash(vec2(band, Seed));
    uv.x += step(1.0 - Noise * 0.35 - Impact * 0.3, roll)
            * (hash(vec2(Seed, band + 3.1)) - 0.5) * (Noise * 0.12 + Impact * 0.06);

    // ---- 色収差 ----
    float split = Noise * 0.006 + Impact * 0.015;
    vec3 col = vec3(
            texture(DiffuseSampler, uv + vec2(split, 0.0)).r,
            texture(DiffuseSampler, uv).g,
            texture(DiffuseSampler, uv - vec2(split, 0.0)).b);

    // ---- 層の色で塗り潰された矩形が走る ----
    vec2 block = floor(px / vec2(64.0, 14.0));
    float n = hash(block + Seed * 1.37);
    col = mix(col, tint * (0.4 + 0.6 * hash(block * 1.7 + Seed)), step(1.0 - Noise * Noise * 0.12, n));

    // ---- 砂嵐と走査線 ----
    col += (hash(floor(px / 2.0) + Seed * 7.13) - 0.5) * Noise * 0.35;
    col *= 1.0 - Noise * 0.18 * step(0.5, fract(px.y / 4.0));

    // ---- インパクトフレーム ----
    if (Impact > 0.0) {
        float lum = dot(col, vec3(0.299, 0.587, 0.114));
        float ink = step(0.3, lum);
        // 1 コマごとに白黒が入れ替わる
        float flip = mod(floor(Seed), 2.0);
        float value = mix(ink, 1.0 - ink, flip);

        // 集中線: 中心は空け、外側ほど線が乗る
        vec2 c = texCoord - 0.5;
        c.x *= InSize.x / InSize.y;
        float angle = atan(c.y, c.x);
        float ray = step(0.72, hash(vec2(floor(angle * 64.0 / 3.14159), floor(Seed))));
        ray *= smoothstep(0.18, 0.55, length(c));
        value = mix(value, flip, ray);

        vec3 paper = mix(vec3(1.0), vec3(1.0, 0.82, 0.90), Depth * 0.6);
        vec3 impact = mix(vec3(0.02, 0.0, 0.03), paper, value);
        col = mix(col, impact, Impact);
    }

    col *= 1.0 - Dim;

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
