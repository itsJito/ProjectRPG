#version 450 core
#include "include/world_size.glsl"
const float PI = 3.14159265359;
// (0.3.6 game, gamefixes: interiors were badly glitched) the baked sky and block light, packed into the material target's
// alpha (16 bits): eight bits each. They were four bits each, and every gentle gradient - a lamp's light fading along a
// wall - came out as hard-edged steps of light, octagonal bands that crossed the walls of every lit room
float packLight(float sky, float blk) { return (floor(clamp(sky, 0.0, 1.0) * 255.0 + 0.5) * 256.0 + floor(clamp(blk, 0.0, 1.0) * 255.0 + 0.5)) / 65535.0; }
vec2 unpackLight(float a) { float pk = floor(a * 65535.0 + 0.5); float hi = floor(pk / 256.0); return vec2(hi, pk - hi * 256.0) / 255.0; }
