// 貫通層シェーダー (ShaderToy互換 / Image shader)
// Layer (0〜10) が深いほど、表層の秩序 (整った方眼・シアン) が崩れて
// 深層の混沌 (帯状のずれ・色収差・ノイズ・赤紫) に置き換わっていく。

uniform float Layer;

// 整数のハッシュ (lowbias32)。float の掛け算で作るハッシュは、入力 (iTime から作る tick) が
// 大きくなると小数部が消え、起動から 20 分ほどで崩れ方が固まっていた
uint mixBits(uint x) {
    x ^= x >> 16;
    x *= 0x7feb352dU;
    x ^= x >> 15;
    x *= 0x846ca68bU;
    x ^= x >> 16;
    return x;
}

// 入力は 1/16 刻みで整数に丸めてから混ぜる (tick * 1.37 のような小数も来る)。負の値は下駄を履かせる
float hash21(vec2 p) {
    uvec2 q = uvec2(ivec2(floor(p * 16.0)) + ivec2(1 << 20));
    return float(mixBits(q.x ^ mixBits(q.y)) >> 8) / 16777216.0;
}

// 方眼の線。p はドット座標、cell は 1 マスの大きさ。
// 座標はドット単位に丸めてあるので、滑らかな線ではなく「割り切れる列と行」を 1 ドット幅で光らせる
float gridAt(vec2 p, float cell) {
    vec2 m = mod(floor(p), floor(cell));
    return step(min(m.x, m.y), 0.5);
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    float depth = clamp(Layer / 10.0, 0.0, 1.0);
    vec2 res = iResolution.xy;
    vec2 uv = fragCoord / res;

    // ---- 帯状の横ずれ: 深いほど細かく、頻繁に、大きくずれる ----
    float bandHeight = mix(8.0, 2.0, depth);
    float band = floor(fragCoord.y / bandHeight);
    float tick = floor(iTime * mix(1.5, 16.0, depth));
    float roll = hash21(vec2(band, tick));
    float shift = step(1.0 - depth * 0.4, roll) * (hash21(vec2(tick, band + 7.0)) - 0.5) * depth * 28.0;
    vec2 p = fragCoord + vec2(shift, 0.0);

    // ---- 色: 表層のシアンから深層の赤紫へ ----
    vec3 surface = vec3(0.10, 0.80, 0.90);
    vec3 abyss = vec3(0.90, 0.08, 0.40);
    vec3 tint = mix(surface, abyss, depth);

    // ---- 背景: 暗く、深層ほど強く脈打つ ----
    float pulse = 0.5 + 0.5 * sin(iTime * (0.8 + depth * 5.0) - uv.y * 6.0);
    vec3 col = tint * (0.04 + 0.10 * pulse * depth);

    // ---- 方眼 (色収差つき): 表層は整然、深層ほどマスが細かく RGB が割れる ----
    float cell = mix(10.0, 5.0, depth);
    vec2 split = vec2(depth * 3.0, 0.0);
    vec3 grid = vec3(gridAt(p + split, cell), gridAt(p, cell), gridAt(p - split, cell));
    col += grid * mix(tint, vec3(1.0), 0.25) * (0.18 + 0.22 * depth);

    // ---- 走査線 ----
    col *= 0.82 + 0.18 * sin(fragCoord.y * 3.14159);

    // ---- 上から降りてくるスキャンの光 (層を降りる演出) ----
    float beamPos = 1.0 - fract(iTime * mix(0.2, 0.9, depth));
    float beam = exp(-abs(uv.y - beamPos) * mix(30.0, 60.0, depth));
    col += tint * beam * 0.5;

    // ---- 深層のノイズブロック ----
    vec2 block = floor(p / vec2(14.0, 4.0));
    float noise = hash21(block + tick * 1.37);
    col += tint * step(1.0 - depth * depth * 0.18, noise) * 0.7;

    // ---- 縁取り: 1px の枠が層の色に光る ----
    float edgeDist = min(min(fragCoord.x, fragCoord.y), min(res.x - fragCoord.x, res.y - fragCoord.y));
    col += tint * (1.0 - smoothstep(0.0, 1.5, edgeDist)) * 0.9;

    // ---- L10: ときどき世界ごと反転する ----
    float flash = step(0.95, hash21(vec2(tick, 3.0))) * step(0.999, depth);
    col = mix(col, vec3(1.0) - col, flash);

    fragColor = vec4(col, 1.0);
}
