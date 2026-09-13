// おお合金 「これはおお...なのか?」
// 合金の段 (ネザライトの焦茶 → ポロニウムの青緑 → 反物質の赤紫) が波打つ層になって溶け合い、
// ヘアラインの金属の筋に光沢が走る。
// 最下段にはおおの「静かな白」が現れかけるが、ドットのちらつきと一緒に金属へ戻ってしまう。本物ではないので。

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

// 白い帯の高さ。tooltip_oo.fsh と同じ
const float CALM_HEIGHT = 13.0;

vec3 alloyColor(float t) {
    vec3 netherite = vec3(0.35, 0.26, 0.22);
    vec3 polonium = vec3(0.05, 0.75, 0.62);
    vec3 antimatter = vec3(0.85, 0.05, 0.95);
    return t < 0.5 ? mix(netherite, polonium, t * 2.0) : mix(polonium, antimatter, t * 2.0 - 1.0);
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 res = iResolution.xy;
    float x = floor(fragCoord.x);
    float y = floor(fragCoord.y);

    // ---- 波打つ合金の層: 上ほど深い段 (反物質) ----
    float wave = sin(x * 0.09 + iTime * 0.8) * 2.5 + sin(x * 0.031 - iTime * 0.5) * 4.0;
    float t = clamp((y + wave) / res.y, 0.0, 1.0);
    float strata = floor(t * 6.0) / 5.0;
    vec3 metal = alloyColor(mix(t, strata, 0.6));

    // ヘアライン: 行ごとに長さの違う横の筋
    float streakLen = 3.0 + floor(hash21(vec2(y, 1.0)) * 10.0);
    float streak = hash21(vec2(floor((x + hash21(vec2(y, 2.0)) * 50.0) / streakLen), y));
    vec3 col = metal * (0.16 + 0.12 * streak);

    // 層の境目は暗い 1 ドット
    col *= mix(0.5, 1.0, step(0.04, fract(t * 6.0)));

    // ---- 光沢: 斜めに往復する白い照り返し ----
    float sheenPos = (0.5 + 0.5 * sin(iTime * 0.7)) * (res.x + res.y);
    float sheen = exp(-abs(x + y - sheenPos) * 0.12);
    col += mix(metal, vec3(1.0), 0.6) * sheen * 0.35;

    // ---- 底: おおの白になりかけて、戻る ----
    float floorY = min(CALM_HEIGHT, res.y * 0.3);
    float hope = smoothstep(0.2, 0.9, 0.5 + 0.5 * sin(iTime * 0.9));
    float doubt = step(0.7, hash21(vec2(floor(iTime * 6.0), 3.0)));   // 疑いが挟まるとすぐ崩れる
    float calmRatio = clamp((floorY - y + 3.0) / 4.0, 0.0, 1.0) * hope * (1.0 - doubt * 0.8);
    float threshold = hash21(vec2(x, y) + floor(iTime * 8.0));
    float calmMix = step(threshold, calmRatio);
    vec3 calm = vec3(0.82, 0.86, 0.95) * (0.12 + 0.05 * sin(iTime * 1.1));
    col = mix(col, calm, calmMix);

    col *= 0.88 + 0.12 * sin(fragCoord.y * 3.14159);

    float edgeDist = min(min(fragCoord.x, fragCoord.y), min(res.x - fragCoord.x, res.y - fragCoord.y));
    vec3 edge = mix(alloyColor(fract(iTime * 0.1 + fragCoord.y / res.y * 0.5)), vec3(0.95), calmMix);
    col += edge * (1.0 - smoothstep(0.0, 1.5, edgeDist)) * 0.85;

    fragColor = vec4(min(col, vec3(1.0)), 1.0);
}
