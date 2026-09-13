// グリッチ 「脆弱性」
// 斜めの縞が RGB に割れ、横に千切れてずれ、縦に引き延ばされる。
// 穴の空いた所には、見慣れた紫と黒の「テクスチャが見つからない」市松模様が覗く。

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

// 壊される前の絵: ゆっくり流れる斜めの縞
float source(vec2 p) {
    return step(mod(floor(p.x + p.y * 0.5 + iTime * 6.0), 10.0), 3.5);
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 res = iResolution.xy;
    vec2 p = floor(fragCoord);
    float tick = floor(iTime * 12.0);
    // ときどき静かな時間があって、そのあと一気に壊れる
    float storm = 0.35 + 0.65 * step(0.6, hash21(vec2(floor(iTime * 1.5), 1.0)));

    // ---- 横に千切れてずれる ----
    vec2 block = floor(p / vec2(18.0, 3.0));
    float torn = step(1.0 - 0.35 * storm, hash21(block + tick));
    p.x += torn * floor((hash21(block + tick + 5.0) - 0.5) * 40.0);

    // ---- 縦に引き延ばす (列ごとに上の画素を下へ垂らす) ----
    float column = floor(p.x / 2.0);
    float smear = step(1.0 - 0.12 * storm, hash21(vec2(column, floor(iTime * 3.0))));
    float drip = floor(hash21(vec2(column, 9.0)) * res.y);
    p.y = mix(p.y, max(p.y, drip), smear);

    // ---- RGB の割れ ----
    float split = (1.0 + 3.0 * storm) * (0.5 + hash21(vec2(tick, 2.0)));
    vec3 col = vec3(
            source(p + vec2(split, 0.0)),
            source(p),
            source(p - vec2(split, 0.0)));
    // 文字が読めるよう、壊れていない時は暗く沈める
    col *= vec3(0.30, 0.22, 0.34) * (0.45 + 0.55 * storm);
    col += vec3(0.03, 0.0, 0.04);

    // ---- 穴: 紫と黒の市松模様の欠け ----
    vec2 hole = floor(fragCoord / vec2(24.0, 8.0));
    float holeTick = floor(iTime * 2.0);
    float missing = step(0.95, hash21(hole + holeTick * 7.0));
    float checker = mod(floor(fragCoord.x / 2.0) + floor(fragCoord.y / 2.0), 2.0);
    col = mix(col, mix(vec3(0.0), vec3(0.97, 0.0, 0.86), checker) * 0.5, missing);

    // ---- 走査線のノイズ ----
    float line = step(0.99 - 0.01 * storm, hash21(vec2(floor(fragCoord.y), tick)));
    col = mix(col, vec3(0.55), line);

    col *= 0.85 + 0.15 * sin(fragCoord.y * 3.14159);

    float edgeDist = min(min(fragCoord.x, fragCoord.y), min(res.x - fragCoord.x, res.y - fragCoord.y));
    vec3 edge = step(0.5, hash21(vec2(tick, 4.0))) > 0.5 ? vec3(0.0, 1.0, 0.6) : vec3(1.0, 0.0, 0.8);
    col += edge * (1.0 - smoothstep(0.0, 1.5, edgeDist)) * 0.8;

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
