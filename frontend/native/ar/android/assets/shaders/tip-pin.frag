precision mediump float;
uniform sampler2D artwork;
varying mediump vec3 material;
void main() {
    vec4 pixel = texture2D(artwork, material.xy);
    if (pixel.a < 0.5) discard;
    gl_FragColor = vec4(pixel.rgb * material.z, 1.0);
}
