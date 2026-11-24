// Contours: drifting topographic contour lines (needs OES_standard_derivatives).
// GLSL ES 1.0 (WebGL 1) fragment shader, run by ShaderCanvas.tsx. Saved as pewdiepie.glsl in ai-course-final.
// Uniforms: u_resolution, u_time, and the palette roles u_bg, u_primary, u_secondary,
// u_primarySoft, u_secondarySoft, u_highlight (set by ShaderCanvas.tsx from the palette tokens).
#extension GL_OES_standard_derivatives : enable
precision mediump float;
uniform vec2 u_resolution;
uniform float u_time;
uniform vec3 u_bg, u_primary, u_secondary, u_primarySoft, u_secondarySoft, u_highlight;
float hash(vec2 p){ p=fract(p*vec2(123.34,456.21)); p+=dot(p,p+45.32); return fract(p.x*p.y); }
float noise(vec2 p){ vec2 i=floor(p), f=fract(p); vec2 u=f*f*(3.0-2.0*f);
  return mix(mix(hash(i),hash(i+vec2(1,0)),u.x), mix(hash(i+vec2(0,1)),hash(i+vec2(1,1)),u.x), u.y); }
float fbm(vec2 p){ float v=0.0,a=0.5; mat2 m=mat2(1.6,1.2,-1.2,1.6); for(int i=0;i<5;i++){ v+=a*noise(p); p=m*p; a*=0.5; } return v; }
vec3 finish(vec3 col, vec2 uv){ float vig=smoothstep(1.2,0.3,length((uv-0.5)*vec2(1.0,1.3))); col=mix(u_bg,col,0.45+0.55*vig);
  col+=(hash(gl_FragCoord.xy)-0.5)/128.0; return clamp(col,0.0,1.0); }

void main(){ vec2 uv=gl_FragCoord.xy/u_resolution; vec2 p=(gl_FragCoord.xy-0.5*u_resolution)/u_resolution.y; float t=u_time*0.015; // slowed from 0.04 for the landing page
 float h=fbm(p*1.3+vec2(t,-t*0.6)+0.4*fbm(p*2.0-t));
 float lines=h*22.0; float d=abs(fract(lines)-0.5)/fwidth(lines);
 float line=1.0-clamp(d*0.9,0.0,1.0);
 float major=1.0-clamp(abs(fract(h*22.0/5.0)-0.5)/fwidth(h*22.0/5.0)*0.6,0.0,1.0);
 vec3 tint=mix(u_primary,u_secondary,smoothstep(0.35,0.7,h)); tint=mix(tint,u_highlight,smoothstep(0.65,0.85,h));
 vec3 col=u_bg+u_primary*0.10*smoothstep(0.3,0.8,h);
 col+=tint*line*0.55*smoothstep(0.2,0.9,uv.x+0.2); col+=tint*major*0.6;
 vec2 m=vec2(0.35*u_resolution.x/u_resolution.y, 0.05)+0.08*vec2(sin(u_time*0.08),cos(u_time*0.07));
 col+=u_secondary*0.18*exp(-6.0*length(p-m));
 gl_FragColor=vec4(finish(col,uv),1.0); }
