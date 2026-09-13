// 攻撃モジュール 「耐え」
// 深紅の地を山形の矢が右へ流れ続け、その上を白い斬撃が次々と走る。
// 斬撃は一瞬で伸び、白い芯から赤い残光になって消える。

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

// 1 本の斬撃。戻り値の x が白い芯、y が赤い残光
vec2 slash(vec2 p, vec2 res, float seed, float t) {
    float cycle = floor(t);
    float ph = fract(t);
    vec2 center = vec2(hash21(vec2(cycle, seed)), hash21(vec2(seed + 3.0, cycle))) * res;
    float slope = mix(-0.8, 0.8, hash21(vec2(cycle + 5.0, seed + 1.0)));
    vec2 dir = normalize(vec2(1.0, slope));
    if (hash21(vec2(cycle, seed + 9.0)) < 0.5) {
        dir = -dir;
    }
    float len = res.x * mix(0.5, 0.9, hash21(vec2(cycle + 2.0, seed + 2.0)));

    vec2 d = p - center;
    float along = dot(d, dir) + len * 0.5;
    float across = abs(dot(d, vec2(-dir.y, dir.x)));

    float reveal = clamp(ph / 0.12, 0.0, 1.0) * len;
    float inRange = step(0.0, along) * step(along, reveal);
    // 振り終わった側ほど細い
    float taper = along / len;
    float fade = 1.0 - smoothstep(0.12, 0.55, ph);
    float core = step(across, mix(0.4, 1.4, taper)) * inRange * fade;
    float glow = exp(-across * 0.45) * inRange * fade;
    return vec2(core, glow);
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 res = iResolution.xy;
    float x = floor(fragCoord.x);
    float y = floor(fragCoord.y);
    vec3 crimson = vec3(0.85, 0.05, 0.10);

    // ---- 地: 右へ流れる山形の矢 ----
    float chevron = step(mod(x - floor(iTime * 24.0) + abs(y - floor(res.y * 0.5)), 12.0), 1.5);
    vec3 col = crimson * (0.05 + 0.08 * chevron);

    // 右端ほど熱い
    col += crimson * 0.06 * (fragCoord.x / res.x);

    // ---- 斬撃: 位相をずらして 3 本 ----
    vec2 s = vec2(0.0);
    s += slash(fragCoord, res, 1.0, iTime * 1.7);
    s += slash(fragCoord, res, 2.0, iTime * 1.7 + 0.37);
    s += slash(fragCoord, res, 3.0, iTime * 1.3 + 0.71);
    col += crimson * min(s.y, 1.0) * 0.9;
    col = mix(col, vec3(1.0, 0.92, 0.90), min(s.x, 1.0));

    // 斬撃が走った瞬間、全体がわずかに明るむ
    float hit = 1.0 - smoothstep(0.0, 0.08, fract(iTime * 1.7));
    col += crimson * hit * 0.08;

    col *= 0.85 + 0.15 * sin(fragCoord.y * 3.14159);

    float edgeDist = min(min(fragCoord.x, fragCoord.y), min(res.x - fragCoord.x, res.y - fragCoord.y));
    col += crimson * (1.0 - smoothstep(0.0, 1.5, edgeDist)) * (0.7 + 0.3 * hit);

    fragColor = vec4(min(col, vec3(1.0)), 1.0);
}
