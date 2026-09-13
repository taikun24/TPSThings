// インフィニティインゴット 「良くあるやつ」
// あの手の Mod でおなじみの宇宙。虹色に移ろう星雲の雲と、奥行きの違う 2 層の星が瞬く。

uint mixBits(uint x) {
    x ^= x >> 16;
    x *= 0x7feb352dU;
    x ^= x >> 15;
    x *= 0x846ca68bU;
    x ^= x >> 16;
    return x;
}

float hash21(vec2 p) {
    uvec2 q = uvec2(ivec2(floor(p * 16.0)) + ivec2(1 << 20));
    return float(mixBits(q.x ^ mixBits(q.y)) >> 8) / 16777216.0;
}

// 整数格子の値ノイズ
float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

vec3 hue(float h) {
    return clamp(abs(fract(h + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
}

// 1 層ぶんの星。density が小さいほどまばら
float stars(vec2 p, float density, float twinkleSpeed) {
    vec2 cell = floor(p);
    float h = hash21(cell);
    float present = step(1.0 - density, h);
    float twinkle = 0.5 + 0.5 * sin(iTime * twinkleSpeed + h * 60.0);
    return present * twinkle;
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 res = iResolution.xy;

    // ---- 星雲: 2 段の値ノイズを虹色で塗る ----
    vec2 q = fragCoord / 22.0 + vec2(iTime * 0.05, 0.0);
    float n = valueNoise(q) * 0.65 + valueNoise(q * 2.3 + 4.0) * 0.35;
    vec3 nebula = hue(n * 0.8 + iTime * 0.06) * smoothstep(0.35, 0.9, n) * 0.30;
    vec3 col = vec3(0.02, 0.01, 0.04) + nebula;

    // ---- 星: 奥の層はゆっくり、手前の層は速く流れる ----
    float farStars = stars(floor(fragCoord + vec2(floor(iTime * 2.0), 0.0)), 0.025, 3.0);
    float nearStars = stars(floor(fragCoord + vec2(floor(iTime * 6.0), 0.0)) + vec2(500.0), 0.010, 5.0);
    col += vec3(0.75, 0.80, 1.0) * farStars * 0.45;
    col += vec3(1.0) * nearStars * 0.9;

    // 明るい星だけ十字に光を漏らす
    vec2 np = floor(fragCoord + vec2(floor(iTime * 6.0), 0.0)) + vec2(500.0);
    float leak = stars(np + vec2(1.0, 0.0), 0.010, 5.0) + stars(np - vec2(1.0, 0.0), 0.010, 5.0)
            + stars(np + vec2(0.0, 1.0), 0.010, 5.0) + stars(np - vec2(0.0, 1.0), 0.010, 5.0);
    col += vec3(0.8, 0.85, 1.0) * leak * 0.25;

    float edgeDist = min(min(fragCoord.x, fragCoord.y), min(res.x - fragCoord.x, res.y - fragCoord.y));
    col += hue(iTime * 0.1 + fragCoord.x / res.x * 0.5) * (1.0 - smoothstep(0.0, 1.5, edgeDist)) * 0.8;

    fragColor = vec4(min(col, vec3(1.0)), 1.0);
}
