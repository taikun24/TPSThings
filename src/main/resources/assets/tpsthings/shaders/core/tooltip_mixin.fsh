// Mixin 「Mix死n」
// 青灰色のコードの行に、橙の注入点が割り込んで右側を押しのけていく。
// ときどき 2 つの注入が同じ所を取り合い、赤く明滅して死ぬ。

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

// コードの 1 行: 長さのまちまちな字句が並ぶ。x はドット、line は行番号
float tokens(float x, float line, float salt) {
    float indent = floor(hash21(vec2(line, salt + 11.0)) * 4.0) * 4.0;
    float lineLen = 20.0 + hash21(vec2(line, salt + 12.0)) * 120.0;
    float cell = floor((x - indent) / 7.0);
    float local = mod(x - indent, 7.0);
    float len = 2.0 + floor(hash21(vec2(cell, line + salt)) * 5.0);
    return step(indent, x) * step(x, indent + lineLen) * step(local, len - 0.5)
            * step(0.15, hash21(vec2(cell + 3.0, line * 1.5 + salt)));
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 res = iResolution.xy;
    float x = floor(fragCoord.x);
    float y = floor(fragCoord.y);

    // 1 行 4 ドット (字句 1 + 隙間 3)
    float line = floor(y / 4.0);
    bool onLine = mod(y, 4.0) < 1.0;

    // ---- 注入: 3 秒ごとに、どこか 1 か所へ ----
    float cycle = floor(iTime / 3.0);
    float ph = fract(iTime / 3.0);
    float ix = floor(hash21(vec2(cycle, 1.0)) * res.x * 0.8 + res.x * 0.1);
    float width = floor(smoothstep(0.0, 0.35, ph) * 18.0);
    // 注入は一部の行だけ (メソッド 1 つ分のまとまり)
    float rowFrom = floor(hash21(vec2(cycle, 2.0)) * res.y / 4.0);
    float rowSpan = 2.0 + floor(hash21(vec2(cycle, 3.0)) * 4.0);
    float target = step(rowFrom, line) * step(line, rowFrom + rowSpan);

    vec3 base = vec3(0.40, 0.52, 0.75);
    vec3 inject = vec3(1.00, 0.55, 0.10);
    vec3 col = vec3(0.02, 0.025, 0.05);

    if (onLine) {
        float shifted = x - width * target * step(ix, x);
        bool inserted = target > 0.5 && x >= ix && x < ix + width;
        if (inserted) {
            col += inject * 0.8 * tokens(x - ix, line, 50.0 + cycle);
        } else {
            col += base * 0.35 * tokens(shifted, line, 0.0);
        }
    }

    // 注入点の目印: 行の範囲を縦に貫く 1 ドットの線
    float caret = step(abs(x - ix), 0.5) * target * step(0.05, ph);
    col += inject * caret * (0.6 + 0.4 * sin(iTime * 20.0));

    // ---- 取り合い: 3 回に 1 回ほど、2 つ目の注入が近くに来て衝突する ----
    float clash = step(0.62, hash21(vec2(cycle, 4.0))) * step(0.4, ph) * step(ph, 0.85);
    float clashFlicker = step(0.5, hash21(vec2(floor(iTime * 14.0), 6.0)));
    float nearby = target * (1.0 - smoothstep(10.0, 40.0, abs(x - ix)));
    col = mix(col, vec3(0.9, 0.05, 0.08) * (0.35 + 0.6 * float(onLine)), clash * clashFlicker * nearby);

    col *= 0.85 + 0.15 * sin(fragCoord.y * 3.14159);

    float edgeDist = min(min(fragCoord.x, fragCoord.y), min(res.x - fragCoord.x, res.y - fragCoord.y));
    vec3 edge = mix(base, inject, 0.5 + 0.5 * sin(iTime * 1.5 + fragCoord.x * 0.05));
    col += edge * (1.0 - smoothstep(0.0, 1.5, edgeDist)) * 0.8;

    fragColor = vec4(min(col, vec3(1.0)), 1.0);
}
