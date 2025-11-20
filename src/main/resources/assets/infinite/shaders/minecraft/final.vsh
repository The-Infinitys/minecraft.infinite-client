#version 120 // 比較的古い、広くサポートされているバージョンを使用

// 組み込み変数 (Minecraft/OptiFine/Irisで提供されることが多い)
attribute vec4 gl_Vertex;    // 頂点位置 (モデル空間)
attribute vec4 gl_Color;     // 頂点カラー
attribute vec2 gl_MultiTexCoord0; // テクスチャ座標

uniform mat4 gbufferModelViewProjection; // MVP行列 (モデル、ビュー、射影をまとめたもの)

// フラグメントシェーダーに渡す変数
varying vec4 var_color;
varying vec2 var_texcoord;

void main() {
    // 1. 頂点位置の変換: 最小限のMVP行列による座標変換
    gl_Position = gbufferModelViewProjection * gl_Vertex;

    // 2. 頂点カラーとテクスチャ座標をそのまま引き継ぐ
    var_color = gl_Color;
    var_texcoord = gl_MultiTexCoord0;

    // 軽量化のため、法線変換や複雑なワールド座標計算は省略
    // (フラグメントシェーダーで照明を計算しないため)
}