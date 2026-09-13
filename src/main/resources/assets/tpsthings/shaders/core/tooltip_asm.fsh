// ASM 「低レベル」
// 緑の端末を 16 進ダンプが流れていく。左に番地、右に 2 桁ずつのバイト。
// 1 文字は 3x5 ドットのビット列で、読めそうで読めない。ときどき 1 バイトが書き換わって明るく灯る。

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

// 3x5 の 1 文字。bits の下から idx 番目のビットを見る
float glyphBit(float seed, float idx) {
    float bits = floor(seed * 32768.0);
    return mod(floor(bits / pow(2.0, idx)), 2.0);
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 res = iResolution.xy;
    vec3 phosphor = vec3(0.25, 1.0, 0.40);

    // 1 行 7 ドット (字 5 + 行間 2)、1 文字 4 ドット (字 3 + 字間 1)。1 ドットずつ上へ流す
    float scroll = floor(iTime * 6.0);
    float y = fragCoord.y + scroll;
    float row = floor(y / 7.0);
    float col = floor(fragCoord.x / 4.0);
    float lx = mod(floor(fragCoord.x), 4.0);
    float ly = mod(floor(y), 7.0);

    vec3 col3 = phosphor * 0.035;

    bool inGlyph = lx < 3.0 && ly >= 1.0 && ly < 6.0;
    // 列の割り当て: 1〜4 が番地、6 以降が「2 桁 + 空白」の繰り返し
    bool address = col >= 1.0 && col <= 4.0;
    float j = col - 6.0;
    bool data = j >= 0.0 && mod(j, 3.0) < 2.0 && fragCoord.x < res.x - 4.0;

    if (inGlyph && (address || data)) {
        float idx = lx + (5.0 - ly) * 3.0;
        if (address) {
            // 番地は行ごとに固定。暗く
            col3 += phosphor * 0.28 * glyphBit(hash21(vec2(col, row * 2.0 + 0.5)), idx);
        } else {
            // バイトは世代ごとに書き換わりうる。書き換わった直後だけ明るい
            float gen = floor(iTime * 1.2 + hash21(vec2(col, row + 91.0)) * 10.0);
            float rewrite = step(0.88, hash21(vec2(col + gen * 17.0, row)));
            float age = fract(iTime * 1.2 + hash21(vec2(col, row + 91.0)) * 10.0);
            float seed = hash21(vec2(col + rewrite * gen * 3.0, row + 40.0));
            float lit = glyphBit(seed, idx);
            col3 += mix(phosphor * 0.45, vec3(0.85, 1.0, 0.85), rewrite * (1.0 - age)) * lit;
        }
    }

    // 最下段のカーソル。点滅しながら右へ進む
    float cursorCol = 6.0 + mod(floor(iTime * 3.0), max(floor((res.x - 10.0) / 4.0) - 6.0, 1.0));
    float cursor = step(abs(col - cursorCol), 0.5) * step(fragCoord.y, 7.0) * step(1.0, fragCoord.y)
            * step(0.5, fract(iTime * 2.0));
    col3 += phosphor * cursor * 0.7;

    // 走査線と、画面のにじみ
    col3 *= 0.80 + 0.20 * sin(fragCoord.y * 3.14159);
    col3 += phosphor * 0.04 * (0.5 + 0.5 * sin(iTime * 60.0));

    float edgeDist = min(min(fragCoord.x, fragCoord.y), min(res.x - fragCoord.x, res.y - fragCoord.y));
    col3 += phosphor * (1.0 - smoothstep(0.0, 1.5, edgeDist)) * 0.7;

    fragColor = vec4(min(col3, vec3(1.0)), 1.0);
}
