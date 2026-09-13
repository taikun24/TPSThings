// CoreMod 「本番でやるな」
// 上下を黄黒の立入禁止テープが流れ、中は赤い回転灯がゆっくり掃いていく。
// ときどき警報が強まって、枠ごと赤く点滅する。

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
    vec2 uv = fragCoord / res;
    float x = floor(fragCoord.x);
    float y = floor(fragCoord.y);

    vec3 hazard = vec3(1.0, 0.78, 0.05);
    vec3 alarm = vec3(0.95, 0.08, 0.05);

    // ---- 中: 暗い赤と、回転灯の光の帯 ----
    float sweep = fract(iTime * 0.45);
    float beam = pow(max(cos((uv.x - sweep) * 6.2832), 0.0), 8.0)
            + pow(max(cos((uv.x - sweep - 0.5) * 6.2832), 0.0), 8.0) * 0.5;
    vec3 col = alarm * (0.05 + 0.22 * beam);

    // 床の格子点 (現場の床)
    float dots = step(mod(x, 6.0), 0.5) * step(mod(y, 6.0), 0.5);
    col += alarm * dots * 0.10;

    // ---- 警報の山: 4 秒ごとにしばらく強く点滅 ----
    float surge = step(0.55, fract(iTime * 0.25)) * step(0.5, fract(iTime * 4.0));
    col += alarm * surge * 0.12;

    // ---- 上下のテープ: 3 ドットの黄黒の斜め縞が流れる ----
    float tape = step(y, 2.0) + step(res.y - 3.0, y);
    float stripe = step(mod(x + y + floor(iTime * 10.0), 8.0), 3.5);
    vec3 tapeColor = mix(vec3(0.03), hazard * 0.85, stripe);
    col = mix(col, tapeColor, clamp(tape, 0.0, 1.0));

    // テープのかすれ
    float wear = step(0.93, hash21(vec2(floor(x / 2.0), floor(iTime * 3.0))));
    col *= 1.0 - wear * clamp(tape, 0.0, 1.0) * 0.5;

    col *= 0.85 + 0.15 * sin(fragCoord.y * 3.14159);

    // ---- 左右の縁: 警報に合わせて赤く点く ----
    float edgeDist = min(fragCoord.x, res.x - fragCoord.x);
    col += mix(hazard * 0.4, alarm, surge) * (1.0 - smoothstep(0.0, 1.5, edgeDist)) * 0.9;

    fragColor = vec4(min(col, vec3(1.0)), 1.0);
}
