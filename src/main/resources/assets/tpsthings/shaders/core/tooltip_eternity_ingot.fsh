// エタニティインゴット 「この流れ」
// 翠からの青緑の流線がうねりながら右へ流れ、その上を彗星のような光の粒が滑っていく。
// 流れは途切れず、端から端へ、いつまでも。

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
    float x = fragCoord.x;

    vec3 emerald = vec3(0.10, 0.85, 0.45);
    vec3 teal = vec3(0.10, 0.55, 0.85);

    // ---- 流線: y をうねりで歪めて、一定間隔の線を引く ----
    float warp = sin(x * 0.07 - iTime * 1.1) * 3.0 + sin(x * 0.023 + iTime * 0.6) * 5.0;
    float v = fragCoord.y + warp;
    const float SPACING = 6.0;
    float lineId = floor(v / SPACING);
    float onLine = step(mod(floor(v), SPACING), 0.5);

    vec3 tint = mix(emerald, teal, 0.5 + 0.5 * sin(lineId * 0.9 + iTime * 0.3));
    vec3 col = vec3(0.01, 0.04, 0.04) + tint * 0.05;
    col += tint * onLine * 0.22;

    // ---- 光の粒: 線ごとに速さの違う彗星。頭が明るく、尾が伸びる ----
    float speed = 25.0 + hash21(vec2(lineId, 1.0)) * 35.0;
    float period = 60.0 + hash21(vec2(lineId, 2.0)) * 60.0;
    float head = mod(x - iTime * speed + hash21(vec2(lineId, 3.0)) * period, period);
    // head はこの点から見て「頭が何ドット先を過ぎたか」。小さいほど頭に近い
    float tail = exp(-(period - head) * 0.12) * step(period - 24.0, head);
    col += mix(tint, vec3(0.9, 1.0, 0.95), tail) * tail * onLine * 1.1;

    // 流れと一緒に動くかすかな霞
    float haze = 0.5 + 0.5 * sin((x - iTime * 20.0) * 0.05 + fragCoord.y * 0.15);
    col += tint * haze * 0.03;

    col *= 0.9 + 0.1 * sin(fragCoord.y * 3.14159);

    float edgeDist = min(min(fragCoord.x, fragCoord.y), min(res.x - fragCoord.x, res.y - fragCoord.y));
    col += mix(emerald, teal, 0.5 + 0.5 * sin(iTime + x * 0.04)) * (1.0 - smoothstep(0.0, 1.5, edgeDist)) * 0.8;

    fragColor = vec4(min(col, vec3(1.0)), 1.0);
}
