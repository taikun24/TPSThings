// BERWL 「虚勢を張るための道具」
// 金ぴかの地に斜めの光沢が走り、あちこちで星のきらめきが弾ける。
// ただしメッキなので、ときどき帯ごと剥がれて安っぽい灰色が覗く。

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

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 res = iResolution.xy;
    float x = floor(fragCoord.x);
    float y = floor(fragCoord.y);

    // ---- 金属の地: 縦方向に明暗の帯 (磨いた金の反射) ----
    float bands = 0.5 + 0.5 * cos(fragCoord.y / res.y * 9.0 + 0.8);
    vec3 gold = mix(vec3(0.35, 0.20, 0.03), vec3(0.95, 0.75, 0.25), bands * 0.8);
    vec3 col = gold * 0.28;

    // ---- 斜めの光沢: 2.5 秒に 1 回、左から右へ ----
    float sweepPos = fract(iTime * 0.4) * (res.x + res.y + 40.0) - 20.0 - res.y;
    float sheen = exp(-abs(x + y * 0.8 - sweepPos - res.y) * 0.15);
    col += vec3(1.0, 0.92, 0.60) * sheen * 0.5;

    // ---- きらめき: 格子の目ごとに、たまに十字の星 ----
    vec2 cellSize = vec2(14.0, 10.0);
    vec2 cell = floor(fragCoord / cellSize);
    float t = iTime * 0.9 + hash21(cell) * 5.0;
    float ph = fract(t);
    float alive = step(0.55, hash21(cell + floor(t) * 3.1));
    vec2 star = floor(cell * cellSize + vec2(2.0) + floor(vec2(hash21(cell + 7.0), hash21(cell + 13.0)) * (cellSize - 4.0)));
    vec2 d = abs(vec2(x, y) - star);
    float arm = floor(sin(ph * 3.14159) * 3.0);
    float sparkle = (step(d.x, 0.5) * step(d.y, arm) + step(d.y, 0.5) * step(d.x, arm)) * alive;
    col = mix(col, vec3(1.0, 0.97, 0.85), min(sparkle, 1.0) * sin(ph * 3.14159));

    // ---- メッキ剥がれ: 横帯 1 本が一瞬だけ灰色になる ----
    float tick = floor(iTime * 5.0);
    float peel = step(0.88, hash21(vec2(tick, 1.0)));
    float bandY = floor(hash21(vec2(tick, 2.0)) * res.y);
    float bandH = 2.0 + floor(hash21(vec2(tick, 3.0)) * 4.0);
    float inBand = peel * step(bandY, y) * step(y, bandY + bandH);
    float grey = dot(col, vec3(0.3, 0.5, 0.2)) * 0.6;
    col = mix(col, vec3(grey), inBand);

    col *= 0.90 + 0.10 * sin(fragCoord.y * 3.14159);

    float edgeDist = min(min(fragCoord.x, fragCoord.y), min(res.x - fragCoord.x, res.y - fragCoord.y));
    col += vec3(1.0, 0.80, 0.30) * (1.0 - smoothstep(0.0, 1.5, edgeDist)) * (0.8 + 0.4 * sheen);

    fragColor = vec4(min(col, vec3(1.0)), 1.0);
}
