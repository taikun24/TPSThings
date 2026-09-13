// ユニティインゴット 「どこかで見た」
// 中心から白金の輪が広がる万華鏡。インフィニティの虹とエタニティの翠を 1 つに束ねている。
// 少し遅れて、少しずれた所に同じ絵の残像が 2 つ重なる。見覚えがあるのはそのせい。

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

vec3 hue(float h) {
    return clamp(abs(fract(h + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
}

// 時刻 t の万華鏡。明るさを返す
float kaleido(vec2 p, vec2 res, float t) {
    vec2 d = p - res * 0.5;
    float r = length(d);
    float a = atan(d.y, d.x) / 6.2832 + 0.5;
    // 8 つに割った扇が交互に明暗、ゆっくり回る
    float sector = mod(floor((a + t * 0.04) * 8.0), 2.0);
    // 外へ広がる輪
    float ring = step(mod(r - t * 9.0, 10.0), 1.2);
    // 扇の境目の放射線
    float spoke = step(fract((a + t * 0.04) * 8.0), 0.03) * step(4.0, r);
    return ring * (0.55 + 0.45 * sector) + spoke * 0.35;
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 res = iResolution.xy;
    vec2 p = floor(fragCoord) + 0.5;

    vec3 platinum = vec3(0.95, 0.90, 0.75);
    vec3 col = vec3(0.03, 0.03, 0.035);

    // 本体
    col += platinum * kaleido(p, res, iTime) * 0.45;
    // 残像: 虹色 (インフィニティ) と翠 (エタニティ) で、遅れてずれる
    col += hue(iTime * 0.1) * kaleido(p - vec2(5.0, 1.0), res, iTime - 0.45) * 0.18;
    col += vec3(0.15, 0.85, 0.50) * kaleido(p + vec2(5.0, 1.0), res, iTime - 0.90) * 0.14;

    // 中心の核: 3 つが重なる所だけ強く光る
    float core = exp(-length(p - res * 0.5) * 0.25);
    col += platinum * core * (0.5 + 0.2 * sin(iTime * 2.0));

    // デジャヴの瞬間: 数秒に一度、全体が一拍だけ前の絵に戻る
    float deja = step(0.92, fract(iTime * 0.2));
    col = mix(col, platinum * kaleido(p, res, floor(iTime * 0.2) / 0.2) * 0.5, deja * 0.7);

    col *= 0.9 + 0.1 * sin(fragCoord.y * 3.14159);

    float edgeDist = min(min(fragCoord.x, fragCoord.y), min(res.x - fragCoord.x, res.y - fragCoord.y));
    col += mix(platinum, hue(iTime * 0.1 + fragCoord.x / res.x), 0.3) * (1.0 - smoothstep(0.0, 1.5, edgeDist)) * 0.8;

    fragColor = vec4(min(col, vec3(1.0)), 1.0);
}
