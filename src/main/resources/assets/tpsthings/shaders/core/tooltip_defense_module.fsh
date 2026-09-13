// 防衛モジュール 「絶え」
// 青い六角形の障壁。どこかに攻撃が当たるたびに波紋が広がり、通った所の目が光る。
// 当たった目は一瞬白く割れて、ゆっくり塞がる。

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

// 六角形の格子。xy = 目の中心からの位置、zw = 目の中心
vec4 hexCell(vec2 p) {
    vec2 r = vec2(1.0, 1.7320508);
    vec2 h = r * 0.5;
    vec2 a = mod(p, r) - h;
    vec2 b = mod(p - h, r) - h;
    vec2 gv = dot(a, a) < dot(b, b) ? a : b;
    return vec4(gv, p - gv);
}

float hexDist(vec2 p) {
    p = abs(p);
    return max(dot(p, vec2(0.5, 0.8660254)), p.x);
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 res = iResolution.xy;
    vec3 shield = vec3(0.25, 0.60, 1.00);

    const float SCALE = 7.0;
    vec4 hex = hexCell(fragCoord / SCALE);
    float dist = hexDist(hex.xy);
    vec2 cellCenter = hex.zw * SCALE;

    // 目の縁 (中心から 0.5 が縁)
    float edge = step(0.40, dist);

    // 障壁全体を縦に撫でていく光
    float shimmer = exp(-abs(fract(iTime * 0.25) * (res.x + 40.0) - 20.0 - fragCoord.x) * 0.08);

    // ---- 被弾: 1.4 秒ごとに 1 か所、ずらして 2 系統 ----
    float ripple = 0.0;
    float crack = 0.0;
    for (int i = 0; i < 2; i++) {
        float fi = float(i);
        float t = iTime / 1.4 + fi * 0.5;
        float cycle = floor(t);
        float ph = fract(t);
        vec2 impact = vec2(hash21(vec2(cycle, fi + 1.0)), hash21(vec2(fi + 7.0, cycle))) * res;
        float radius = ph * res.x * 0.7;
        float d = length(cellCenter - impact);
        ripple += exp(-abs(d - radius) * 0.25) * (1.0 - ph);
        crack += step(d, SCALE * 1.2) * (1.0 - smoothstep(0.0, 0.5, ph));
    }

    vec3 col = shield * 0.04;
    col += shield * edge * (0.16 + 0.20 * shimmer + 0.6 * min(ripple, 1.0));
    // 波紋が通った目の中も淡く満たす
    col += shield * (1.0 - edge) * 0.18 * min(ripple, 1.0);
    // 当たった目は白く割れる
    col = mix(col, vec3(0.85, 0.93, 1.0), min(crack, 1.0) * (0.35 + 0.5 * edge));

    col *= 0.88 + 0.12 * sin(fragCoord.y * 3.14159);

    float edgeDist = min(min(fragCoord.x, fragCoord.y), min(res.x - fragCoord.x, res.y - fragCoord.y));
    col += shield * (1.0 - smoothstep(0.0, 1.5, edgeDist)) * 0.9;

    fragColor = vec4(min(col, vec3(1.0)), 1.0);
}
