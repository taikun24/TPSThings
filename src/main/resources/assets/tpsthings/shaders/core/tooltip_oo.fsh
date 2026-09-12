// おお専用のツールチップシェーダー (ShaderToy互換 / Image shader)
// 上から表層 → 索引層の地層を 1 枚ずつ降り、深いほど表層の秩序が崩れる (崩れ方は tooltip_layer と同じ文法)。
// 最下段 (最後の 1 行「おお」) だけはノイズが消え、静かな白に戻る。
// 降りてくる走査の光が底に着くたびに、白が一瞬だけ強く灯る。

// 白い帯の高さ (ドット)。最後の 1 行 (10) + 下の余白 (3)
const float CALM_HEIGHT = 13.0;

// 整数のハッシュ。理由は tooltip_layer.fsh と同じ (float のハッシュは時間が経つと固まる)
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

float gridAt(vec2 p, float cell) {
    vec2 m = mod(floor(p), floor(cell));
    return step(min(m.x, m.y), 0.5);
}

vec3 layerTint(float depth) {
    return mix(vec3(0.10, 0.80, 0.90), vec3(0.90, 0.08, 0.40), depth);
}

// 地層 1 枚ぶんの崩れ方。depth は 0 (表層) 〜 1 (索引層)
vec3 stratum(vec2 fragCoord, float depth) {
    float tick = floor(iTime * mix(1.5, 16.0, depth));
    vec3 tint = layerTint(depth);

    // 帯状の横ずれ
    float bandHeight = mix(8.0, 2.0, depth);
    float band = floor(fragCoord.y / bandHeight);
    float roll = hash21(vec2(band, tick));
    float shift = step(1.0 - depth * 0.4, roll) * (hash21(vec2(tick, band + 7.0)) - 0.5) * depth * 20.0;
    vec2 p = fragCoord + vec2(shift, 0.0);

    // 暗い地、深いほど強く脈打つ
    float pulse = 0.5 + 0.5 * sin(iTime * (0.8 + depth * 5.0) - fragCoord.y * 0.1);
    vec3 col = tint * (0.04 + 0.10 * pulse * depth);

    // 方眼 (色収差つき)
    float cell = mix(10.0, 5.0, depth);
    vec2 split = vec2(depth * 3.0, 0.0);
    vec3 grid = vec3(gridAt(p + split, cell), gridAt(p, cell), gridAt(p - split, cell));
    col += grid * mix(tint, vec3(1.0), 0.25) * (0.14 + 0.20 * depth);

    // 走査線
    col *= 0.82 + 0.18 * sin(fragCoord.y * 3.14159);

    // 深層のノイズブロック
    vec2 block = floor(p / vec2(14.0, 4.0));
    col += tint * step(1.0 - depth * depth * 0.18, hash21(block + tick * 1.37)) * 0.7;

    // 索引層だけ、ときどき反転する
    float flash = step(0.95, hash21(vec2(tick, 3.0))) * step(0.999, depth);
    return mix(col, vec3(1.0) - col, flash);
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 res = iResolution.xy;
    float floorY = min(CALM_HEIGHT, res.y * 0.3);   // 地層の底。これより下が白い帯
    float span = max(res.y - floorY, 1.0);
    float stratumHeight = span / 11.0;

    // ---- 地層: 上から数えて何枚目か ----
    float descent = (res.y - fragCoord.y) / stratumHeight;
    float layer = clamp(floor(descent), 0.0, 10.0);
    float depth = layer / 10.0;
    vec3 tint = layerTint(depth);
    vec3 col = stratum(fragCoord, depth);

    // 境目: 暗い 1 ドットの線と、左端にその層の番号ぶんの目盛り
    float inside = (descent - layer) * stratumHeight;   // この地層の上端からのドット数
    col *= mix(0.35, 1.0, step(1.0, inside));
    float x = floor(fragCoord.x);
    float mark = step(1.0, inside) * step(inside, 2.0)
            * step(3.0, x) * step(x, 2.0 + layer * 2.0) * step(mod(x - 3.0, 2.0), 0.5);
    col += tint * mark * 0.8;

    // ---- 上から降りてくる走査の光。全ての地層を通って底に着く ----
    float cycle = fract(iTime * 0.3);
    float beamY = res.y - cycle * (span + 24.0);        // 底を少し過ぎるまで降りる
    col += tint * exp(-abs(fragCoord.y - beamY) * 0.35) * step(floorY, beamY) * 0.6;

    // ---- 底: 降りきった果て。ノイズの無い、静かな白 ----
    float breath = 0.5 + 0.5 * sin(iTime * 1.1);
    float arrival = step(beamY, floorY) * exp(-(floorY - beamY) * 0.12);
    vec3 calm = vec3(0.82, 0.86, 0.95) * (0.10 + 0.06 * breath + 0.45 * arrival);

    // 境目はドットのちらつきで溶かす
    float threshold = hash21(floor(fragCoord) + floor(iTime * 8.0));
    float calmMix = step(threshold, clamp((floorY - fragCoord.y + 3.0) / 4.0, 0.0, 1.0));
    col = mix(col, calm, calmMix);

    // ---- 底から地層を昇っていく 1 ドットの光 ----
    float seed = hash21(vec2(floor(fragCoord.x / 6.0), 5.0));
    float riseY = mod(iTime * (8.0 + seed * 10.0) + seed * 200.0, res.y + 20.0);
    float spark = step(abs(floor(fragCoord.y) - floor(riseY)), 0.5)
            * step(mod(x, 6.0), 0.5) * step(0.55, seed)
            * (1.0 - riseY / (res.y + 20.0));
    col += vec3(0.90, 0.95, 1.0) * spark * 0.8;

    // ---- 縁取り: 地層では層の色、底では白 ----
    float edgeDist = min(min(fragCoord.x, fragCoord.y), min(res.x - fragCoord.x, res.y - fragCoord.y));
    col += mix(tint, vec3(0.95), calmMix) * (1.0 - smoothstep(0.0, 1.5, edgeDist)) * 0.9;

    fragColor = vec4(min(col, vec3(1.0)), 1.0);
}
