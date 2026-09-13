// すごいメニュー 「GuiGui」
// パステルの市松模様の上を、小さなウィンドウがぐいぐい押し合いながら漂う。
// ウィンドウはタイトルバーの色が虹色に巡り、右上に 3 つのボタン。

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

vec3 hue(float h) {
    return clamp(abs(fract(h + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 res = iResolution.xy;
    float x = floor(fragCoord.x);
    float y = floor(fragCoord.y);

    // ---- 地: 斜めに流れる市松模様 ----
    float sx = floor((x + floor(iTime * 8.0)) / 6.0);
    float sy = floor((y + floor(iTime * 8.0)) / 6.0);
    float checker = mod(sx + sy, 2.0);
    vec3 col = mix(vec3(0.10, 0.07, 0.14), vec3(0.14, 0.10, 0.18), checker);
    col += hue(iTime * 0.05 + fragCoord.x / res.x * 0.3) * 0.03;

    // ---- ウィンドウ: 奥から順に 5 枚重ねる ----
    for (int i = 0; i < 5; i++) {
        float fi = float(i);
        float w = 22.0 + floor(hash21(vec2(fi, 1.0)) * 18.0);
        float h = 12.0 + floor(hash21(vec2(fi, 2.0)) * 8.0);
        // リサジューで漂い、隣とぶつかるように位相を詰める
        vec2 center = res * 0.5 + vec2(
                sin(iTime * (0.6 + fi * 0.13) + fi * 1.9) * (res.x * 0.5 - w * 0.4),
                cos(iTime * (0.5 + fi * 0.11) + fi * 2.7) * (res.y * 0.5 - h * 0.3));
        vec2 lo = floor(center - vec2(w, h) * 0.5);
        vec2 hi = lo + vec2(w, h);
        if (x < lo.x || x >= hi.x || y < lo.y || y >= hi.y) {
            continue;
        }
        vec3 bar = hue(iTime * 0.2 + fi * 0.2) * 0.55 + 0.12;
        vec3 body = vec3(0.20, 0.19, 0.24);
        vec3 win = body;
        // タイトルバー (上 3 ドット)
        if (y >= hi.y - 3.0) {
            win = bar;
            // 右上の 3 つのボタン
            float bx = hi.x - 2.0 - x;
            if (y == hi.y - 2.0 && bx >= 0.0 && bx < 8.0 && mod(bx, 3.0) < 1.5) {
                win = vec3(0.95, 0.9, 1.0) * 0.7;
            }
        } else {
            // 中身: 項目の行が並ぶ
            float item = step(mod(hi.y - 5.0 - y, 3.0), 0.5) * step(lo.x + 2.0, x)
                    * step(x, lo.x + 4.0 + floor(hash21(vec2(fi, floor((hi.y - y) / 3.0))) * (w - 8.0)));
            win += bar * item * 0.35;
        }
        // 1 ドットの枠
        if (x == lo.x || x == hi.x - 1.0 || y == lo.y || y == hi.y - 1.0) {
            win = vec3(0.04, 0.03, 0.06);
        }
        col = win;
    }

    // マウスカーソルのきらめき (時々どこかがクリックされる)
    float clickCycle = floor(iTime * 1.1);
    vec2 click = floor(vec2(hash21(vec2(clickCycle, 8.0)), hash21(vec2(9.0, clickCycle))) * res);
    float ring = step(abs(length(vec2(x, y) - click) - fract(iTime * 1.1) * 10.0), 0.6)
            * (1.0 - fract(iTime * 1.1));
    col += vec3(1.0, 0.85, 0.95) * ring * 0.6;

    float edgeDist = min(min(fragCoord.x, fragCoord.y), min(res.x - fragCoord.x, res.y - fragCoord.y));
    col += hue(iTime * 0.15 + (fragCoord.x + fragCoord.y) / (res.x + res.y)) * (1.0 - smoothstep(0.0, 1.5, edgeDist)) * 0.8;

    fragColor = vec4(min(col, vec3(1.0)), 1.0);
}
