#version 120
#extension GL_ARB_shader_texture_lod : enable // 必要に応じてLOD制御を有効化

// 精度指定 (パフォーマンス向上のため積極的にlowp/mediumpを使用)
#define HIGH highp
#define MEDIUM mediump

// 組み込み変数
uniform sampler2D colortex0; // メインのテクスチャ

// 外部から渡される変数 (設定や時刻など)
uniform MEDIUM vec3 sunPosition; // 太陽の向きを表すベクトル (簡易的な光源方向)
uniform MEDIUM vec3 colorAmbient; // 環境光の色 (固定値で軽量化)

// 頂点シェーダーから受け取る変数
varying MEDIUM vec4 var_color;
varying MEDIUM vec2 var_texcoord;

void main() {
    // 1. テクスチャサンプリング (最低限のサンプリング回数)
    MEDIUM vec4 textureColor = texture2D(colortex0, var_texcoord);

    // 2. アルファテスト
    if (textureColor.a < 0.1) {
        discard; // 透明なピクセルは描画を破棄
    }

    // 3. 頂点カラーの乗算
    MEDIUM vec4 finalColor = textureColor * var_color;

    // --- 超軽量な照明モデル ---

    // 4. 単純なディレクショナル照明（太陽光）の計算
    // - 法線の情報がないため、単純に太陽が上にあるかどうかで補正
    MEDIUM float sunFactor = max(0.0, sunPosition.y); // 太陽が地平線より上にある場合に明るさを持つ

    // 太陽光の強さを調整 (任意)
    MEDIUM float diffuseStrength = 0.8;
    MEDIUM vec3 diffuseLight = vec3(1.0, 0.95, 0.9) * diffuseStrength * sunFactor; // 太陽光の色と強さ

    // 5. 環境光とディフューズ光の合成
    // 環境光は固定値 (colorAmbient) を使用することで計算量を削減
    MEDIUM vec3 lighting = colorAmbient + diffuseLight;

    // 6. 最終色の決定
    // 色 = (テクスチャ色 * 頂点色) * 照明
    MEDIUM vec3 litColor = finalColor.rgb * lighting;

    // 7. 出力
    gl_FragColor = vec4(litColor, finalColor.a);
}