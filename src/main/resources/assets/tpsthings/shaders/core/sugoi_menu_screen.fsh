#version 150

// SugoiMenu 表示中に画面全体へ掛けるポストエフェクト。
// Sampler0 には描画直前の画面のコピーが入っている。Openness = 0 なら素通し。

uniform sampler2D Sampler0;

uniform vec2 ScreenSize;  // 画面のピクセルサイズ
uniform float Openness;   // メニューの開き具合 0..1
uniform float Time;
uniform vec2 Center;      // 衝撃波とズームの中心 (UV)
uniform float WaveAge;    // 直近の開閉からの秒数
uniform float WaveSign;   // 開いた = +1 / 閉じた = -1
uniform vec3 Tint;

in vec2 texCoord;
out vec4 fragColor;

const int BLUR_TAPS = 6;

// RGB をずらして読む (色収差)
vec3 sampleSplit(vec2 uv, vec2 offset) {
    return vec3(
        texture(Sampler0, uv + offset).r,
        texture(Sampler0, uv).g,
        texture(Sampler0, uv - offset).b);
}

// 開くときは外へ広がり、閉じるときは中心へ吸い込まれるリング
float waveRing(float dist) {
    float life = WaveSign > 0.0 ? 0.9 : 0.26;
    float fade = clamp(1.0 - WaveAge / life, 0.0, 1.0);
    float radius = WaveSign > 0.0 ? WaveAge * 1.8 : 1.4 * fade;
    float d = (dist - radius) / 0.07;
    return exp(-d * d) * fade * fade;
}

void main() {
    vec2 aspect = vec2(ScreenSize.x / max(ScreenSize.y, 1.0), 1.0);
    vec2 toCenter = (texCoord - Center) * aspect;
    float dist = length(toCenter);
    vec2 dir = dist > 1.0e-4 ? toCenter / dist / aspect : vec2(0.0);

    float ring = waveRing(dist) * sqrt(Openness);

    // ほんの少し寄り、衝撃波で押し出す/引き込む
    vec2 uv = Center + (texCoord - Center) * (1.0 - 0.02 * Openness);
    uv -= dir * ring * 0.03 * WaveSign;

    vec2 fromMid = texCoord - 0.5;
    float edge = dot(fromMid * aspect, fromMid * aspect);
    vec2 split = fromMid * (0.012 * Openness * edge + 0.01 * ring);

    // 中心からのズームブラー (遠いほど強くかかる)
    vec3 col = vec3(0.0);
    float blurAmount = 0.025 * Openness;
    for (int i = 0; i < BLUR_TAPS; i++) {
        float s = 1.0 - blurAmount * float(i) / float(BLUR_TAPS - 1);
        col += sampleSplit(Center + (uv - Center) * s, split);
    }
    col /= float(BLUR_TAPS);

    // 彩度を落として青寄りに、少し暗く
    float luma = dot(col, vec3(0.299, 0.587, 0.114));
    vec3 graded = mix(col, vec3(luma) * vec3(0.8, 0.88, 1.08), 0.6);
    col = mix(col, graded, Openness);
    col *= 1.0 - 0.3 * Openness;

    // 色付きビネット
    float vig = smoothstep(0.35, 1.0, length(fromMid * aspect) * 1.3);
    col = mix(col, col * 0.35 + Tint * 0.3, vig * Openness * 0.75);

    // 流れる走査線
    float scan = 0.5 + 0.5 * sin(texCoord.y * ScreenSize.y * 1.5708 + Time * 4.0);
    col *= 1.0 - 0.07 * Openness * scan;

    col += Tint * ring * 0.45;

    // 閉じた瞬間の白フラッシュ
    float closeFlash = WaveSign < 0.0 ? clamp(1.0 - WaveAge / 0.12, 0.0, 1.0) : 0.0;
    col += vec3(0.12 * Openness * closeFlash);

    fragColor = vec4(col, 1.0);
}
