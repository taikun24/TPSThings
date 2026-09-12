// ドット絵の宇宙シェーダー (ShaderToy互換 / Image shader)
// mainImage(out vec4 fragColor, in vec2 fragCoord) を実装するだけでOK
//
// fragCoord はラッパー側で GUI の 1 ドット単位に丸めてある (マイクラのドットと揃う)。
// 色は決まったパレットから選び、中間の明るさはディザで作る。
// 星は 1 ドットと十字のドット絵、動きもフレームを落としてカクっと進める。
//
// 奥から: 星雲 (紫 / 青緑のパレット) と塵 → 渦巻銀河 → 星 3 層 (視差) → 流れ星

// ---- ユーティリティ ----
float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

vec2 hash22(vec2 p) {
    float n = hash21(p);
    return vec2(n, hash21(p + n + 17.0));
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float amp = 0.5;
    mat2 rot = mat2(0.8, 0.6, -0.6, 0.8);
    for (int i = 0; i < 4; i++) {
        v += amp * noise(p);
        p = rot * p * 2.03 + vec2(1.7, 9.2);
        amp *= 0.5;
    }
    return v;
}

// 4x4 のベイヤー行列 (0〜1)。パレットの段の間をドットの混ぜ方で埋める
float bayer2(vec2 a) {
    a = floor(a);
    return fract(a.x / 2.0 + a.y * a.y * 0.75);
}

float bayer4(vec2 a) {
    return bayer2(0.5 * a) * 0.25 + bayer2(a);
}

// ---- パレット: 暗い方から 6 段。紫系と青緑系の 2 本 ----
vec3 ramp(float index, bool teal) {
    if (index < 0.5) return vec3(0.027, 0.020, 0.090);
    if (index < 1.5) return teal ? vec3(0.043, 0.086, 0.200) : vec3(0.086, 0.047, 0.200);
    if (index < 2.5) return teal ? vec3(0.071, 0.220, 0.345) : vec3(0.165, 0.086, 0.345);
    if (index < 3.5) return teal ? vec3(0.118, 0.380, 0.490) : vec3(0.310, 0.141, 0.510);
    if (index < 4.5) return teal ? vec3(0.227, 0.588, 0.639) : vec3(0.541, 0.227, 0.639);
    return teal ? vec3(0.600, 0.867, 0.800) : vec3(0.820, 0.478, 0.769);
}

// ---- 星 1 層。1 セルに星は高々 1 つ、位置は整数ドット ----
vec3 pixelStars(vec2 p, float cell, float chance, float seed, float t, float brightness) {
    vec2 id = floor(p / cell);
    float n = hash21(id + seed);
    if (n > chance) return vec3(0.0);

    vec2 local = floor(p - id * cell);
    vec2 pos = floor(hash22(id + seed) * (cell - 6.0)) + 3.0;   // 十字の腕がセルからはみ出ないように
    vec2 d = abs(local - pos);
    float manhattan = d.x + d.y;
    float onAxis = step(min(d.x, d.y), 0.5);

    // 瞬き: 星ごとの周期で、明るさの段がパッと切り替わる
    float phase = floor(t * (1.5 + fract(n * 13.0) * 3.0) + n * 10.0);
    float flicker = hash21(vec2(phase, n * 97.0));
    float lit = step(0.15, flicker);
    float flare = step(0.72, flicker);                          // 十字に光る瞬間
    float big = step(0.35, fract(n * 7.3));                     // 腕を 2 ドットまで伸ばせる星

    float center = step(manhattan, 0.5);
    float arm1 = step(manhattan, 1.5) * onAxis - center;
    float arm2 = (step(manhattan, 2.5) * onAxis - center - arm1) * big;

    vec3 tint = fract(n * 31.0) > 0.5 ? vec3(1.0, 0.94, 0.78) : vec3(0.74, 0.86, 1.0);
    return tint * brightness * (center * lit + arm1 * flare * 0.65 + arm2 * flare * 0.35);
}

// ---- 流れ星。2:1 の階段線で、尾は 6 ドットごとに暗くなる ----
vec3 shootingStar(vec2 p, float t, vec2 res) {
    float period = 4.5;
    float cycle = floor(t / period);
    float local = t - cycle * period;
    float life = 1.0;
    if (local > life) return vec3(0.0);
    local = floor(local * 30.0) / 30.0;

    vec2 start = vec2(res.x * (0.4 + hash21(vec2(cycle, 1.3)) * 0.7),
                      res.y + 6.0 - hash21(vec2(cycle, 7.1)) * res.y * 0.4);
    vec2 head = floor(start + vec2(-2.0, -1.0) * local * 70.0);
    vec2 rel = floor(p) - head;

    float onLine = step(abs(rel.y - floor(rel.x * 0.5)), 0.4) * step(0.0, rel.x) * step(rel.x, 23.0);
    float fade = 1.0 - floor(rel.x / 6.0) * 0.25;
    return vec3(0.96, 0.98, 1.0) * onLine * fade;
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 res = iResolution.xy;
    // 12fps で進む時間。ドット絵らしく少しカクつかせる
    float st = floor(iTime * 12.0) / 12.0;
    vec2 dir = vec2(1.0, 0.55);
    float dither = bayer4(fragCoord);

    // ---- 星雲: 一番奥なのでゆっくり流れる ----
    vec2 np = (fragCoord + floor(dir * st * 2.0)) / 56.0;
    vec2 q = vec2(fbm(np + vec2(0.0, st * 0.03)), fbm(np + vec2(5.2, 1.3)));
    float nebula = fbm(np + 2.2 * q + vec2(st * 0.02, 0.0));
    float density = smoothstep(0.38, 0.85, nebula);
    float dust = smoothstep(0.52, 0.72, fbm(np * 1.7 + q * 1.8 + 11.0));
    bool teal = fbm(np * 0.6 + 7.3) + (dither - 0.5) * 0.12 > 0.52;

    float level = density * (1.0 - dust * 0.75) * 4.0;
    level += fragCoord.y / res.y * 0.6 - 0.35;                   // 上ほどわずかに明るい (全体は暗めに沈める)
    float edge = min(min(fragCoord.x, fragCoord.y), min(res.x - fragCoord.x, res.y - fragCoord.y));
    level -= (1.0 - smoothstep(0.0, 10.0, edge)) * 1.2;          // 縁は暗く沈める

    // ---- 渦巻銀河: 左上。パレットの段を押し上げる形で描く ----
    vec2 gc = vec2(min(28.0, res.x * 0.25), res.y - min(15.0, res.y * 0.35));
    vec2 gp = mat2(0.88, -0.48, 0.48, 0.88) * (fragCoord - gc);
    gp.y *= 2.2;
    float gr = length(gp) / 12.0;
    float ga = atan(gp.y, gp.x);
    float arms = pow(0.5 + 0.5 * sin(2.0 * ga - log(gr + 0.05) * 5.0 + st * 0.5), 3.0);
    level += arms * exp(-gr * 2.2) * 3.0 + exp(-gr * 9.0) * 4.0;

    vec3 col = ramp(clamp(floor(level + dither), 0.0, 5.0), teal) * 0.8;

    // ---- 星 3 層: 手前ほど大きく、速く流れる ----
    col += pixelStars(fragCoord + floor(dir * st * 4.0), 13.0, 0.45, 0.0, st, 0.35);
    col += pixelStars(fragCoord + floor(dir * st * 7.0), 21.0, 0.50, 11.0, st, 0.6);
    col += pixelStars(fragCoord + floor(dir * st * 11.0), 33.0, 0.55, 23.0, st, 0.9);

    // ---- 流れ星 ----
    col += shootingStar(fragCoord, iTime, res);

    fragColor = vec4(min(col, vec3(1.0)), 1.0);
}
