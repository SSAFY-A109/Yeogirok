uniform mat4 mvp;
attribute vec4 position;
attribute vec3 surface;
varying mediump vec3 material;
void main() {
    material = surface;
    gl_Position = mvp * position;
}
