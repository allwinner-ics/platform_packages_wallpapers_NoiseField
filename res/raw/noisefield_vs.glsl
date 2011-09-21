varying float pointSize;
varying float alpha;

void main() {
    vec4 pos;
    pos.xyz = ATTRIB_position.xyz;
    pos.w = 1.0;
    pos.x = pos.x - ATTRIB_offsetX;
    gl_Position = UNI_MVP * pos;

    float pointSize = 1.0 + ATTRIB_speed * ATTRIB_scaleSize * 3000.0;
    alpha = ATTRIB_alpha;
    gl_PointSize = pointSize;
}



